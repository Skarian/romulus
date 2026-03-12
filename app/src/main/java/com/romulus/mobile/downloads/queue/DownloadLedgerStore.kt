@file:Suppress(
    "ChainMethodContinuation",
    "FunctionExpressionBody",
    "FunctionSignature",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.OutputCleanupScope
import com.romulus.mobile.downloads.output.OutputReservation
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val CLAIM_LEASE_DURATION = 15.minutes

@Serializable
private data class QueueLedgerState(
    val wakeGeneration: Long = 0,
    val acknowledgedWakeGeneration: Long = 0,
    val rows: List<QueueRowRecord> = emptyList()
)

@Suppress("TooManyFunctions")
interface DownloadLedgerStore {
    suspend fun insertTasks(tasks: List<QueueTask>): Result<Unit>

    suspend fun claimRunnableTasks(now: Instant, limit: Int): List<QueueClaim>

    suspend fun claimActionRecoveryTasks(now: Instant, limit: Int): List<QueueClaim>

    suspend fun beginAttempt(taskId: TaskId, enteredAt: Instant): Result<Int>

    suspend fun releaseClaim(taskId: TaskId): Result<Unit>

    suspend fun renewClaim(taskId: TaskId): Result<Unit>

    suspend fun resumePausedTask(taskId: TaskId): Result<Unit>

    suspend fun resetForManualRetry(taskId: TaskId): Result<Unit>

    suspend fun restartTask(taskId: TaskId): Result<Unit>

    suspend fun persistState(taskId: TaskId, state: QueueTaskState): Result<Unit>

    suspend fun acknowledgeStop(
        taskId: TaskId,
        checkpoint: TransferCheckpoint?
    ): Result<Unit>

    suspend fun persistActionRequest(
        taskId: TaskId,
        request: QueueActionRequest?
    ): Result<Unit>

    suspend fun persistTransferCheckpoint(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit>

    suspend fun persistReservation(
        taskId: TaskId,
        reservation: OutputReservation
    ): Result<Unit>

    suspend fun enterFinalization(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit>

    suspend fun persistFinalizationCursor(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit>

    suspend fun reserveFreshOutput(
        taskId: TaskId,
        outputDirectoryUri: String,
        filesystemRelativePaths: Set<String>,
        createReservation: (Set<String>) -> OutputReservation
    ): Result<OutputReservation>

    suspend fun persistFinalOutputs(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit>

    suspend fun completeTask(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit>

    suspend fun readNextRetryAt(): Instant?

    suspend fun readNextLeaseExpiryAt(actionRecoveryOnly: Boolean): Instant?

    suspend fun readRow(taskId: TaskId): QueueRowRecord?

    suspend fun readReservedRelativePaths(
        outputDirectoryUri: String,
        excludeTaskId: TaskId? = null
    ): Set<String>

    suspend fun readWakeGeneration(): Long

    suspend fun requestDispatch(): Result<Long>

    suspend fun acknowledgeDispatchStart(): Result<Long>

    suspend fun hasPendingDispatch(): Boolean

    suspend fun readRows(
        includeHidden: Boolean = true
    ): List<QueueRowRecord>

    fun observeRows(
        includeHidden: Boolean = true
    ): Flow<List<QueueRowRecord>>

    suspend fun hideTerminalRowsAtomically(taskIds: List<TaskId>): Result<Unit>
}

@Suppress("LargeClass", "TooManyFunctions")
internal class FileDownloadLedgerStore(
    private val ledgerFile: File,
    private val json: Json,
    private val clock: Clock
) : DownloadLedgerStore {
    private val mutex = Mutex()
    private val ledgerState = MutableStateFlow(loadLedgerState())

    override suspend fun insertTasks(
        tasks: List<QueueTask>
    ): Result<Unit> =
        mutateLedger(advanceWakeGeneration = true) { current ->
            val appended = current.rows + tasks.map { task ->
                QueueRowRecord(
                    task = task,
                    updatedAt = task.createdAt,
                    state = QueueTaskState.Queued,
                    visibility = QueueVisibility.VISIBLE,
                    attemptCount = 0,
                    lease = null,
                    pendingAction = null,
                    reservation = null,
                    finalOutputs = emptyList(),
                    cleanupScopes = emptyList(),
                    finalizationCursor = null
                )
            }
            current.withRows(appended.sortedBy { row -> row.task.createdAt })
        }

    override suspend fun claimRunnableTasks(now: Instant, limit: Int): List<QueueClaim> =
        claimTasks(now, limit) { row -> row.canClaimAt(now) }

    override suspend fun claimActionRecoveryTasks(now: Instant, limit: Int): List<QueueClaim> =
        claimTasks(now, limit) { row ->
            row.canClaimAt(now) &&
                row.pendingAction?.action in setOf(
                    PendingQueueAction.PAUSE,
                    PendingQueueAction.CANCEL
                )
        }

    private suspend fun claimTasks(
        now: Instant,
        limit: Int,
        predicate: (QueueRowRecord) -> Boolean
    ): List<QueueClaim> =
        mutex.withLock {
            if (limit <= 0) {
                return emptyList()
            }

            val claimable = ledgerState.value.rows
                .filter(predicate)
                .sortedBy { it.task.createdAt }
                .take(limit)
            if (claimable.isEmpty()) {
                return emptyList()
            }

            val updatedRows = ledgerState.value.rows.map { row ->
                val claimableRow = claimable.firstOrNull { it.task.taskId == row.task.taskId }
                if (claimableRow == null) {
                    row
                } else {
                    row.copy(
                        lease = ClaimLease.claimedAt(now),
                        updatedAt = now
                    )
                }
            }
            persistLocked(ledgerState.value.withRows(updatedRows))

            updatedRows.mapNotNull { row ->
                claimable.firstOrNull { it.task.taskId == row.task.taskId }?.let {
                    row.toClaim()
                }
            }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun beginAttempt(taskId: TaskId, enteredAt: Instant): Result<Int> = try {
        var attemptCount = 0
        updateRow(taskId) { row ->
            attemptCount = when (row.state) {
                QueueTaskState.Queued,
                is QueueTaskState.RetryScheduled -> row.attemptCount + 1
                else -> row.attemptCount
            }
            row.copy(
                state = QueueTaskState.Resolving(enteredAt),
                attemptCount = attemptCount,
                pendingAction = null,
                lease = row.lease?.renewed(clock.instant()),
                updatedAt = clock.instant(),
                finalizationCursor = null
            )
        }
        Result.success(attemptCount)
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    override suspend fun releaseClaim(taskId: TaskId): Result<Unit> = updateRow(taskId) { row ->
        row.copy(
            lease = null,
            updatedAt = clock.instant()
        )
    }

    override suspend fun renewClaim(taskId: TaskId): Result<Unit> = updateRow(taskId) { row ->
        row.copy(
            lease = row.lease?.renewed(clock.instant()),
            updatedAt = clock.instant()
        )
    }

    override suspend fun resumePausedTask(taskId: TaskId): Result<Unit> = updateRow(
        taskId,
        advanceWakeGeneration = true
    ) { row ->
        row.state as? QueueTaskState.Paused
            ?: error("Resume is valid only for paused rows")
        row.copy(
            pendingAction = QueueActionRequest(
                taskId = taskId,
                action = PendingQueueAction.RESUME,
                requestedAt = clock.instant()
            ),
            lease = null,
            updatedAt = clock.instant()
        )
    }

    override suspend fun resetForManualRetry(taskId: TaskId): Result<Unit> =
        updateRow(taskId, advanceWakeGeneration = true) { row ->
            when (row.state) {
                is QueueTaskState.Failed -> {
                    row.copy(
                        state = QueueTaskState.Queued,
                        attemptCount = 0,
                        pendingAction = null,
                        lease = null,
                        reservation = null,
                        finalOutputs = emptyList(),
                        cleanupScopes = row.cleanupScopes + row.activeCleanupScope(),
                        finalizationCursor = null,
                        updatedAt = clock.instant()
                    )
                }

                else -> error("Retry is valid only for failed rows")
            }
        }

    override suspend fun restartTask(taskId: TaskId): Result<Unit> = updateRow(
        taskId,
        advanceWakeGeneration = true
    ) { row ->
        when (row.state) {
            QueueTaskState.Completed,
            is QueueTaskState.Failed,
            is QueueTaskState.Cancelled -> {
                row.copy(
                    state = QueueTaskState.Queued,
                    attemptCount = 0,
                    pendingAction = null,
                    lease = null,
                    reservation = null,
                    finalOutputs = emptyList(),
                    cleanupScopes = emptyList(),
                    finalizationCursor = null,
                    updatedAt = clock.instant()
                )
            }

            else -> error("Restart is valid only for terminal rows")
        }
    }

    override suspend fun persistState(taskId: TaskId, state: QueueTaskState): Result<Unit> =
        updateRow(taskId) { row ->
            row.copy(
                state = state,
                updatedAt = clock.instant(),
                lease = if (state.releasesLease()) {
                    null
                } else {
                    row.lease?.renewed(clock.instant())
                },
                pendingAction = if (
                    state.releasesPendingAction() ||
                    row.pendingAction?.action == PendingQueueAction.RESUME
                ) {
                    null
                } else {
                    row.pendingAction
                },
                finalizationCursor = if (state.clearsFinalizationCursor()) {
                    null
                } else {
                    row.finalizationCursor
                }
            )
        }

    override suspend fun acknowledgeStop(
        taskId: TaskId,
        checkpoint: TransferCheckpoint?
    ): Result<Unit> = updateRow(taskId) { row ->
        val pendingAction = row.pendingAction?.action
            ?: error("Stop acknowledgement requires a pending pause or cancel request")
        val nextState = when (pendingAction) {
            PendingQueueAction.CANCEL -> QueueTaskState.Cancelled(
                reason = checkpoint?.let { "Cancelled during transfer" }
            )

            PendingQueueAction.PAUSE -> QueueTaskState.Paused(
                checkpoint ?: error("Pause acknowledgement requires a checkpoint")
            )

            PendingQueueAction.RESUME ->
                error("Resume cannot be acknowledged as a stop action")
        }
        row.copy(
            state = nextState,
            lease = null,
            pendingAction = null,
            updatedAt = clock.instant(),
            finalizationCursor = when (pendingAction) {
                PendingQueueAction.PAUSE -> row.finalizationCursor
                PendingQueueAction.CANCEL,
                PendingQueueAction.RESUME -> null
            }
        )
    }

    override suspend fun persistActionRequest(
        taskId: TaskId,
        request: QueueActionRequest?
    ): Result<Unit> = updateRow(taskId) { row ->
        row.copy(
            pendingAction = row.pendingAction.mergeWith(request),
            updatedAt = clock.instant()
        )
    }

    override suspend fun persistTransferCheckpoint(
        taskId: TaskId,
        checkpoint: TransferCheckpoint
    ): Result<Unit> = updateRow(taskId) { row ->
        val nextState = when (row.state) {
            is QueueTaskState.Paused -> QueueTaskState.Paused(checkpoint)
            else -> QueueTaskState.Running(checkpoint)
        }
        row.copy(
            state = nextState,
            lease = if (nextState.releasesLease()) {
                null
            } else {
                row.lease?.renewed(clock.instant())
            },
            updatedAt = clock.instant(),
            finalizationCursor = row.finalizationCursor
        )
    }

    override suspend fun persistReservation(
        taskId: TaskId,
        reservation: OutputReservation
    ): Result<Unit> = updateRow(taskId) { row ->
        row.copy(
            reservation = reservation,
            lease = row.lease?.renewed(clock.instant()),
            updatedAt = clock.instant()
        )
    }

    override suspend fun enterFinalization(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit> = updateRow(taskId) { row ->
        row.withFinalizationCursor(checkpoint, cursor, clock.instant())
    }

    override suspend fun persistFinalizationCursor(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit> = updateRow(taskId) { row ->
        row.withFinalizationCursor(checkpoint, cursor, clock.instant())
    }

    override suspend fun reserveFreshOutput(
        taskId: TaskId,
        outputDirectoryUri: String,
        filesystemRelativePaths: Set<String>,
        createReservation: (Set<String>) -> OutputReservation
    ): Result<OutputReservation> = try {
        mutex.withLock {
            val occupiedRelativePaths = filesystemRelativePaths +
                ledgerState.value.rows
                    .asSequence()
                    .filter { row -> row.task.taskId != taskId }
                    .mapNotNull(QueueRowRecord::reservation)
                    .filter { reservation ->
                        reservation.boundOutputDirectoryUri == outputDirectoryUri
                    }
                    .flatMap { reservation ->
                        reservation.occupiedRelativePaths().asSequence()
                    }
                    .toSet()
            val reservation = createReservation(occupiedRelativePaths)
            var found = false
            val now = clock.instant()
            val updatedRows = ledgerState.value.rows.map { row ->
                if (row.task.taskId != taskId) {
                    row
                } else {
                    found = true
                    row.copy(
                        reservation = reservation,
                        lease = row.lease?.renewed(now),
                        updatedAt = now
                    )
                }
            }
            check(found) { "Unknown task ${taskId.value}" }
            persistLocked(ledgerState.value.withRows(updatedRows))
            Result.success(reservation)
        }
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
        Result.failure(throwable)
    }

    override suspend fun persistFinalOutputs(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit> = updateRow(taskId) { row ->
        row.copy(
            finalOutputs = outputs,
            lease = row.lease?.renewed(clock.instant()),
            updatedAt = clock.instant()
        )
    }

    override suspend fun completeTask(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit> = updateRow(taskId) { row ->
        row.copy(
            state = QueueTaskState.Completed,
            finalOutputs = outputs,
            updatedAt = clock.instant(),
            lease = null,
            pendingAction = null,
            finalizationCursor = null
        )
    }

    override suspend fun readNextRetryAt(): Instant? {
        val retryInstants = ledgerState.value.rows.mapNotNull { row ->
            (row.state as? QueueTaskState.RetryScheduled)?.retryAt
        }
        return retryInstants.minOrNull()
    }

    override suspend fun readNextLeaseExpiryAt(actionRecoveryOnly: Boolean): Instant? =
        ledgerState.value.rows
            .asSequence()
            .filter { row -> row.lease != null && !row.state.isTerminal() }
            .filter { row ->
                !actionRecoveryOnly || row.pendingAction?.action in setOf(
                    PendingQueueAction.PAUSE,
                    PendingQueueAction.CANCEL
                )
            }
            .mapNotNull { row -> row.lease?.leaseExpiresAt }
            .minOrNull()

    override suspend fun readRow(taskId: TaskId): QueueRowRecord? =
        ledgerState.value.rows.firstOrNull { it.task.taskId == taskId }

    override suspend fun readReservedRelativePaths(
        outputDirectoryUri: String,
        excludeTaskId: TaskId?
    ): Set<String> = ledgerState.value.rows
        .asSequence()
        .filter { row -> excludeTaskId == null || row.task.taskId != excludeTaskId }
        .mapNotNull(QueueRowRecord::reservation)
        .filter { reservation -> reservation.boundOutputDirectoryUri == outputDirectoryUri }
        .flatMap { reservation ->
            sequence {
                reservation.directOutput?.relativePath?.let { relativePath ->
                    yield(relativePath)
                }
                reservation.extractionPlan.forEach { output -> yield(output.relativePath) }
            }
        }
        .toSet()

    override suspend fun readWakeGeneration(): Long = ledgerState.value.wakeGeneration

    override suspend fun requestDispatch(): Result<Long> = try {
        mutex.withLock {
            val updated = ledgerState.value.advanceWakeGeneration()
            persistLocked(updated)
            Result.success(updated.wakeGeneration)
        }
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
        Result.failure(throwable)
    }

    override suspend fun acknowledgeDispatchStart(): Result<Long> = try {
        mutex.withLock {
            val updated = ledgerState.value.acknowledgeCurrentWakeGeneration()
            persistLocked(updated)
            Result.success(updated.wakeGeneration)
        }
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
        Result.failure(throwable)
    }

    override suspend fun hasPendingDispatch(): Boolean =
        ledgerState.value.wakeGeneration > ledgerState.value.acknowledgedWakeGeneration

    override suspend fun readRows(includeHidden: Boolean): List<QueueRowRecord> =
        ledgerState.value.rows.filterVisibility(includeHidden)

    override fun observeRows(includeHidden: Boolean): Flow<List<QueueRowRecord>> =
        ledgerState.map { current -> current.rows.filterVisibility(includeHidden) }

    override suspend fun hideTerminalRowsAtomically(taskIds: List<TaskId>): Result<Unit> =
        mutateLedger { current ->
            val taskIdsSet = taskIds.toSet()
            val updatedRows = current.rows.map { row ->
                if (row.task.taskId !in taskIdsSet) {
                    row
                } else {
                    check(row.state.isTerminal()) {
                        "Only terminal rows may be hidden"
                    }
                    row.copy(
                        visibility = QueueVisibility.HIDDEN,
                        updatedAt = clock.instant()
                    )
                }
            }
            current.withRows(updatedRows)
        }

    private suspend fun updateRow(
        taskId: TaskId,
        advanceWakeGeneration: Boolean = false,
        transform: (QueueRowRecord) -> QueueRowRecord
    ): Result<Unit> = mutateLedger(advanceWakeGeneration) { current ->
        var found = false
        val updatedRows = current.rows.map { row ->
            if (row.task.taskId != taskId) {
                row
            } else {
                found = true
                transform(row)
            }
        }
        check(found) { "Unknown task ${taskId.value}" }
        current.withRows(updatedRows)
    }

    private suspend fun mutateLedger(
        advanceWakeGeneration: Boolean = false,
        transform: (QueueLedgerState) -> QueueLedgerState
    ): Result<Unit> = try {
        mutex.withLock {
            val updated = transform(ledgerState.value)
                .let { state ->
                    if (advanceWakeGeneration) {
                        state.advanceWakeGeneration()
                    } else {
                        state
                    }
                }
            persistLocked(updated)
        }
        Result.success(Unit)
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
        Result.failure(throwable)
    }

    private fun persistLocked(updated: QueueLedgerState) {
        ensureParentDirectory()
        val tempFile = File(ledgerFile.parentFile, "${ledgerFile.name}.tmp")
        tempFile.writeText(
            json.encodeToString(QueueLedgerState.serializer(), updated)
        )
        replaceLedgerFile(tempFile)
        ledgerState.value = updated
    }

    private fun replaceLedgerFile(tempFile: File) {
        val tempPath = tempFile.toPath()
        val ledgerPath = ledgerFile.toPath()
        try {
            Files.move(
                tempPath,
                ledgerPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempPath,
                ledgerPath,
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun loadLedgerState(): QueueLedgerState {
        return if (!ledgerFile.exists()) {
            QueueLedgerState()
        } else {
            val encoded = ledgerFile.readText()
            runCatching {
                json.decodeFromString(QueueLedgerState.serializer(), encoded)
            }.recoverCatching {
                QueueLedgerState(
                    rows = json.decodeFromString(
                        ListSerializer(QueueRowRecord.serializer()),
                        encoded
                    )
                )
            }.getOrElse { throwable ->
                throw IllegalStateException("Queue ledger could not be read", throwable)
            }
        }
    }

    private fun ensureParentDirectory() {
        val parentDirectory = ledgerFile.parentFile ?: return
        if (parentDirectory.exists()) {
            return
        }
        check(parentDirectory.mkdirs()) { "Queue ledger directory could not be created" }
    }

    private fun List<QueueRowRecord>.filterVisibility(
        includeHidden: Boolean
    ): List<QueueRowRecord> =
        if (includeHidden) {
            this
        } else {
            filter { row -> row.visibility == QueueVisibility.VISIBLE }
        }

    private fun QueueRowRecord.canClaimAt(now: Instant): Boolean {
        val leaseExpired = lease?.leaseExpiresAt?.let { expiry -> !expiry.isAfter(now) } ?: true
        if (!leaseExpired) {
            return false
        }

        return when (state) {
            QueueTaskState.Queued -> true
            is QueueTaskState.Resolving -> true
            is QueueTaskState.Preparing -> true
            is QueueTaskState.Running -> true
            is QueueTaskState.RetryScheduled -> !state.retryAt.isAfter(now)
            is QueueTaskState.Paused -> pendingAction?.action == PendingQueueAction.RESUME
            QueueTaskState.Completed,
            is QueueTaskState.Failed,
            is QueueTaskState.Cancelled -> false
        }
    }

    private fun QueueLedgerState.withRows(rows: List<QueueRowRecord>): QueueLedgerState =
        copy(rows = rows)

    private fun QueueLedgerState.advanceWakeGeneration(): QueueLedgerState = copy(
        wakeGeneration = wakeGeneration + 1
    )

    private fun QueueLedgerState.acknowledgeCurrentWakeGeneration(): QueueLedgerState = copy(
        acknowledgedWakeGeneration = maxOf(
            acknowledgedWakeGeneration,
            wakeGeneration
        )
    )
}

private fun QueueRowRecord.withFinalizationCursor(
    checkpoint: TransferCheckpoint,
    cursor: FinalizationCursor,
    now: Instant
): QueueRowRecord = copy(
    state = QueueTaskState.Running(checkpoint),
    finalizationCursor = cursor,
    lease = lease?.renewed(now),
    updatedAt = now
)

private fun QueueRowRecord.toClaim(): QueueClaim = QueueClaim(
    task = task,
    state = state,
    lease = checkNotNull(lease),
    attemptCount = attemptCount,
    pendingAction = pendingAction,
    reservation = reservation,
    finalOutputs = finalOutputs,
    finalizationCursor = finalizationCursor
)

private fun QueueTaskState.isTerminal(): Boolean = when (this) {
    QueueTaskState.Queued,
    is QueueTaskState.Resolving,
    is QueueTaskState.Preparing,
    is QueueTaskState.Running,
    is QueueTaskState.Paused,
    is QueueTaskState.RetryScheduled -> false
    QueueTaskState.Completed,
    is QueueTaskState.Failed,
    is QueueTaskState.Cancelled -> true
}

private fun QueueTaskState.releasesLease(): Boolean = when (this) {
    QueueTaskState.Queued,
    is QueueTaskState.Resolving,
    is QueueTaskState.Preparing,
    is QueueTaskState.Running -> false

    is QueueTaskState.Paused,
    is QueueTaskState.RetryScheduled,
    QueueTaskState.Completed,
    is QueueTaskState.Failed,
    is QueueTaskState.Cancelled -> true
}

private fun QueueTaskState.releasesPendingAction(): Boolean = when (this) {
    is QueueTaskState.Paused,
    is QueueTaskState.Cancelled,
    QueueTaskState.Completed,
    is QueueTaskState.Failed,
    is QueueTaskState.RetryScheduled -> true

    QueueTaskState.Queued,
    is QueueTaskState.Resolving,
    is QueueTaskState.Preparing,
    is QueueTaskState.Running -> false
}

private fun QueueTaskState.clearsFinalizationCursor(): Boolean = when (this) {
    QueueTaskState.Queued,
    is QueueTaskState.Resolving,
    is QueueTaskState.Preparing,
    is QueueTaskState.Running -> true

    is QueueTaskState.Paused -> false
    is QueueTaskState.RetryScheduled,
    QueueTaskState.Completed,
    is QueueTaskState.Failed,
    is QueueTaskState.Cancelled -> true
}

private fun ClaimLease.Companion.claimedAt(now: Instant): ClaimLease = ClaimLease(
    claimedAt = now,
    leaseExpiresAt = now.plusMillis(CLAIM_LEASE_DURATION.inWholeMilliseconds)
)

private fun ClaimLease.renewed(now: Instant): ClaimLease = copy(
    leaseExpiresAt = now.plusMillis(CLAIM_LEASE_DURATION.inWholeMilliseconds)
)

private fun OutputReservation.occupiedRelativePaths(): Set<String> = buildSet {
    directOutput?.relativePath?.let(::add)
    extractionPlan.forEach { output -> add(output.relativePath) }
}

private fun QueueRowRecord.activeCleanupScope(): List<OutputCleanupScope> =
    listOfNotNull(
        OutputCleanupScope(
            reservation = reservation,
            finalOutputs = finalOutputs
        ).takeIf { scope -> scope.reservation != null || scope.finalOutputs.isNotEmpty() }
    )

private fun QueueActionRequest?.mergeWith(
    request: QueueActionRequest?
): QueueActionRequest? {
    val current = this
    return when {
        request == null -> null
        current == null -> request
        current.action == PendingQueueAction.CANCEL &&
            request.action == PendingQueueAction.PAUSE -> current

        current.action == PendingQueueAction.PAUSE &&
            request.action == PendingQueueAction.CANCEL -> request

        else -> request
    }
}
