@file:Suppress(
    "ArgumentListWrapping",
    "ChainMethodContinuation",
    "FunctionSignature",
    "ImportOrdering",
    "Indentation",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.OutputCleanupScope
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.work.ExecutionControlRegistry
import com.romulus.mobile.downloads.work.WorkScheduler
import com.romulus.mobile.downloads.work.WorkWakeReason
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Suppress("TooManyFunctions")
internal class QueueService(
    private val ledgerStore: DownloadLedgerStore,
    private val executionControlRegistry: ExecutionControlRegistry,
    private val outputCleanupService: OutputCleanupService,
    private val workScheduler: WorkScheduler,
    private val clock: Clock
) {
    suspend fun enqueue(inputs: List<QueueTaskInput>): EnqueueResult {
        if (inputs.isEmpty()) {
            return EnqueueResult.Rejected("No downloads were selected")
        }

        val createdAt = clock.instant()
        val tasks = inputs.mapIndexed { index, input ->
            QueueTask(
                taskId = TaskId(UUID.randomUUID().toString()),
                createdAt = createdAt.plusMillis(index.toLong()),
                snapshotId = input.snapshotId,
                entryId = input.entryId,
                selectedItemId = input.selectedItemId,
                originalDisplayName = input.originalDisplayName,
                originalSizeBytes = input.originalSizeBytes,
                sourceMetadata = input.sourceMetadata,
                namingIntent = input.namingIntent,
                unarchiveIntent = input.unarchiveIntent,
                recursiveUnarchiveIntent = input.recursiveUnarchiveIntent,
                storageTarget = input.storageTarget,
                executionContext = input.executionContext
            )
        }

        return ledgerStore.insertTasks(tasks).fold(
            onSuccess = {
                workScheduler.requestWake(WorkWakeReason.ENQUEUE).fold(
                    onSuccess = { EnqueueResult.Enqueued(tasks.map(QueueTask::taskId)) },
                    onFailure = { throwable ->
                        EnqueueResult.EnqueuedPendingDispatch(
                            taskIds = tasks.map(QueueTask::taskId),
                            message = throwable.message
                                ?: "Downloads were queued, but background dispatch could not be requested"
                        )
                    }
                )
            },
            onFailure = { throwable ->
                EnqueueResult.Failed(throwable.message ?: "Queue insert failed")
            }
        )
    }

    suspend fun performAction(command: QueueActionCommand): Result<Unit> {
        val row = ledgerStore.readRow(command.taskId)
            ?: return Result.failure(
                IllegalStateException("Unknown task ${command.taskId.value}")
            )

        return when (command) {
            is QueueActionCommand.Pause -> when (row.state) {
                is QueueTaskState.Running -> ledgerStore.persistActionRequest(
                    command.taskId,
                    QueueActionRequest(
                        taskId = command.taskId,
                        action = PendingQueueAction.PAUSE,
                        requestedAt = clock.instant()
                    )
                ).fold(
                    onSuccess = {
                        executionControlRegistry.signal(command.taskId, ControlSignal.PAUSE)
                    },
                    onFailure = { throwable -> Result.failure(throwable) }
                )

                else -> Result.failure(
                    IllegalStateException("Pause is valid only for running rows")
                )
            }

            is QueueActionCommand.Resume -> ledgerStore.resumePausedTask(command.taskId).fold(
                onSuccess = {
                    workScheduler.requestWake(WorkWakeReason.RESUME)
                },
                onFailure = { throwable -> Result.failure(throwable) }
            )

            is QueueActionCommand.Cancel -> cancel(row)
            is QueueActionCommand.Retry -> ledgerStore.resetForManualRetry(command.taskId).fold(
                onSuccess = {
                    workScheduler.requestWake(WorkWakeReason.MANUAL_RETRY)
                },
                onFailure = { throwable -> Result.failure(throwable) }
            )

            is QueueActionCommand.Restart -> outputCleanupService.cleanupForRestart(
                row.restartCleanupScopes()
            ).fold(
                onSuccess = {
                    ledgerStore.restartTask(command.taskId).fold(
                        onSuccess = { workScheduler.requestWake(WorkWakeReason.RESTART) },
                        onFailure = { throwable -> Result.failure(throwable) }
                    )
                },
                onFailure = { throwable -> Result.failure(throwable) }
            )
        }
    }

    suspend fun clearHistory(includeFailed: Boolean): Result<Unit> {
        val visibleRows = ledgerStore.readRows(includeHidden = false)
        val taskIds = visibleRows.filter { row ->
            when (row.state) {
                QueueTaskState.Completed,
                is QueueTaskState.Cancelled -> true
                is QueueTaskState.Failed -> includeFailed
                else -> false
            }
        }.map { row ->
            row.task.taskId
        }

        if (taskIds.isEmpty()) {
            return Result.success(Unit)
        }
        return ledgerStore.hideTerminalRowsAtomically(taskIds)
    }

    suspend fun claimRunnableTasks(now: Instant, limit: Int): List<QueueClaim> =
        ledgerStore.claimRunnableTasks(now, limit)

    suspend fun claimActionRecoveryTasks(now: Instant, limit: Int): List<QueueClaim> =
        ledgerStore.claimActionRecoveryTasks(now, limit)

    suspend fun readWakeGeneration(): Long = ledgerStore.readWakeGeneration()

    suspend fun acknowledgeDispatchStart(): Result<Long> = ledgerStore.acknowledgeDispatchStart()

    suspend fun beginAttempt(taskId: TaskId): Result<Int> =
        ledgerStore.beginAttempt(taskId, clock.instant())

    suspend fun requeue(taskId: TaskId, state: QueueTaskState): Result<Unit> =
        ledgerStore.persistState(taskId, state)

    suspend fun releaseClaim(taskId: TaskId): Result<Unit> = ledgerStore.releaseClaim(taskId)

    suspend fun renewClaim(taskId: TaskId): Result<Unit> = ledgerStore.renewClaim(taskId)

    suspend fun persistReservation(taskId: TaskId, reservation: OutputReservation): Result<Unit> =
        ledgerStore.persistReservation(taskId, reservation)

    suspend fun enterFinalization(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit> = ledgerStore.enterFinalization(
        taskId = taskId,
        checkpoint = checkpoint,
        cursor = cursor
    )

    suspend fun persistFinalizationCursor(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit> = ledgerStore.persistFinalizationCursor(
        taskId = taskId,
        checkpoint = checkpoint,
        cursor = cursor
    )

    suspend fun reserveFreshOutput(
        taskId: TaskId,
        outputDirectoryUri: String,
        filesystemRelativePaths: Set<String>,
        createReservation: (Set<String>) -> OutputReservation
    ): Result<OutputReservation> = ledgerStore.reserveFreshOutput(
        taskId = taskId,
        outputDirectoryUri = outputDirectoryUri,
        filesystemRelativePaths = filesystemRelativePaths,
        createReservation = createReservation
    )

    suspend fun persistFinalOutputs(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit> = ledgerStore.persistFinalOutputs(taskId, outputs)

    suspend fun readReservedRelativePaths(
        outputDirectoryUri: String,
        excludeTaskId: TaskId? = null
    ): Set<String> = ledgerStore.readReservedRelativePaths(
        outputDirectoryUri = outputDirectoryUri,
        excludeTaskId = excludeTaskId
    )

    suspend fun recordPreparing(taskId: TaskId, metadata: PreparingMetadata): Result<Unit> =
        ledgerStore.persistState(taskId, QueueTaskState.Preparing(metadata))

    suspend fun recordRunning(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit> =
        ledgerStore.persistState(taskId, QueueTaskState.Running(checkpoint))

    suspend fun acknowledgePause(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit> =
        ledgerStore.acknowledgeStop(taskId, checkpoint)

    suspend fun acknowledgeCancel(
        taskId: TaskId,
        checkpoint: TransferCheckpoint?
    ): Result<Unit> =
        ledgerStore.acknowledgeStop(taskId, checkpoint)

    suspend fun scheduleRetry(taskId: TaskId, retryAt: Instant, attemptIndex: Int): Result<Unit> =
        ledgerStore.persistState(
            taskId,
            QueueTaskState.RetryScheduled(
                retryAt = retryAt,
                attemptIndex = attemptIndex
            )
        ).fold(
            onSuccess = { workScheduler.scheduleNextDeferredWake() },
            onFailure = { throwable -> Result.failure(throwable) }
        )

    suspend fun complete(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit> = ledgerStore.completeTask(taskId, outputs)

    suspend fun fail(taskId: TaskId, reason: FailureReason): Result<Unit> =
        ledgerStore.persistState(taskId, QueueTaskState.Failed(reason))

    private suspend fun cancel(row: QueueRowRecord): Result<Unit> = when (row.state) {
        QueueTaskState.Queued,
        is QueueTaskState.RetryScheduled,
        is QueueTaskState.Paused ->
            ledgerStore.persistActionRequest(
                taskId = row.task.taskId,
                request = null
            ).fold(
            onSuccess = {
                ledgerStore.persistState(
                    row.task.taskId,
                    QueueTaskState.Cancelled(reason = null)
                )
            },
            onFailure = { throwable -> Result.failure(throwable) }
        )

        is QueueTaskState.Resolving,
        is QueueTaskState.Preparing,
        is QueueTaskState.Running ->
            ledgerStore.persistActionRequest(
                row.task.taskId,
                QueueActionRequest(
                    taskId = row.task.taskId,
                    action = PendingQueueAction.CANCEL,
                    requestedAt = clock.instant()
                )
            ).fold(
                onSuccess = {
                    executionControlRegistry.signalIfRegistered(
                        row.task.taskId,
                        ControlSignal.CANCEL
                    )
                },
                onFailure = { throwable -> Result.failure(throwable) }
            )

        QueueTaskState.Completed,
        is QueueTaskState.Failed,
        is QueueTaskState.Cancelled -> Result.success(Unit)
    }

    private fun QueueRowRecord.restartCleanupScopes(): List<OutputCleanupScope> =
        cleanupScopes + listOfNotNull(
            OutputCleanupScope(
                reservation = reservation,
                finalOutputs = finalOutputs
            ).takeIf { scope -> scope.reservation != null || scope.finalOutputs.isNotEmpty() }
        )
}
