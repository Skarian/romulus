@file:Suppress(
    "ArgumentListWrapping",
    "ChainMethodContinuation",
    "FunctionSignature",
    "ImportOrdering",
    "Indentation",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads.queue

import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.downloads.attempts.AttemptOutcome
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
    private val clock: Clock,
    private val diagnosticsFacade: DiagnosticsFacade? = null
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
                storageTarget = input.storageTarget,
                executionContext = input.executionContext
            )
        }

        val result = ledgerStore.insertTasks(tasks).fold(
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
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.DOWNLOADS,
            event = "enqueue",
            outcome = when (result) {
                is EnqueueResult.Enqueued,
                is EnqueueResult.EnqueuedPendingDispatch -> "succeeded"
                is EnqueueResult.Rejected -> "rejected"
                is EnqueueResult.Failed -> "failed"
            },
            context = buildMap {
                put("selectedCount", inputs.size.toString())
                when (result) {
                    is EnqueueResult.Rejected -> put("message", result.message)
                    is EnqueueResult.Failed -> put("message", result.message)
                    is EnqueueResult.EnqueuedPendingDispatch -> put("message", result.message)
                    is EnqueueResult.Enqueued -> Unit
                }
            }
        )
        return result
    }

    @Suppress("LongMethod")
    suspend fun performAction(command: QueueActionCommand): Result<Unit> {
        val row = ledgerStore.readRow(command.taskId)
            ?: return Result.failure(
                IllegalStateException("Unknown task ${command.taskId.value}")
            )

        val result = when (command) {
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
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.DOWNLOADS,
            event = "queue-action",
            outcome = if (result.isSuccess) "succeeded" else "failed",
            taskId = command.taskId.value,
            context = buildMap {
                put("action", command.actionName())
                result.exceptionOrNull()?.message?.let { put("message", it) }
            }
        )
        return result
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
        val result = ledgerStore.hideTerminalRowsAtomically(taskIds)
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.DOWNLOADS,
            event = "clear-history",
            outcome = if (result.isSuccess) "succeeded" else "failed",
            context = buildMap {
                put("includeFailed", includeFailed.toString())
                put("clearedCount", taskIds.size.toString())
                result.exceptionOrNull()?.message?.let { put("message", it) }
            }
        )
        return result
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
        ledgerStore.persistState(taskId, QueueTaskState.Preparing(metadata)).onSuccess {
            diagnosticsFacade?.record(
                domain = DiagnosticDomain.DOWNLOADS,
                event = "state-transition",
                outcome = "observed",
                taskId = taskId.value,
                context = buildMap {
                    put("state", "Preparing")
                    metadata.lastProviderStatus?.let { put("providerStatus", it) }
                    metadata.lastProviderProgress?.let {
                        put("providerProgressPercent", it.toString())
                    }
                }
            )
        }

    suspend fun recordRunning(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit> =
        ledgerStore.persistState(taskId, QueueTaskState.Running(checkpoint)).onSuccess {
            diagnosticsFacade?.record(
                domain = DiagnosticDomain.DOWNLOADS,
                event = "state-transition",
                outcome = "observed",
                taskId = taskId.value,
                context = mapOf(
                    "state" to "Running",
                    "downloadedBytes" to checkpoint.downloadedBytes.toString()
                )
            )
        }

    suspend fun acknowledgePause(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit> =
        ledgerStore.acknowledgeStop(taskId, checkpoint).onSuccess {
            diagnosticsFacade?.record(
                domain = DiagnosticDomain.DOWNLOADS,
                event = "state-transition",
                outcome = "observed",
                taskId = taskId.value,
                context = mapOf("state" to "Paused")
            )
        }

    suspend fun acknowledgeCancel(
        taskId: TaskId,
        checkpoint: TransferCheckpoint?
    ): Result<Unit> =
        ledgerStore.acknowledgeStop(taskId, checkpoint).onSuccess {
            diagnosticsFacade?.record(
                domain = DiagnosticDomain.DOWNLOADS,
                event = "state-transition",
                outcome = "observed",
                taskId = taskId.value,
                context = mapOf("state" to "Cancelled")
            )
        }

    suspend fun settleAttemptOutcome(
        taskId: TaskId,
        outcome: AttemptOutcome,
        attemptIndex: Int
    ): Result<Unit> =
        ledgerStore.readRow(taskId)?.let { row ->
            if (
                row.pendingAction?.action == PendingQueueAction.CANCEL &&
                outcome !is AttemptOutcome.Completed
            ) {
                acknowledgeCancel(
                    taskId = taskId,
                    checkpoint = checkpointForCancelOverride(row, outcome)
                )
            } else {
                when (outcome) {
                    is AttemptOutcome.Completed -> complete(
                        taskId = taskId,
                        outputs = outcome.outputs
                    )

                    is AttemptOutcome.Paused -> acknowledgePause(taskId, outcome.checkpoint)

                    is AttemptOutcome.Cancelled -> acknowledgeCancel(taskId, outcome.checkpoint)

                    is AttemptOutcome.Failed -> settleFailure(
                        taskId = taskId,
                        reason = outcome.reason,
                        attemptIndex = attemptIndex,
                        retryable = outcome.retryable
                    )
                }
            }
        } ?: Result.failure(IllegalStateException("Unknown task ${taskId.value}"))

    suspend fun scheduleRetry(taskId: TaskId, retryAt: Instant, attemptIndex: Int): Result<Unit> =
        ledgerStore.persistState(
            taskId,
            QueueTaskState.RetryScheduled(
                retryAt = retryAt,
                attemptIndex = attemptIndex
            )
        ).fold(
            onSuccess = {
                diagnosticsFacade?.record(
                    domain = DiagnosticDomain.DOWNLOADS,
                    event = "state-transition",
                    outcome = "observed",
                    taskId = taskId.value,
                    context = mapOf(
                        "state" to "Retry Scheduled",
                        "attemptIndex" to attemptIndex.toString()
                    )
                )
                workScheduler.scheduleNextDeferredWake()
            },
            onFailure = { throwable -> Result.failure(throwable) }
        )

    suspend fun complete(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit> = ledgerStore.completeTask(taskId, outputs).onSuccess {
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.DOWNLOADS,
            event = "state-transition",
            outcome = "observed",
            taskId = taskId.value,
            context = mapOf(
                "state" to "Completed",
                "outputCount" to outputs.size.toString()
            )
        )
    }

    suspend fun fail(taskId: TaskId, reason: FailureReason): Result<Unit> =
        ledgerStore.persistState(taskId, QueueTaskState.Failed(reason)).onSuccess {
            diagnosticsFacade?.record(
                domain = DiagnosticDomain.DOWNLOADS,
                event = "state-transition",
                outcome = "failed",
                taskId = taskId.value,
                context = mapOf(
                    "state" to "Failed",
                    "message" to reason.diagnosticMessage()
                )
            )
        }

    private suspend fun settleFailure(
        taskId: TaskId,
        reason: FailureReason,
        attemptIndex: Int,
        retryable: Boolean
    ): Result<Unit> {
        val retryDelay = retryDelayFor(
            reason = reason,
            attemptIndex = attemptIndex,
            retryable = retryable
        )
        if (retryDelay == null) {
            return fail(taskId, reason)
        }
        return scheduleRetry(
            taskId = taskId,
            retryAt = clock.instant().plus(retryDelay),
            attemptIndex = attemptIndex
        )
    }

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

    private fun checkpointForCancelOverride(
        row: QueueRowRecord,
        outcome: AttemptOutcome
    ): TransferCheckpoint? = when (outcome) {
        is AttemptOutcome.Paused -> outcome.checkpoint
        is AttemptOutcome.Cancelled -> outcome.checkpoint
        is AttemptOutcome.Failed,
        is AttemptOutcome.Completed -> when (val state = row.state) {
            is QueueTaskState.Running -> state.checkpoint
            is QueueTaskState.Paused -> state.checkpoint
            else -> null
        }
    }

    private fun retryDelayFor(
        reason: FailureReason,
        attemptIndex: Int,
        retryable: Boolean
    ): java.time.Duration? =
        when {
            !retryable -> null
            reason is FailureReason.AuthRequired ||
                reason is FailureReason.DirectoryAccessFailure -> null

            else -> when (attemptIndex) {
            FIRST_ATTEMPT_INDEX -> java.time.Duration.ZERO
            SECOND_ATTEMPT_INDEX -> RETRY_DELAY_SECOND
            THIRD_ATTEMPT_INDEX -> RETRY_DELAY_THIRD
            else -> null
        }
        }

    private fun QueueRowRecord.restartCleanupScopes(): List<OutputCleanupScope> =
        cleanupScopes + listOfNotNull(
            OutputCleanupScope(
                reservation = reservation,
                finalOutputs = finalOutputs
            ).takeIf { scope -> scope.reservation != null || scope.finalOutputs.isNotEmpty() }
        )

    private companion object {
        const val FIRST_ATTEMPT_INDEX = 1
        const val SECOND_ATTEMPT_INDEX = 2
        const val THIRD_ATTEMPT_INDEX = 3
        val RETRY_DELAY_SECOND: java.time.Duration = java.time.Duration.ofSeconds(15)
        val RETRY_DELAY_THIRD: java.time.Duration = java.time.Duration.ofSeconds(60)
    }
}

private fun QueueActionCommand.actionName(): String = when (this) {
    is QueueActionCommand.Pause -> "Pause"
    is QueueActionCommand.Resume -> "Resume"
    is QueueActionCommand.Cancel -> "Cancel"
    is QueueActionCommand.Retry -> "Retry"
    is QueueActionCommand.Restart -> "Restart"
}

private fun FailureReason.diagnosticMessage(): String = when (this) {
    is FailureReason.AuthRequired -> message
    is FailureReason.ProviderFailure -> message
    is FailureReason.OutputFailure -> message
    is FailureReason.DirectoryAccessFailure -> message
}
