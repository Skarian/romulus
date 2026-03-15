@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.ArtifactRecoveryDisposition
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.OutputReservationService

internal sealed interface RecoveryDecision {
    data class HonorPendingPause(val checkpoint: TransferCheckpoint) : RecoveryDecision

    data class HonorPendingCancel(val checkpoint: TransferCheckpoint?) : RecoveryDecision

    data class ResumePreparing(val metadata: PreparingMetadata) : RecoveryDecision

    data class ResumeRunning(
        val checkpoint: TransferCheckpoint,
        val reservation: OutputReservation
    ) : RecoveryDecision

    data class ResumeFinalization(
        val checkpoint: TransferCheckpoint,
        val reservation: OutputReservation,
        val cursor: FinalizationCursor
    ) : RecoveryDecision

    data class Requeue(val state: QueueTaskState) : RecoveryDecision
}

internal class QueueRecoveryPolicy(private val outputReservationService: OutputReservationService) {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun decide(claim: QueueClaim): RecoveryDecision {
        when (claim.pendingAction?.action) {
            PendingQueueAction.CANCEL -> {
                val checkpoint = when (val state = claim.state) {
                    is QueueTaskState.Running -> state.checkpoint
                    is QueueTaskState.Paused -> state.checkpoint
                    else -> null
                }
                return RecoveryDecision.HonorPendingCancel(checkpoint)
            }

            PendingQueueAction.PAUSE -> {
                val checkpoint = when (val state = claim.state) {
                    is QueueTaskState.Running -> state.checkpoint
                    is QueueTaskState.Paused -> state.checkpoint
                    else -> null
                }
                if (checkpoint != null) {
                    return RecoveryDecision.HonorPendingPause(checkpoint)
                }
            }

            PendingQueueAction.RESUME -> {
                val pausedState = claim.state as? QueueTaskState.Paused
                    ?: return RecoveryDecision.Requeue(claim.state)
                return recoveryDecisionFor(
                    reservation = claim.reservation,
                    checkpoint = pausedState.checkpoint,
                    persistedCursor = claim.finalizationCursor,
                    requeueState = QueueTaskState.Queued
                )
            }

            null -> Unit
        }

        return when (val state = claim.state) {
            is QueueTaskState.Preparing -> RecoveryDecision.ResumePreparing(state.metadata)
            is QueueTaskState.Running -> recoveryDecisionFor(
                reservation = claim.reservation,
                checkpoint = state.checkpoint,
                persistedCursor = claim.finalizationCursor,
                requeueState = QueueTaskState.Queued
            )

            is QueueTaskState.Resolving -> RecoveryDecision.Requeue(QueueTaskState.Queued)
            QueueTaskState.Queued -> RecoveryDecision.Requeue(QueueTaskState.Queued)
            is QueueTaskState.RetryScheduled -> RecoveryDecision.Requeue(QueueTaskState.Queued)
            is QueueTaskState.Paused -> RecoveryDecision.Requeue(state)
            QueueTaskState.Completed,
            is QueueTaskState.Failed,
            is QueueTaskState.Cancelled -> RecoveryDecision.Requeue(state)
        }
    }

    private fun recoveryDecisionFor(
        reservation: OutputReservation?,
        checkpoint: TransferCheckpoint,
        persistedCursor: FinalizationCursor?,
        requeueState: QueueTaskState
    ): RecoveryDecision = when (
        val disposition = outputReservationService.inspectRecoveryDisposition(
            reservation = reservation,
            checkpoint = checkpoint,
            persistedCursor = persistedCursor
        )
    ) {
        ArtifactRecoveryDisposition.CannotResume -> RecoveryDecision.Requeue(requeueState)
        is ArtifactRecoveryDisposition.ResumeTransfer -> RecoveryDecision.ResumeRunning(
            checkpoint = checkpoint.copy(
                downloadedBytes = disposition.safeResumeOffset,
                resumeByteOffset = disposition.safeResumeOffset
            ),
            reservation = disposition.reservation
        )

        is ArtifactRecoveryDisposition.ResumeFinalization -> RecoveryDecision.ResumeFinalization(
            checkpoint = checkpoint,
            reservation = disposition.reservation,
            cursor = disposition.cursor
        )
    }
}
