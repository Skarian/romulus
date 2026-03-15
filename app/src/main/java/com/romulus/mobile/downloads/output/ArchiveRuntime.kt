@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.output

import java.io.File

internal data class ArchiveRuntimeEntry(
    val rawEntryPath: String,
    val isArchiveCandidate: Boolean
)

internal data class ArchiveRuntimeExtractedArtifact(
    val rawEntryPath: String,
    val localArtifactPath: String,
    val sizeBytes: Long,
    val isArchiveCandidate: Boolean
)

internal data class ArchiveRuntimePassResult(
    val outputs: List<ArchiveRuntimeExtractedArtifact>,
    val failureMessage: String?
)

internal interface ArchiveRuntime {
    suspend fun inspect(archiveFile: File): Result<List<ArchiveRuntimeEntry>>

    suspend fun extract(
        archiveFile: File,
        targetFiles: Map<String, File>,
        control: FinalizationControl? = null
    ): Result<ArchiveRuntimePassResult>
}
