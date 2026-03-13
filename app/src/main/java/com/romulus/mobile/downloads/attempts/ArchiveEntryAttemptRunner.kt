@file:Suppress(
    "ChainMethodContinuation",
    "CyclomaticComplexMethod",
    "LongMethod",
    "ReturnCount",
    "ThrowsCount",
    "TooGenericExceptionCaught"
)

package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.downloads.output.OutputFinalizationOutcome
import com.romulus.mobile.downloads.output.OutputFinalizationPersistence
import com.romulus.mobile.downloads.output.OutputFinalizer
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.queue.ControlSignal
import com.romulus.mobile.downloads.queue.FailureReason
import com.romulus.mobile.downloads.queue.QueueClaim
import com.romulus.mobile.downloads.queue.QueueExecutionContext
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.RecoveryDecision
import com.romulus.mobile.downloads.queue.TransferCheckpoint
import com.romulus.mobile.realdebrid.AuthRequiredException
import com.romulus.mobile.remotezip.CopySelectedEntryRequest
import java.io.File
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

internal class ArchiveEntryAttemptRunner internal constructor(
    private val archiveContainerGateway: ArchiveContainerGateway?,
    private val remoteZipCopyGateway: RemoteZipCopyGateway?,
    private val outputReservationService: OutputReservationService?,
    private val outputFinalizer: OutputFinalizer?,
    private val queueService: QueueService?,
    private val clock: Clock?
) {
    constructor() : this(
        archiveContainerGateway = null,
        remoteZipCopyGateway = null,
        outputReservationService = null,
        outputFinalizer = null,
        queueService = null,
        clock = null
    )

    suspend fun run(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision,
        controlHandle: ControlHandle
    ): AttemptOutcome {
        val archiveContainerGateway = archiveContainerGateway
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
            )
        val remoteZipCopyGateway = remoteZipCopyGateway
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
            )
        val outputReservationService = outputReservationService
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
            )
        val outputFinalizer = outputFinalizer
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
            )
        val queueService = queueService
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
            )
        val clock = clock
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
            )

        val executionContext = claim.task.executionContext as? QueueExecutionContext.ArchiveEntry
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Queue row is not an archive-entry task")
            )
        if (controlHandle.current() == ControlSignal.CANCEL) {
            return AttemptOutcome.Cancelled(null)
        }
        if (recoveryDecision is RecoveryDecision.ResumeFinalization) {
            return finalizeReservedArtifact(
                claim = claim,
                reservation = recoveryDecision.reservation,
                checkpoint = recoveryDecision.checkpoint,
                initialCursor = recoveryDecision.cursor,
                controlHandle = controlHandle,
                outputFinalizer = outputFinalizer,
                queueService = queueService,
                clock = clock
            )
        }

        val refreshedContainer = try {
            archiveContainerGateway.resolveReadyArchiveContainer(
                executionContext.preparationKey
            ).getOrElse { failure ->
                throw failure
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (failure: AuthRequiredException) {
            return AttemptOutcome.Failed(
                reason = FailureReason.AuthRequired(failure.message ?: "Auth required"),
                retryable = false
            )
        } catch (failure: Exception) {
            return AttemptOutcome.Failed(
                reason = FailureReason.ProviderFailure(
                    stage = "Resolve",
                    message = failure.message ?: "Outer ZIP could not be refreshed"
                )
            )
        }

        if (controlHandle.current() == ControlSignal.CANCEL) {
            return AttemptOutcome.Cancelled(null)
        }

        val reservation = resolveReservation(
            claim = claim,
            recoveryDecision = recoveryDecision,
            outputReservationService = outputReservationService,
            queueService = queueService
        ).getOrElse { failure ->
            return AttemptOutcome.Failed(failure.toReservationFailure())
        }

        val totalBytes = executionContext.archiveEntryIdentity.uncompressedSize
            .takeIf { it >= 0L }
        val resumeOffset = when (recoveryDecision) {
            is RecoveryDecision.ResumeRunning -> recoveryDecision.checkpoint.resumeByteOffset
            else -> 0L
        }
        val initialCheckpoint = TransferCheckpoint(
            downloadedBytes = resumeOffset,
            totalBytes = totalBytes,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = resumeOffset
        )
        queueService.recordRunning(claim.task.taskId, initialCheckpoint).getOrElse { failure ->
            return AttemptOutcome.Failed(
                FailureReason.OutputFailure(
                    failure.message ?: "Running state could not be persisted"
                )
            )
        }

        var lastPersistedAt = initialCheckpoint.lastPersistedAt
        val copyResult = remoteZipCopyGateway.copySelectedEntry(
            CopySelectedEntryRequest(
                archiveUrl = refreshedContainer.archiveUrl,
                identity = executionContext.archiveEntryIdentity,
                destination = File(reservation.tempArtifactPath).toPath(),
                resumeByteOffset = resumeOffset,
                onProgress = { downloadedBytes ->
                    val now = clock.instant()
                    val checkpoint = TransferCheckpoint(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        lastPersistedAt = now,
                        tempFileToken = null,
                        resumeByteOffset = downloadedBytes
                    )
                    when (controlHandle.current()) {
                        ControlSignal.PAUSE,
                        ControlSignal.CANCEL -> throw ArchiveCopyInterruptedException(
                            signal = controlHandle.current(),
                            checkpoint = checkpoint
                        )

                        ControlSignal.NONE -> Unit
                    }
                    if (Duration.between(lastPersistedAt, now) >= CHECKPOINT_INTERVAL) {
                        queueService.recordRunning(
                            claim.task.taskId,
                            checkpoint
                        ).getOrElse { failure ->
                            throw QueueStatePersistenceException(
                                failure.message ?: "Transfer checkpoint could not be persisted",
                                failure
                            )
                        }
                        lastPersistedAt = now
                    }
                }
            )
        )
        copyResult.exceptionOrNull()?.let { failure ->
            return when (failure) {
                is ArchiveCopyInterruptedException -> when (failure.signal) {
                    ControlSignal.PAUSE -> AttemptOutcome.Paused(failure.checkpoint)
                    ControlSignal.CANCEL -> AttemptOutcome.Cancelled(failure.checkpoint)
                    ControlSignal.NONE -> AttemptOutcome.Failed(
                        FailureReason.OutputFailure("Archive copy stopped without a control signal")
                    )
                }

                is QueueStatePersistenceException -> AttemptOutcome.Failed(
                    FailureReason.OutputFailure(failure.message.orEmpty())
                )

                is AuthRequiredException -> AttemptOutcome.Failed(
                    reason = FailureReason.AuthRequired(failure.message ?: "Auth required"),
                    retryable = false
                )

                else -> AttemptOutcome.Failed(
                    reason = FailureReason.ProviderFailure(
                        stage = "Download",
                        message = failure.message ?: "Archive entry could not be copied"
                    )
                )
            }
        }

        val finalCheckpoint = TransferCheckpoint(
            downloadedBytes = totalBytes ?: File(reservation.tempArtifactPath).length(),
            totalBytes = totalBytes,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = totalBytes ?: File(reservation.tempArtifactPath).length()
        )
        val initialCursor = outputFinalizer.createInitialCursor(reservation)
        queueService.enterFinalization(
            taskId = claim.task.taskId,
            checkpoint = finalCheckpoint,
            cursor = initialCursor
        ).getOrElse { failure ->
            return AttemptOutcome.Failed(
                FailureReason.OutputFailure(
                    failure.message ?: "Finalization start could not be persisted"
                )
            )
        }
        return finalizeReservedArtifact(
            claim = claim,
            reservation = reservation,
            checkpoint = finalCheckpoint,
            initialCursor = initialCursor,
            controlHandle = controlHandle,
            outputFinalizer = outputFinalizer,
            queueService = queueService,
            clock = clock
        )
    }

    private suspend fun resolveReservation(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision,
        outputReservationService: OutputReservationService,
        queueService: QueueService
    ): Result<OutputReservation> = when (recoveryDecision) {
        is RecoveryDecision.ResumeRunning -> Result.success(recoveryDecision.reservation)
        else -> {
            val existingReservation = claim.reservation
            if (existingReservation != null) {
                Result.success(existingReservation)
            } else {
                outputReservationService.resolveFreshBinding().fold(
                    onSuccess = { binding ->
                        outputReservationService.listRelativePaths(
                            outputDirectoryUri = binding.outputDirectoryUri,
                            subfolder = claim.task.storageTarget.subfolder
                        ).fold(
                            onSuccess = { filesystemRelativePaths ->
                                queueService.reserveFreshOutput(
                                    taskId = claim.task.taskId,
                                    outputDirectoryUri = binding.outputDirectoryUri,
                                    filesystemRelativePaths = filesystemRelativePaths
                                ) { occupiedRelativePaths ->
                                    outputReservationService.createReservation(
                                        task = claim.task,
                                        binding = binding,
                                        occupiedRelativePaths = occupiedRelativePaths
                                    )
                                }
                            },
                            onFailure = { failure -> Result.failure(failure) }
                        )
                    },
                    onFailure = { failure -> Result.failure(failure) }
                )
            }
        }
    }

    private suspend fun finalizeReservedArtifact(
        claim: QueueClaim,
        reservation: OutputReservation,
        checkpoint: TransferCheckpoint,
        initialCursor: com.romulus.mobile.downloads.queue.FinalizationCursor?,
        controlHandle: ControlHandle,
        outputFinalizer: OutputFinalizer,
        queueService: QueueService,
        clock: Clock
    ): AttemptOutcome {
        val finalization = outputFinalizer.finalize(
            task = claim.task,
            reservation = reservation,
            artifactPath = reservation.tempArtifactPath,
            existingOutputs = claim.finalOutputs,
            initialCursor = initialCursor,
            persistence = OutputFinalizationPersistence(
                onReservationUpdated = { updatedReservation ->
                    queueService.persistReservation(
                        taskId = claim.task.taskId,
                        reservation = updatedReservation
                    )
                },
                onOutputsUpdated = { outputs ->
                    queueService.persistFinalOutputs(
                        taskId = claim.task.taskId,
                        outputs = outputs
                    )
                },
                onCursorUpdated = { cursor ->
                    queueService.persistFinalizationCursor(
                        taskId = claim.task.taskId,
                        checkpoint = checkpoint,
                        cursor = cursor
                    )
                },
                occupiedRelativePaths = {
                    queueService.readReservedRelativePaths(
                        outputDirectoryUri = reservation.boundOutputDirectoryUri,
                        excludeTaskId = claim.task.taskId
                    )
                }
            ),
            control = QueueFinalizationControl(
                taskId = claim.task.taskId,
                controlHandle = controlHandle,
                queueService = queueService,
                clock = clock
            )
        ).getOrElse { failure ->
            outputReservationService?.revalidateCurrentOutputDirectory()
            return AttemptOutcome.Failed(failure.toReservationFailure())
        }
        return when (finalization) {
            is OutputFinalizationOutcome.Completed -> AttemptOutcome.Completed(
                reservation = finalization.result.reservation,
                outputs = finalization.result.outputs
            )

            is OutputFinalizationOutcome.Stopped -> when (finalization.signal) {
                ControlSignal.PAUSE -> AttemptOutcome.Paused(checkpoint)
                ControlSignal.CANCEL -> AttemptOutcome.Cancelled(checkpoint)
                ControlSignal.NONE -> AttemptOutcome.Failed(
                    FailureReason.OutputFailure("Finalization stopped without a control signal")
                )
            }
        }
    }

    private class ArchiveCopyInterruptedException(
        val signal: ControlSignal,
        val checkpoint: TransferCheckpoint
    ) : IllegalStateException("Archive copy interrupted with $signal")

    private companion object {
        val CHECKPOINT_INTERVAL: Duration = Duration.ofSeconds(3)
    }
}
