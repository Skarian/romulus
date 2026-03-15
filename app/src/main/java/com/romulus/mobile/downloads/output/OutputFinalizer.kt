@file:Suppress(
    "ChainMethodContinuation",
    "ClassSignature",
    "CyclomaticComplexMethod",
    "LongMethod",
    "MaxLineLength",
    "MaximumLineLength",
    "ReturnCount"
)

package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.queue.ControlSignal
import com.romulus.mobile.downloads.queue.DirectSaveStage
import com.romulus.mobile.downloads.queue.FinalizationCursor
import com.romulus.mobile.downloads.queue.PendingArchivePassCursor
import com.romulus.mobile.downloads.queue.QueueTask
import java.io.File

internal data class OutputFinalizationResult(
    val reservation: OutputReservation,
    val outputs: List<FinalOutputRecord>
)

internal sealed interface OutputFinalizationOutcome {
    data class Completed(val result: OutputFinalizationResult) : OutputFinalizationOutcome

    data class Stopped(val signal: ControlSignal) : OutputFinalizationOutcome
}

internal data class OutputFinalizationPersistence(
    val onReservationUpdated: suspend (OutputReservation) -> Result<Unit>,
    val onOutputsUpdated: suspend (List<FinalOutputRecord>) -> Result<Unit>,
    val onCursorUpdated: suspend (FinalizationCursor) -> Result<Unit>,
    val occupiedRelativePaths: suspend (String) -> Set<String> = { emptySet() }
)

internal class OutputFinalizer(
    private val reservationService: OutputReservationService,
    private val extractionController: ArchiveExtractionController,
    private val outputFilesystem: OutputFilesystem
) {
    fun createInitialCursor(
        reservation: OutputReservation,
        artifactPath: String = reservation.tempArtifactPath
    ): FinalizationCursor = createInitialFinalizationCursor(
        reservation = reservation,
        artifactPath = artifactPath
    )

    suspend fun finalize(
        task: QueueTask,
        reservation: OutputReservation,
        artifactPath: String,
        existingOutputs: List<FinalOutputRecord>,
        initialCursor: FinalizationCursor?,
        persistence: OutputFinalizationPersistence,
        control: FinalizationControl
    ): Result<OutputFinalizationOutcome> = when (reservation.artifact.handling) {
        ReservedArtifactHandling.DIRECT_SAVE ->
            finalizeDirectSave(
                reservation = reservation,
                artifactPath = artifactPath,
                existingOutputs = existingOutputs,
                initialCursor = initialCursor as? FinalizationCursor.DirectSave,
                persistence = persistence,
                control = control
            )

        ReservedArtifactHandling.LOCAL_UNARCHIVE ->
            finalizeWithUnarchive(
                task = task,
                reservation = reservation,
                artifactPath = artifactPath,
                existingOutputs = existingOutputs,
                initialCursor = initialCursor as? FinalizationCursor.Unarchive,
                persistence = persistence,
                control = control
            )
    }

    private suspend fun finalizeDirectSave(
        reservation: OutputReservation,
        artifactPath: String,
        existingOutputs: List<FinalOutputRecord>,
        initialCursor: FinalizationCursor.DirectSave?,
        persistence: OutputFinalizationPersistence,
        control: FinalizationControl
    ): Result<OutputFinalizationOutcome> {
        val directOutput = reservation.directOutput
            ?: return Result.failure(IllegalStateException("Direct output reservation is missing"))
        val persistedOutput = existingOutputs.firstOrNull { output ->
            output.finalOutputId == directOutput.finalOutputId
        }
        if (
            persistedOutput != null ||
            initialCursor?.stage == DirectSaveStage.COMPLETION_PENDING
        ) {
            val completedOutput = persistedOutput ?: FinalOutputRecord(
                finalOutputId = directOutput.finalOutputId,
                relativePath = directOutput.relativePath,
                displayName = directOutput.displayName,
                sizeBytes = null
            )
            return Result.success(
                OutputFinalizationOutcome.Completed(
                    OutputFinalizationResult(
                        reservation = reservation,
                        outputs = listOf(completedOutput)
                    )
                )
            )
        }

        if (initialCursor?.stage != DirectSaveStage.PROMOTING) {
            persistence.onCursorUpdated(
                FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING)
            ).getOrElse { throwable ->
                return Result.failure(throwable)
            }
        }
        outputFilesystem.deleteFinalOutput(
            outputDirectoryUri = reservation.boundOutputDirectoryUri,
            relativePath = directOutput.relativePath
        ).getOrElse { throwable ->
            return Result.failure(throwable)
        }
        val writeResult = outputFilesystem.writeArtifactToFinalOutput(
            artifactPath = artifactPath,
            outputDirectoryUri = reservation.boundOutputDirectoryUri,
            relativePath = directOutput.relativePath,
            control = control
        )
        if (writeResult.isFailure) {
            return interruptionOrFailure(checkNotNull(writeResult.exceptionOrNull()))
        }
        val sizeBytes = writeResult.getOrThrow()
        val output = FinalOutputRecord(
            finalOutputId = directOutput.finalOutputId,
            relativePath = directOutput.relativePath,
            displayName = directOutput.displayName,
            sizeBytes = sizeBytes
        )
        persistence.onOutputsUpdated(listOf(output)).getOrElse { throwable ->
            deletePromotedOutput(
                outputDirectoryUri = reservation.boundOutputDirectoryUri,
                output = output
            ).getOrElse { cleanupThrowable ->
                return Result.failure(
                    IllegalStateException(
                        "Promoted output could not be rolled back after persistence failed",
                        cleanupThrowable
                    )
                )
            }
            return Result.failure(throwable)
        }
        persistence.onCursorUpdated(
            FinalizationCursor.DirectSave(DirectSaveStage.COMPLETION_PENDING)
        ).getOrElse { throwable ->
            return Result.failure(throwable)
        }
        File(artifactPath).delete()
        return Result.success(
            OutputFinalizationOutcome.Completed(
                OutputFinalizationResult(
                    reservation = reservation,
                    outputs = listOf(output)
                )
            )
        )
    }

    private suspend fun finalizeWithUnarchive(
        task: QueueTask,
        reservation: OutputReservation,
        artifactPath: String,
        existingOutputs: List<FinalOutputRecord>,
        initialCursor: FinalizationCursor.Unarchive?,
        persistence: OutputFinalizationPersistence,
        control: FinalizationControl
    ): Result<OutputFinalizationOutcome> {
        var currentReservation = reservation
        val accumulatedOutputs = existingOutputs.toMutableList()
        var cursor = initialCursor ?: createInitialCursor(
            reservation = reservation,
            artifactPath = artifactPath
        ) as FinalizationCursor.Unarchive
        if (cursor.completionPending) {
            return Result.success(
                OutputFinalizationOutcome.Completed(
                    OutputFinalizationResult(
                        reservation = currentReservation,
                        outputs = accumulatedOutputs.toList()
                    )
                )
            )
        }

        while (cursor.pendingPasses.isNotEmpty()) {
            val pendingPass = cursor.pendingPasses.first()
            if (cursor.activePassReservedEntries.isEmpty()) {
                val manifest = extractionController.inspectManifest(
                    archiveFile = File(pendingPass.archiveFilePath),
                    recursive = task.unarchiveIntent.recursive,
                    lineage = pendingPass.lineage
                ).getOrElse { throwable ->
                    val message = throwable.message ?: "Manifest inspection failed"
                    return Result.failure(
                        IllegalStateException(
                            "Archive extraction failed on pass ${pendingPass.passIndex}: $message",
                            throwable
                        )
                    )
                }
                if (manifest.finalEntryPaths.isNotEmpty()) {
                    currentReservation = reservationService.expandExtractionPlan(
                        task = task,
                        reservation = currentReservation,
                        extractedEntries = manifest.finalEntryPaths,
                        occupiedRelativePaths = persistence.occupiedRelativePaths
                    ).getOrElse { throwable ->
                        return Result.failure(throwable)
                    }
                    persistence.onReservationUpdated(currentReservation).getOrElse { throwable ->
                        return Result.failure(throwable)
                    }
                }
                cursor = cursor.copy(
                    activePassReservedEntries = manifest.finalEntryPaths.map(
                        ExtractionManifestEntry::archiveEntryPath
                    ),
                    currentPromotionOutputId = null
                )
                persistence.onCursorUpdated(cursor).getOrElse { throwable ->
                    return Result.failure(throwable)
                }
            }

            val passReservedOutputs = currentReservation.extractionPlan.filter { output ->
                output.archiveEntryPath in cursor.activePassReservedEntries
            }
            cleanupCurrentPassArtifacts(
                reservation = currentReservation,
                pendingPass = pendingPass,
                reservedOutputs = passReservedOutputs
            )

            val extractionResult = extractionController.extract(
                archiveFile = File(pendingPass.archiveFilePath),
                extractionRoot = File(currentReservation.extractionRootPath),
                recursive = task.unarchiveIntent.recursive,
                lineage = pendingPass.lineage,
                passIndex = pendingPass.passIndex,
                reservedOutputs = passReservedOutputs,
                control = control
            )
            if (extractionResult.isFailure) {
                return interruptionOrFailure(checkNotNull(extractionResult.exceptionOrNull()))
            }
            val passExecution = extractionResult.getOrThrow()
            passExecution.finalArtifacts.forEach { artifact ->
                if (accumulatedOutputs.any { output ->
                        output.finalOutputId == artifact.reservedOutput.finalOutputId
                    }
                ) {
                    File(artifact.localArtifactPath).delete()
                    return@forEach
                }
                cursor = cursor.copy(
                    currentPromotionOutputId = artifact.reservedOutput.finalOutputId
                )
                persistence.onCursorUpdated(cursor).getOrElse { throwable ->
                    return Result.failure(throwable)
                }
                outputFilesystem.deleteFinalOutput(
                    outputDirectoryUri = currentReservation.boundOutputDirectoryUri,
                    relativePath = artifact.reservedOutput.relativePath
                ).getOrElse { throwable ->
                    return Result.failure(throwable)
                }
                val writeResult = outputFilesystem.writeArtifactToFinalOutput(
                    artifactPath = artifact.localArtifactPath,
                    outputDirectoryUri = currentReservation.boundOutputDirectoryUri,
                    relativePath = artifact.reservedOutput.relativePath,
                    control = control
                )
                if (writeResult.isFailure) {
                    return interruptionOrFailure(checkNotNull(writeResult.exceptionOrNull()))
                }
                val sizeBytes = writeResult.getOrThrow()
                val promotedOutput = FinalOutputRecord(
                    finalOutputId = artifact.reservedOutput.finalOutputId,
                    relativePath = artifact.reservedOutput.relativePath,
                    displayName = artifact.reservedOutput.displayName,
                    sizeBytes = sizeBytes
                )
                accumulatedOutputs += promotedOutput
                persistence.onOutputsUpdated(accumulatedOutputs.toList()).getOrElse { throwable ->
                    deletePromotedOutput(
                        outputDirectoryUri = currentReservation.boundOutputDirectoryUri,
                        output = promotedOutput
                    ).getOrElse { cleanupThrowable ->
                        return Result.failure(
                            IllegalStateException(
                                "Promoted output could not be rolled back after persistence failed",
                                cleanupThrowable
                            )
                        )
                    }
                    accumulatedOutputs.remove(promotedOutput)
                    return Result.failure(throwable)
                }
                File(artifact.localArtifactPath).delete()
                cursor = cursor.copy(currentPromotionOutputId = null)
                persistence.onCursorUpdated(cursor).getOrElse { throwable ->
                    return Result.failure(throwable)
                }
            }
            if (passExecution.failureMessage != null) {
                return Result.failure(
                    IllegalStateException(
                        "Archive extraction failed on pass ${pendingPass.passIndex}: ${passExecution.failureMessage}"
                    )
                )
            }

            val nextPendingPasses = cursor.pendingPasses.drop(1) +
                passExecution.recursiveArchives.map { archive ->
                    PendingArchivePassCursor(
                        archiveFilePath = archive.localArtifactPath,
                        lineage = archive.lineage,
                        passIndex = pendingPass.passIndex + 1
                    )
                }
            cursor = cursor.copy(
                pendingPasses = nextPendingPasses,
                activePassReservedEntries = emptyList(),
                currentPromotionOutputId = null,
                completionPending = nextPendingPasses.isEmpty()
            )
            persistence.onCursorUpdated(cursor).getOrElse { throwable ->
                return Result.failure(throwable)
            }
            File(pendingPass.archiveFilePath).delete()
            if (cursor.completionPending) {
                File(currentReservation.extractionRootPath).deleteRecursively()
                return Result.success(
                    OutputFinalizationOutcome.Completed(
                        OutputFinalizationResult(
                            reservation = currentReservation,
                            outputs = accumulatedOutputs.toList()
                        )
                    )
                )
            }
        }

        cursor = cursor.copy(completionPending = true)
        persistence.onCursorUpdated(cursor).getOrElse { throwable ->
            return Result.failure(throwable)
        }
        File(currentReservation.extractionRootPath).deleteRecursively()
        return Result.success(
            OutputFinalizationOutcome.Completed(
                OutputFinalizationResult(
                    reservation = currentReservation,
                    outputs = accumulatedOutputs.toList()
                )
            )
        )
    }

    private suspend fun deletePromotedOutput(
        outputDirectoryUri: String,
        output: FinalOutputRecord
    ): Result<Unit> = outputFilesystem.deleteFinalOutput(
        outputDirectoryUri = outputDirectoryUri,
        relativePath = output.relativePath
    )

    private fun cleanupCurrentPassArtifacts(
        reservation: OutputReservation,
        pendingPass: PendingArchivePassCursor,
        reservedOutputs: List<ReservedExtractionOutput>
    ) {
        reservedOutputs.forEach { reservedOutput ->
            File(resolveFinalArtifactPath(reservation, reservedOutput)).delete()
        }
        extractionController.resolveRecursiveRoot(
            extractionRoot = File(reservation.extractionRootPath),
            passIndex = pendingPass.passIndex,
            lineage = pendingPass.lineage
        ).deleteRecursively()
    }

    private fun resolveFinalArtifactPath(
        reservation: OutputReservation,
        reservedOutput: ReservedExtractionOutput
    ): String {
        val extension = reservedOutput.displayName.substringAfterLast('.', "bin")
        return File(reservation.extractionRootPath)
            .resolve("final/${reservedOutput.finalOutputId.value}.$extension")
            .absolutePath
    }

    private fun interruptionOrFailure(throwable: Throwable): Result<OutputFinalizationOutcome> =
        when (throwable) {
            is FinalizationInterruptedException -> Result.success(
                OutputFinalizationOutcome.Stopped(throwable.signal)
            )

            else -> Result.failure(throwable)
        }
}
