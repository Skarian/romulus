@file:Suppress(
    "ArgumentListWrapping",
    "BinaryExpressionWrapping",
    "BlankLineBeforeDeclaration",
    "ChainMethodContinuation",
    "FunctionLiteral",
    "ImportOrdering",
    "InjectDispatcher",
    "MaximumLineLength",
    "ReturnCount"
)

package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.downloads.output.FinalizationControl
import com.romulus.mobile.downloads.output.OutputFinalizationOutcome
import com.romulus.mobile.downloads.output.OutputFinalizationPersistence
import com.romulus.mobile.downloads.output.OutputFinalizer
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.queue.FailureReason
import com.romulus.mobile.downloads.queue.PreparingMetadata
import com.romulus.mobile.downloads.queue.QueueClaim
import com.romulus.mobile.downloads.queue.QueueExecutionContext
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.RecoveryDecision
import com.romulus.mobile.downloads.queue.TransferCheckpoint
import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.AuthRequiredException
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderReadyLink
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class StandardAttemptRunner(
    private val providerGateway: ProviderRuntimeGateway,
    private val downloadTransport: DownloadTransport,
    private val outputReservationService: OutputReservationService,
    private val outputFinalizer: OutputFinalizer,
    private val queueService: QueueService,
    private val clock: Clock
) {
    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod",
        "ThrowsCount",
        "TooGenericExceptionCaught"
    )
    suspend fun run(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision,
        controlHandle: ControlHandle
    ): AttemptOutcome {
        val executionContext = claim.task.executionContext as? QueueExecutionContext.StandardFile
            ?: return AttemptOutcome.Failed(
                FailureReason.OutputFailure("Queue row is not a standard-file task")
            )

        if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
            return AttemptOutcome.Cancelled(null)
        }
        if (recoveryDecision is RecoveryDecision.ResumeFinalization) {
            return finalizeReservedArtifact(
                claim = claim,
                reservation = recoveryDecision.reservation,
                checkpoint = recoveryDecision.checkpoint,
                initialCursor = recoveryDecision.cursor,
                controlHandle = controlHandle
            )
        }

        val readyLink = try {
            acquireReadyLink(
                taskId = claim.task.taskId,
                locator = executionContext.providerLocator,
                recoveryDecision = recoveryDecision,
                controlHandle = controlHandle
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: AuthRequiredException) {
            return AttemptOutcome.Failed(
                reason = FailureReason.AuthRequired(exception.message ?: "Auth required"),
                retryable = false
            )
        } catch (exception: ProviderPreparationTimeoutException) {
            return AttemptOutcome.Failed(
                reason = FailureReason.ProviderFailure(
                    stage = "Preparing",
                    message = exception.message ?: "Provider preparation timed out after 24 hours"
                ),
                retryable = false
            )
        } catch (exception: ProviderLinkAmbiguityException) {
            return AttemptOutcome.Failed(
                reason = FailureReason.ProviderFailure(
                    stage = "Preparing",
                    message = exception.message ?: "Provider returned multiple ready links"
                ),
                retryable = false
            )
        } catch (exception: QueueStatePersistenceException) {
            return AttemptOutcome.Failed(FailureReason.OutputFailure(exception.message.orEmpty()))
        } catch (throwable: Exception) {
            return throwable.toProviderAttemptFailure(stage = "Preparing")
        } ?: return when (controlHandle.current()) {
            com.romulus.mobile.downloads.queue.ControlSignal.CANCEL -> AttemptOutcome.Cancelled(null)
            else -> AttemptOutcome.Failed(
                FailureReason.ProviderFailure(
                    stage = "Preparing",
                    message = "Provider acquisition did not yield a ready link"
                )
            )
        }

        if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
            return AttemptOutcome.Cancelled(null)
        }

        val resolvedUnit = providerGateway.resolveReadyLink(readyLink).getOrElse { throwable ->
            return throwable.toProviderAttemptFailure(stage = "Resolve")
        }

        if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
            return AttemptOutcome.Cancelled(null)
        }

        val reservation = resolveReservation(
            claim = claim,
            recoveryDecision = recoveryDecision
        ).getOrElse { throwable ->
            return AttemptOutcome.Failed(throwable.toReservationFailure())
        }

        if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
            return AttemptOutcome.Cancelled(null)
        }

        val tempArtifact = File(reservation.tempArtifactPath)
        tempArtifact.parentFile?.mkdirs()
        val resumeOffset = when (recoveryDecision) {
            is RecoveryDecision.ResumeRunning -> recoveryDecision.checkpoint.resumeByteOffset
            else -> 0L
        }

        val transport = downloadTransport.open(
            url = resolvedUnit.downloadUrl,
            resumeByteOffset = resumeOffset
        ).getOrElse { throwable ->
            return AttemptOutcome.Failed(
                FailureReason.ProviderFailure(
                    stage = "Download",
                    message = throwable.message ?: "Download could not start"
                )
            )
        }

        transport.use { stream ->
            if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
                return AttemptOutcome.Cancelled(null)
            }
            if (resumeOffset > 0L && !stream.resumeAccepted) {
                return AttemptOutcome.Failed(
                    FailureReason.ProviderFailure(
                        stage = "Download",
                        message = "Provider refused to resume the partial transfer"
                    ),
                    retryable = false
                )
            }
            return transferAndFinalize(
                claim = claim,
                reservation = reservation,
                stream = stream,
                startingOffset = resumeOffset,
                controlHandle = controlHandle
            )
        }
    }

    @Suppress("CyclomaticComplexMethod", "ThrowsCount")
    private suspend fun acquireReadyLink(
        taskId: com.romulus.mobile.downloads.queue.TaskId,
        locator: ProviderLocator,
        recoveryDecision: RecoveryDecision,
        controlHandle: ControlHandle
    ): ProviderReadyLink? {
        if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
            return null
        }
        val preparingMetadata = when (recoveryDecision) {
            is RecoveryDecision.ResumePreparing -> recoveryDecision.metadata
            else -> null
        }
        var status = when (recoveryDecision) {
            is RecoveryDecision.ResumePreparing -> providerGateway.resumeAcquisition(
                recoveryDecision.metadata.resumeMarker ?: return null
            )

            else -> providerGateway.startAcquisition(locator)
        }.getOrElse { throwable ->
            throw throwable.toAcquisitionException("Provider acquisition could not start")
        }

        val enteredAt = preparingMetadata?.enteredAt ?: clock.instant()
        val timeoutAt = preparingMetadata?.timeoutAt ?: enteredAt.plus(PREPARING_TIMEOUT)

        while (true) {
            when (status) {
                is AcquisitionStatus.LinksReady -> {
                    return status.readyLinks.singleOrNull()
                        ?: throw ProviderLinkAmbiguityException(
                            "Provider returned multiple ready links for one queue row"
                        )
                }

                is AcquisitionStatus.Waiting -> {
                    if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
                        return null
                    }
                    if (!timeoutAt.isAfter(clock.instant())) {
                        throw ProviderPreparationTimeoutException(
                            "Provider preparation timed out after 24 hours"
                        )
                    }
                    persistPreparingState(
                        taskId = taskId,
                        enteredAt = enteredAt,
                        timeoutAt = timeoutAt,
                        status = status
                    )
                    delay(POLL_INTERVAL.toMillis())
                    if (controlHandle.current() == com.romulus.mobile.downloads.queue.ControlSignal.CANCEL) {
                        return null
                    }
                    status = providerGateway.resumeAcquisition(status.resumeMarker).getOrElse { throwable ->
                        throw throwable.toAcquisitionException(
                            "Provider acquisition could not resume"
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistPreparingState(
        taskId: com.romulus.mobile.downloads.queue.TaskId,
        enteredAt: java.time.Instant,
        timeoutAt: java.time.Instant,
        status: AcquisitionStatus.Waiting
    ) {
        val metadata = PreparingMetadata(
            enteredAt = enteredAt,
            timeoutAt = timeoutAt,
            lastProviderStatus = status.statusLabel,
            lastProviderProgress = status.progressPercent,
            resumeMarker = status.resumeMarker
        )
        queueService.recordPreparing(
            taskId = taskId,
            metadata = metadata
        ).getOrElse { throwable ->
            throw QueueStatePersistenceException(
                throwable.message ?: "Preparing state could not be persisted",
                throwable
            )
        }
    }

    private suspend fun resolveReservation(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision
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
                            onFailure = { throwable -> Result.failure(throwable) }
                        )
                    },
                    onFailure = { throwable -> Result.failure(throwable) }
                )
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun transferAndFinalize(
        claim: QueueClaim,
        reservation: OutputReservation,
        stream: DownloadStream,
        startingOffset: Long,
        controlHandle: ControlHandle
    ): AttemptOutcome {
        val tempArtifact = File(reservation.tempArtifactPath)
        val initialCheckpoint = TransferCheckpoint(
            downloadedBytes = startingOffset,
            totalBytes = stream.totalBytes,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = startingOffset
        )
        queueService.recordRunning(claim.task.taskId, initialCheckpoint).getOrElse { throwable ->
            return AttemptOutcome.Failed(
                FailureReason.OutputFailure(
                    throwable.message ?: "Running state could not be persisted"
                )
            )
        }

        return try {
            withContext(Dispatchers.IO) {
                FileOutputStream(tempArtifact, startingOffset > 0L).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloadedBytes = startingOffset
                    var lastPersistedAt = clock.instant()

                    while (true) {
                        val bytesRead = stream.inputStream.read(buffer)
                        if (bytesRead < 0) {
                            break
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead.toLong()
                        val now = clock.instant()
                        val checkpoint = TransferCheckpoint(
                            downloadedBytes = downloadedBytes,
                            totalBytes = stream.totalBytes,
                            lastPersistedAt = now,
                            tempFileToken = null,
                            resumeByteOffset = downloadedBytes
                        )
                        when (controlHandle.current()) {
                            com.romulus.mobile.downloads.queue.ControlSignal.PAUSE ->
                                return@withContext AttemptOutcome.Paused(checkpoint)

                            com.romulus.mobile.downloads.queue.ControlSignal.CANCEL ->
                                return@withContext AttemptOutcome.Cancelled(checkpoint)

                            com.romulus.mobile.downloads.queue.ControlSignal.NONE -> Unit
                        }
                        if (Duration.between(lastPersistedAt, now) >= CHECKPOINT_INTERVAL) {
                            queueService.recordRunning(claim.task.taskId, checkpoint).getOrElse { throwable ->
                                return@withContext AttemptOutcome.Failed(
                                    FailureReason.OutputFailure(
                                        throwable.message ?: "Transfer checkpoint could not be persisted"
                                    )
                                )
                            }
                            lastPersistedAt = now
                        }
                    }
                    output.flush()
                    val finalCheckpoint = TransferCheckpoint(
                        downloadedBytes = downloadedBytes,
                        totalBytes = stream.totalBytes,
                        lastPersistedAt = clock.instant(),
                        tempFileToken = null,
                        resumeByteOffset = downloadedBytes
                    )
                    val initialCursor = outputFinalizer.createInitialCursor(reservation)
                    queueService.enterFinalization(
                        taskId = claim.task.taskId,
                        checkpoint = finalCheckpoint,
                        cursor = initialCursor
                    ).getOrElse { throwable ->
                        return@withContext AttemptOutcome.Failed(
                            FailureReason.OutputFailure(
                                throwable.message ?: "Finalization start could not be persisted"
                            )
                        )
                    }
                    finalizeReservedArtifact(
                        claim = claim,
                        reservation = reservation,
                        checkpoint = finalCheckpoint,
                        initialCursor = initialCursor,
                        controlHandle = controlHandle
                    )
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Exception) {
            AttemptOutcome.Failed(
                FailureReason.ProviderFailure(
                    stage = "Download",
                    message = throwable.message ?: "Transfer failed"
                )
            )
        }
    }

    @Suppress("LongMethod")
    private suspend fun finalizeReservedArtifact(
        claim: QueueClaim,
        reservation: OutputReservation,
        checkpoint: TransferCheckpoint,
        initialCursor: com.romulus.mobile.downloads.queue.FinalizationCursor?,
        controlHandle: ControlHandle
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
        ).getOrElse { throwable ->
            outputReservationService.revalidateCurrentOutputDirectory()
            return AttemptOutcome.Failed(throwable.toReservationFailure())
        }
        return when (finalization) {
            is OutputFinalizationOutcome.Completed -> AttemptOutcome.Completed(
                reservation = finalization.result.reservation,
                outputs = finalization.result.outputs
            )

            is OutputFinalizationOutcome.Stopped -> when (finalization.signal) {
                com.romulus.mobile.downloads.queue.ControlSignal.PAUSE ->
                    AttemptOutcome.Paused(checkpoint)

                com.romulus.mobile.downloads.queue.ControlSignal.CANCEL ->
                    AttemptOutcome.Cancelled(checkpoint)

                com.romulus.mobile.downloads.queue.ControlSignal.NONE ->
                    AttemptOutcome.Failed(
                        FailureReason.OutputFailure("Finalization stopped without a control signal")
                    )
            }
        }
    }

    private companion object {
        val POLL_INTERVAL: Duration = Duration.ofSeconds(1)
        val PREPARING_TIMEOUT: Duration = Duration.ofHours(24)
        val CHECKPOINT_INTERVAL: Duration = Duration.ofSeconds(3)
        const val BUFFER_SIZE = 64 * 1024
    }
}

private class QueueFinalizationControl(
    private val taskId: com.romulus.mobile.downloads.queue.TaskId,
    private val controlHandle: ControlHandle,
    private val queueService: QueueService,
    private val clock: Clock
) : FinalizationControl {
    private var lastLeaseRenewedAt = clock.instant()

    override fun currentSignal(): com.romulus.mobile.downloads.queue.ControlSignal =
        controlHandle.current()

    override fun pulse(): Result<Unit> {
        val now = clock.instant()
        return if (Duration.between(lastLeaseRenewedAt, now) >= FINALIZATION_LEASE_RENEW_INTERVAL) {
            lastLeaseRenewedAt = now
            runBlocking {
                queueService.renewClaim(taskId)
            }
        } else {
            Result.success(Unit)
        }
    }
}

private val FINALIZATION_LEASE_RENEW_INTERVAL: Duration = Duration.ofSeconds(30)

private fun Throwable.toReservationFailure(): FailureReason {
    val message = message ?: "Download directory is not usable"
    return if (
        message.contains("directory", ignoreCase = true) ||
        message.contains("uri", ignoreCase = true) ||
        message.contains("usable", ignoreCase = true)
    ) {
        FailureReason.DirectoryAccessFailure(message)
    } else {
        FailureReason.OutputFailure(message)
    }
}

private class QueueStatePersistenceException(message: String, cause: Throwable) :
    IllegalStateException(message, cause)

private class ProviderPreparationTimeoutException(message: String) : IllegalStateException(message)

private class ProviderLinkAmbiguityException(message: String) : IllegalStateException(message)

private fun Throwable.toAcquisitionException(defaultMessage: String): Exception = when (this) {
    is AuthRequiredException -> this
    is Exception -> IllegalStateException(message ?: defaultMessage, this)
    else -> IllegalStateException(message ?: defaultMessage, this)
}

private fun Throwable.toProviderAttemptFailure(stage: String): AttemptOutcome.Failed = when (this) {
    is AuthRequiredException -> AttemptOutcome.Failed(
        reason = FailureReason.AuthRequired(message ?: "Auth required"),
        retryable = false
    )

    else -> AttemptOutcome.Failed(
        reason = FailureReason.ProviderFailure(
            stage = stage,
            message = message ?: "Provider request failed"
        )
    )
}
