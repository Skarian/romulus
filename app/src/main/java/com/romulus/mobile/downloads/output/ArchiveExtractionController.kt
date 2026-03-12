@file:Suppress(
    "ArgumentListWrapping",
    "ChainMethodContinuation",
    "ClassSignature",
    "FunctionSignature",
    "LongMethod",
    "MaxLineLength",
    "MaximumLineLength",
    "ReturnCount"
)

package com.romulus.mobile.downloads.output

import java.io.File

internal data class ArchiveManifest(
    val finalEntryPaths: List<ExtractionManifestEntry>
)

internal data class ExtractedFinalArtifact(
    val reservedOutput: ReservedExtractionOutput,
    val localArtifactPath: String,
    val sizeBytes: Long
)

internal data class ExtractedRecursiveArchive(
    val lineage: String,
    val localArtifactPath: String
)

internal data class ArchivePassExecution(
    val finalArtifacts: List<ExtractedFinalArtifact>,
    val recursiveArchives: List<ExtractedRecursiveArchive>,
    val failureMessage: String?
)

internal class ArchiveExtractionController(
    private val archiveRuntime: ArchiveRuntime,
    private val outputFilesystem: OutputFilesystem
) {
    suspend fun inspectManifest(
        archiveFile: File,
        recursive: Boolean,
        lineage: String
    ): Result<ArchiveManifest> = planPass(
        archiveFile = archiveFile,
        recursive = recursive,
        lineage = lineage
    ).map { plan ->
        ArchiveManifest(
            finalEntryPaths = plan.finalEntries.map { entry ->
                ExtractionManifestEntry(
                    archiveEntryPath = entry.scopedEntryPath,
                    renameEligible = entry.renameEligible
                )
            }
        )
    }

    suspend fun extract(
        archiveFile: File,
        extractionRoot: File,
        recursive: Boolean,
        lineage: String,
        passIndex: Int,
        reservedOutputs: List<ReservedExtractionOutput>,
        control: FinalizationControl? = null
    ): Result<ArchivePassExecution> {
        val passPlan = planPass(
            archiveFile = archiveFile,
            recursive = recursive,
            lineage = lineage
        ).getOrElse { throwable ->
            return Result.failure(throwable)
        }
        val reservedByEntryPath = reservedOutputs.associateBy(ReservedExtractionOutput::archiveEntryPath)
        val targetFiles = mutableMapOf<String, File>()
        passPlan.finalEntries.forEach { entry ->
            val reservedOutput = reservedByEntryPath[entry.scopedEntryPath]
                ?: return Result.failure(
                    IllegalStateException("Missing extraction reservation for ${entry.scopedEntryPath}")
                )
            targetFiles[entry.rawEntryPath] = extractionRoot.resolve(
                buildFinalTempFileName(reservedOutput)
            )
        }
        val recursiveRoot = resolveRecursiveRoot(
            extractionRoot = extractionRoot,
            passIndex = passIndex,
            lineage = lineage
        )
        passPlan.recursiveArchives.forEach { entry ->
            targetFiles[entry.rawEntryPath] = resolveExtractionTarget(
                parent = recursiveRoot,
                preferredFileName = entry.displayName
            )
        }
        val runtimePass = archiveRuntime.extract(
            archiveFile = archiveFile,
            targetFiles = targetFiles,
            control = control
        ).getOrElse { throwable ->
            return Result.failure(throwable)
        }
        val finalArtifacts = runtimePass.outputs.mapNotNull { output ->
            val finalEntry = passPlan.finalEntries.firstOrNull { entry ->
                entry.rawEntryPath == output.rawEntryPath
            } ?: return@mapNotNull null
            val reservedOutput = checkNotNull(reservedByEntryPath[finalEntry.scopedEntryPath])
            ExtractedFinalArtifact(
                reservedOutput = reservedOutput,
                localArtifactPath = output.localArtifactPath,
                sizeBytes = output.sizeBytes
            )
        }
        val recursiveArchives = runtimePass.outputs.mapNotNull { output ->
            val recursiveEntry = passPlan.recursiveArchives.firstOrNull { entry ->
                entry.rawEntryPath == output.rawEntryPath
            } ?: return@mapNotNull null
            ExtractedRecursiveArchive(
                lineage = recursiveEntry.scopedEntryPath,
                localArtifactPath = output.localArtifactPath
            )
        }.filter { extracted ->
            outputFilesystem.isSupportedArchive(extracted.localArtifactPath)
        }
        return Result.success(
            ArchivePassExecution(
                finalArtifacts = finalArtifacts,
                recursiveArchives = recursiveArchives,
                failureMessage = runtimePass.failureMessage
            )
        )
    }

    private suspend fun planPass(
        archiveFile: File,
        recursive: Boolean,
        lineage: String
    ): Result<ArchivePassPlan> {
        val entries = archiveRuntime.inspect(archiveFile).getOrElse { throwable ->
            return Result.failure(throwable)
        }
        val finalEntries = entries.filter { entry ->
            !recursive || !entry.isArchiveCandidate
        }.map { entry ->
            PlannedArchiveEntry(
                rawEntryPath = entry.rawEntryPath,
                scopedEntryPath = scopedEntryPath(
                    lineage = lineage,
                    rawEntryPath = entry.rawEntryPath
                ),
                displayName = entry.rawEntryPath.substringAfterLast('/').ifBlank { "entry" },
                renameEligible = !entry.isArchiveCandidate
            )
        }
        val recursiveArchives = if (recursive) {
            entries.filter(ArchiveRuntimeEntry::isArchiveCandidate).map { entry ->
                PlannedArchiveEntry(
                    rawEntryPath = entry.rawEntryPath,
                    scopedEntryPath = scopedEntryPath(
                        lineage = lineage,
                        rawEntryPath = entry.rawEntryPath
                    ),
                    displayName = entry.rawEntryPath.substringAfterLast('/').ifBlank { "entry" },
                    renameEligible = false
                )
            }
        } else {
            emptyList()
        }
        return Result.success(
            ArchivePassPlan(
                finalEntries = finalEntries,
                recursiveArchives = recursiveArchives
            )
        )
    }

    private fun scopedEntryPath(lineage: String, rawEntryPath: String): String =
        if (lineage.isBlank()) {
            rawEntryPath
        } else {
            "$lineage!/$rawEntryPath"
        }

    private fun resolveExtractionTarget(parent: File, preferredFileName: String): File {
        parent.mkdirs()
        val extension = preferredFileName.substringAfterLast('.', "")
        val stem = if (extension.isBlank()) {
            preferredFileName
        } else {
            preferredFileName.removeSuffix(".$extension")
        }
        var collisionIndex = 0
        while (true) {
            val candidateName = when {
                collisionIndex == 0 -> preferredFileName
                extension.isBlank() -> "$stem ($collisionIndex)"
                else -> "$stem ($collisionIndex).$extension"
            }
            val candidate = parent.resolve(candidateName)
            if (!candidate.exists()) {
                return candidate
            }
            collisionIndex += 1
        }
    }

    fun resolveRecursiveRoot(extractionRoot: File, passIndex: Int, lineage: String): File =
        extractionRoot.resolve(
            "recursive/pass-$passIndex-${lineage.ifBlank { "root" }.stablePathToken()}"
        )

    private fun buildFinalTempFileName(reservedOutput: ReservedExtractionOutput): String {
        val extension = reservedOutput.displayName.substringAfterLast('.', "bin")
        return "final/${reservedOutput.finalOutputId.value}.$extension"
    }

    private data class ArchivePassPlan(
        val finalEntries: List<PlannedArchiveEntry>,
        val recursiveArchives: List<PlannedArchiveEntry>
    )

    private data class PlannedArchiveEntry(
        val rawEntryPath: String,
        val scopedEntryPath: String,
        val displayName: String,
        val renameEligible: Boolean
    )
}

private fun String.stablePathToken(): String = buildString(length) {
    for (character in this@stablePathToken) {
        append(
            when {
                character.isLetterOrDigit() -> character
                else -> '_'
            }
        )
    }
}.ifBlank { "root" }
