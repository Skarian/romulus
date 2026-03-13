@file:Suppress(
    "BinaryExpressionWrapping",
    "ChainMethodContinuation",
    "FunctionSignature",
    "LongParameterList",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.attempts.ArchiveEntryAttemptRunner
import com.romulus.mobile.downloads.attempts.StandardAttemptRunner
import com.romulus.mobile.downloads.queue.QueueClaim
import com.romulus.mobile.downloads.queue.QueueExecutionContext
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.QueueTaskState
import com.romulus.mobile.downloads.queue.RecoveryDecision
import java.time.Clock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

internal class DownloadWorkerEntryPoint(
    private val queueService: QueueService,
    private val recoveryPolicy: com.romulus.mobile.downloads.queue.QueueRecoveryPolicy,
    private val workScheduler: WorkScheduler,
    private val executionControlRegistry: ExecutionControlRegistry,
    private val standardAttemptRunner: StandardAttemptRunner,
    private val archiveEntryAttemptRunner: ArchiveEntryAttemptRunner,
    private val clock: Clock
) {
    @Suppress("ReturnCount")
    suspend fun runOnce(): Result<Unit> {
        queueService.acknowledgeDispatchStart().getOrElse { throwable ->
            return Result.failure(throwable)
        }
        val gate = workScheduler.readGate()
        val claimLimit = workScheduler.readClaimLimit()
        val claims = if (gate.authBlocked) {
            queueService.claimActionRecoveryTasks(
                now = clock.instant(),
                limit = claimLimit
            )
        } else {
            queueService.claimRunnableTasks(
                now = clock.instant(),
                limit = claimLimit
            )
        }
        if (claims.isEmpty()) {
            return workScheduler.scheduleNextDeferredWake()
        }
        val processingResult = coroutineScope {
            claims.map { claim ->
                async {
                    processClaim(claim)
                }
            }.awaitAll()
        }
        processingResult.firstFailure()?.let { failure ->
            return Result.failure(failure)
        }
        return workScheduler.scheduleNextDeferredWake()
    }

    @Suppress("ReturnCount")
    suspend fun runUntilDrained(): Result<Unit> {
        var observedWakeGeneration = queueService.acknowledgeDispatchStart()
            .getOrElse { throwable ->
                return Result.failure(throwable)
            }
        return coroutineScope {
            val activeAttempts = linkedSetOf<Deferred<Result<Unit>>>()
            while (true) {
                fillAvailableSlots(activeAttempts)
                if (activeAttempts.isEmpty()) {
                    workScheduler.synchronizeAuthRecovery().getOrElse { throwable ->
                        return@coroutineScope Result.failure(throwable)
                    }
                    val latestWakeGeneration = queueService.readWakeGeneration()
                    if (latestWakeGeneration != observedWakeGeneration) {
                        observedWakeGeneration =
                            queueService.acknowledgeDispatchStart().getOrElse { throwable ->
                                return@coroutineScope Result.failure(throwable)
                            }
                        continue
                    }
                    return@coroutineScope workScheduler.scheduleNextDeferredWake()
                }
                val completedAttempt = awaitNextAttempt(activeAttempts)
                activeAttempts.remove(completedAttempt.deferred)
                completedAttempt.result.exceptionOrNull()?.let { failure ->
                    return@coroutineScope Result.failure(failure)
                }
            }
            @Suppress("UnreachableCode")
            Result.success(Unit)
        }
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.fillAvailableSlots(
        activeAttempts: MutableSet<Deferred<Result<Unit>>>
    ) {
        while (true) {
            val remainingSlots = workScheduler.readClaimLimit() - activeAttempts.size
            if (remainingSlots <= 0) {
                return
            }
            val claims = claimAvailableTasks(limit = remainingSlots)
            if (claims.isEmpty()) {
                return
            }
            claims.forEach { claim ->
                activeAttempts += async {
                    processClaim(claim)
                }
            }
        }
    }

    private suspend fun claimAvailableTasks(limit: Int): List<QueueClaim> {
        if (limit <= 0) {
            return emptyList()
        }
        val gate = workScheduler.readGate()
        return if (gate.authBlocked) {
            queueService.claimActionRecoveryTasks(
                now = clock.instant(),
                limit = limit
            )
        } else {
            queueService.claimRunnableTasks(
                now = clock.instant(),
                limit = limit
            )
        }
    }

    private suspend fun awaitNextAttempt(
        activeAttempts: Set<Deferred<Result<Unit>>>
    ): CompletedAttempt =
        select {
            activeAttempts.forEach { deferred ->
                deferred.onAwait { result ->
                    CompletedAttempt(
                        deferred = deferred,
                        result = result
                    )
                }
            }
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private suspend fun processClaim(claim: QueueClaim): Result<Unit> {
        val recoveryDecision = recoveryPolicy.decide(claim)
        when (recoveryDecision) {
            is RecoveryDecision.HonorPendingPause -> {
                return queueService.acknowledgePause(
                    claim.task.taskId,
                    recoveryDecision.checkpoint
                )
            }

            is RecoveryDecision.HonorPendingCancel -> {
                return queueService.acknowledgeCancel(
                    claim.task.taskId,
                    recoveryDecision.checkpoint
                )
            }

            else -> Unit
        }

        val controlHandle = executionControlRegistry.register(claim.task.taskId)
        try {
            val attemptIndex = when (recoveryDecision) {
                is RecoveryDecision.ResumePreparing ->
                    Result.success(claim.attemptCount.coerceAtLeast(1))

                is RecoveryDecision.ResumeRunning ->
                    Result.success(claim.attemptCount.coerceAtLeast(1))

                is RecoveryDecision.ResumeFinalization ->
                    Result.success(claim.attemptCount.coerceAtLeast(1))

                is RecoveryDecision.Requeue -> beginAttempt(claim, recoveryDecision)
                else -> Result.success(claim.attemptCount)
            }.getOrElse { throwable ->
                return Result.failure(throwable)
            }

            val outcome = when (claim.task.executionContext) {
                is QueueExecutionContext.StandardFile -> standardAttemptRunner.run(
                    claim = claim,
                    recoveryDecision = recoveryDecision,
                    controlHandle = controlHandle
                )

                is QueueExecutionContext.ArchiveEntry -> archiveEntryAttemptRunner.run(
                    claim,
                    recoveryDecision,
                    controlHandle
                )
            }
            return queueService.settleAttemptOutcome(
                taskId = claim.task.taskId,
                outcome = outcome,
                attemptIndex = attemptIndex
            )
        } finally {
            executionControlRegistry.unregister(claim.task.taskId)
        }
    }

    private suspend fun beginAttempt(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision.Requeue
    ): Result<Int> {
        if (recoveryDecision.state != QueueTaskState.Queued) {
            queueService.requeue(claim.task.taskId, recoveryDecision.state).getOrElse { throwable ->
                return Result.failure(throwable)
            }
        }
        return queueService.beginAttempt(claim.task.taskId)
    }
}

private data class CompletedAttempt(val deferred: Deferred<Result<Unit>>, val result: Result<Unit>)

private fun List<Result<Unit>>.firstFailure(): Throwable? =
    firstOrNull(Result<Unit>::isFailure)?.exceptionOrNull()
