@file:Suppress(
    "ChainMethodContinuation",
    "ClassSignature",
    "RedundantSuspendModifier",
    "ReturnCount",
    "TooManyFunctions"
)

package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.queue.FinalizationCursor
import com.romulus.mobile.downloads.queue.QueueTask
import com.romulus.mobile.downloads.queue.TransferCheckpoint
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.ExtractionLayoutMode
import java.io.File
import java.util.UUID

internal sealed interface ArtifactRecoveryDisposition {
    data object CannotResume : ArtifactRecoveryDisposition

    data class ResumeTransfer(
        val reservation: OutputReservation,
        val safeResumeOffset: Long
    ) : ArtifactRecoveryDisposition

    data class ResumeFinalization(
        val reservation: OutputReservation,
        val cursor: FinalizationCursor
    ) : ArtifactRecoveryDisposition
}

internal class OutputReservationService(
    private val outputFilesystem: OutputFilesystem,
    private val outputRootResolver: OutputRootResolver,
    private val artifactRoot: File
) {
    suspend fun resolveFreshBinding(): Result<OutputRootBinding> =
        outputRootResolver.resolveFreshBinding()

    suspend fun listRelativePaths(
        outputDirectoryUri: String,
        subfolder: String
    ): Result<Set<String>> = outputFilesystem.listRelativePaths(
        outputDirectoryUri = outputDirectoryUri,
        subfolder = subfolder
    )

    suspend fun reserve(
        task: QueueTask,
        occupiedRelativePathsProvider: suspend (String) -> Set<String> = { emptySet() }
    ): Result<OutputReservation> {
        val binding = resolveFreshBinding().getOrElse { throwable ->
            return Result.failure(throwable)
        }
        val existingRelativePaths = listRelativePaths(
            outputDirectoryUri = binding.outputDirectoryUri,
            subfolder = task.storageTarget.subfolder
        ).getOrElse { throwable ->
            return Result.failure(throwable)
        } + occupiedRelativePathsProvider(binding.outputDirectoryUri)
        return Result.success(
            createReservation(
                task = task,
                binding = binding,
                occupiedRelativePaths = existingRelativePaths
            )
        )
    }

    fun createReservation(
        task: QueueTask,
        binding: OutputRootBinding,
        occupiedRelativePaths: Set<String>
    ): OutputReservation {
        val reservationId = ReservationId(UUID.randomUUID().toString())
        val tempArtifact = artifactRoot.resolve(
            "artifacts/${buildTempArtifactFileName(task.originalDisplayName, reservationId.value)}"
        )
        val extractionRoot = artifactRoot.resolve("extract/${reservationId.value}")
        val handling = task.reservedArtifactHandling()
        tempArtifact.parentFile?.mkdirs()
        extractionRoot.mkdirs()
        return OutputReservation(
            reservationId = reservationId,
            boundOutputDirectoryUri = binding.outputDirectoryUri,
            artifact = ReservedArtifact(
                originalDisplayName = task.originalDisplayName,
                tempArtifactPath = tempArtifact.absolutePath,
                extractionRootPath = extractionRoot.absolutePath,
                handling = handling,
                extractionLayout = task.unarchiveIntent.layout.takeIf {
                    handling == ReservedArtifactHandling.LOCAL_UNARCHIVE
                },
                resolvedExtractionDirectory = task.resolveExtractionDirectory(
                    occupiedRelativePaths = occupiedRelativePaths,
                    handling = handling
                )
            ),
            directOutput = if (handling == ReservedArtifactHandling.DIRECT_SAVE) {
                val preferredDisplayName = task.preferredOutputName()
                val relativePath = resolveRelativePath(
                    baseRelativeDirectory = task.storageTarget.subfolder,
                    preferredFileName = preferredDisplayName,
                    occupiedRelativePaths = occupiedRelativePaths
                )
                ReservedDirectOutput(
                    finalOutputId = FinalOutputId(UUID.randomUUID().toString()),
                    relativePath = relativePath,
                    displayName = relativePath.substringAfterLast('/')
                )
            } else {
                null
            },
            extractionPlan = emptyList()
        )
    }

    suspend fun expandExtractionPlan(
        task: QueueTask,
        reservation: OutputReservation,
        extractedEntries: List<ExtractionManifestEntry>,
        occupiedRelativePaths: suspend (String) -> Set<String> = { emptySet() }
    ): Result<OutputReservation> {
        val occupiedPaths = outputFilesystem.listRelativePaths(
            outputDirectoryUri = reservation.boundOutputDirectoryUri,
            subfolder = task.storageTarget.subfolder
        ).getOrElse { throwable ->
            return Result.failure(throwable)
        }.toMutableSet().apply {
            addAll(occupiedRelativePaths(reservation.boundOutputDirectoryUri))
            reservation.directOutput?.let { directOutput -> add(directOutput.relativePath) }
            addAll(reservation.extractionPlan.map(ReservedExtractionOutput::relativePath))
        }
        val existingByArchiveEntryPath = reservation.extractionPlan.associateBy(
            ReservedExtractionOutput::archiveEntryPath
        )
        val reserved = extractedEntries.mapNotNull { entryPath ->
            existingByArchiveEntryPath[entryPath.archiveEntryPath] ?: run {
                val preferredFileName = task.preferredExtractedOutputName(
                    entryPath = entryPath.archiveEntryPath,
                    renameEligible = entryPath.renameEligible
                )
                val relativePath = resolveRelativePath(
                    baseRelativeDirectory = reservation.resolvedExtractionDirectory(),
                    preferredFileName = preferredFileName,
                    occupiedRelativePaths = occupiedPaths
                )
                occupiedPaths += relativePath
                ReservedExtractionOutput(
                    finalOutputId = FinalOutputId(UUID.randomUUID().toString()),
                    archiveEntryPath = entryPath.archiveEntryPath,
                    relativePath = relativePath,
                    displayName = relativePath.substringAfterLast('/')
                )
            }
        }
        return Result.success(
            reservation.copy(
                extractionPlan = reservation.extractionPlan + reserved
            )
        )
    }

    suspend fun revalidateCurrentOutputDirectory() =
        outputRootResolver.revalidateCurrentOutputDirectory()

    fun inspectRecoveryDisposition(
        reservation: OutputReservation?,
        checkpoint: TransferCheckpoint?,
        persistedCursor: FinalizationCursor?
    ): ArtifactRecoveryDisposition {
        if (reservation == null || checkpoint == null) {
            return ArtifactRecoveryDisposition.CannotResume
        }
        if (persistedCursor != null) {
            return ArtifactRecoveryDisposition.ResumeFinalization(
                reservation = reservation,
                cursor = persistedCursor
            )
        }
        val tempArtifact = File(reservation.tempArtifactPath)
        if (!tempArtifact.exists()) {
            return ArtifactRecoveryDisposition.CannotResume
        }
        val artifactLength = tempArtifact.length()
        if (artifactLength < checkpoint.resumeByteOffset) {
            return ArtifactRecoveryDisposition.CannotResume
        }
        if (checkpoint.totalBytes != null && artifactLength == checkpoint.totalBytes) {
            return ArtifactRecoveryDisposition.ResumeFinalization(
                reservation = reservation,
                cursor = createInitialFinalizationCursor(reservation)
            )
        }
        return ArtifactRecoveryDisposition.ResumeTransfer(
            reservation = reservation,
            safeResumeOffset = artifactLength
        )
    }

    private fun resolveRelativePath(
        baseRelativeDirectory: String,
        preferredFileName: String,
        occupiedRelativePaths: Set<String>
    ): String {
        val relativeDirectory = baseRelativeDirectory.trim().trim('/')
        val baseRelativePath = listOf(relativeDirectory, preferredFileName)
            .filter(String::isNotBlank)
            .joinToString("/")
        if (baseRelativePath !in occupiedRelativePaths) {
            return baseRelativePath
        }

        val extension = preferredFileName.substringAfterLast('.', "")
        val stem = if (extension.isBlank()) {
            preferredFileName
        } else {
            preferredFileName.removeSuffix(".$extension")
        }
        var collisionIndex = 1
        while (true) {
            val candidateFileName = if (extension.isBlank()) {
                "$stem ($collisionIndex)"
            } else {
                "$stem ($collisionIndex).$extension"
            }
            val candidateRelativePath = listOf(relativeDirectory, candidateFileName)
                .filter(String::isNotBlank)
                .joinToString("/")
            if (candidateRelativePath !in occupiedRelativePaths) {
                return candidateRelativePath
            }
            collisionIndex += 1
        }
    }

    private fun QueueTask.preferredOutputName(): String {
        val renameRule = namingIntent.renameRule
        if (namingIntent.applyRename && renameRule != null) {
            return runCatching {
                Regex(renameRule.pattern).replace(originalDisplayName, renameRule.replacement)
            }.getOrDefault(originalDisplayName)
        }
        return originalDisplayName
    }

    private fun QueueTask.preferredExtractedOutputName(
        entryPath: String,
        renameEligible: Boolean
    ): String {
        val originalName = entryPath.substringAfterLast('/').ifBlank { "entry" }
        val renameRule = namingIntent.renameRule
        if (renameEligible && namingIntent.applyRename && renameRule != null) {
            return runCatching {
                Regex(renameRule.pattern).replace(originalName, renameRule.replacement)
            }.getOrDefault(originalName)
        }
        return originalName
    }

    private fun QueueTask.reservedArtifactHandling(): ReservedArtifactHandling =
        if (unarchiveIntent.enabled && outputFilesystem.isSupportedArchive(originalDisplayName)) {
            ReservedArtifactHandling.LOCAL_UNARCHIVE
        } else {
            ReservedArtifactHandling.DIRECT_SAVE
        }

    private fun QueueTask.resolveExtractionDirectory(
        occupiedRelativePaths: Set<String>,
        handling: ReservedArtifactHandling
    ): String? {
        if (handling != ReservedArtifactHandling.LOCAL_UNARCHIVE) {
            return null
        }
        val baseSubfolder = storageTarget.subfolder.trim().trim('/')
        return when (unarchiveIntent.layout.mode) {
            ExtractionLayoutMode.FLAT -> baseSubfolder
            ExtractionLayoutMode.DEDICATED_FOLDER -> resolveRelativeDirectory(
                subfolder = baseSubfolder,
                preferredDirectoryName = preferredExtractionDirectoryName(),
                occupiedRelativePaths = occupiedRelativePaths
            )
        }
    }

    private fun QueueTask.preferredExtractionDirectoryName(): String {
        val archiveStem = originalDisplayName
            .substringBeforeLast('.', originalDisplayName)
            .ifBlank { "archive" }
        val renameRule = unarchiveIntent.layout.folderRenameRule
        return if (renameRule != null) {
            renameWithRule(
                input = archiveStem,
                renameRule = renameRule
            ).ifBlank { archiveStem }
        } else {
            archiveStem
        }
    }

    private fun resolveRelativeDirectory(
        subfolder: String,
        preferredDirectoryName: String,
        occupiedRelativePaths: Set<String>
    ): String {
        val relativeDirectory = subfolder.trim().trim('/')
        val baseRelativePath = listOf(relativeDirectory, preferredDirectoryName)
            .filter(String::isNotBlank)
            .joinToString("/")
        if (!baseRelativePath.conflictsWithOccupiedPaths(occupiedRelativePaths)) {
            return baseRelativePath
        }
        var collisionIndex = 1
        while (true) {
            val candidateRelativePath = listOf(
                relativeDirectory,
                "$preferredDirectoryName ($collisionIndex)"
            ).filter(String::isNotBlank).joinToString("/")
            if (!candidateRelativePath.conflictsWithOccupiedPaths(occupiedRelativePaths)) {
                return candidateRelativePath
            }
            collisionIndex += 1
        }
    }

    private fun renameWithRule(input: String, renameRule: RenameRule): String = runCatching {
        Regex(renameRule.pattern).replace(input, renameRule.replacement)
    }.getOrDefault(input)

    private fun String.conflictsWithOccupiedPaths(occupiedRelativePaths: Set<String>): Boolean {
        if (this in occupiedRelativePaths) {
            return true
        }
        val prefix = "$this/"
        return occupiedRelativePaths.any { path -> path.startsWith(prefix) }
    }

    private fun OutputReservation.resolvedExtractionDirectory(): String =
        artifact.resolvedExtractionDirectory.orEmpty()
}

private fun buildTempArtifactFileName(originalDisplayName: String, reservationId: String): String {
    val extension = originalDisplayName.substringAfterLast('.', "")
        .lowercase()
        .filter(Char::isLetterOrDigit)
    return if (extension.isBlank()) {
        "$reservationId.part"
    } else {
        "$reservationId.part.$extension"
    }
}
