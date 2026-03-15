@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.output

import com.romulus.mobile.source.snapshot.ExtractionLayoutPolicy
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ReservationId(val value: String)

@Serializable
@JvmInline
value class FinalOutputId(val value: String)

@Serializable
@JvmInline
value class TempFileToken(val value: String)

@Serializable
data class ReservedDirectOutput(
    val finalOutputId: FinalOutputId,
    val relativePath: String,
    val displayName: String
)

@Serializable
data class ReservedExtractionOutput(
    val finalOutputId: FinalOutputId,
    val archiveEntryPath: String,
    val relativePath: String,
    val displayName: String
)

data class ExtractionManifestEntry(
    val archiveEntryPath: String,
    val renameEligible: Boolean
)

@Serializable
enum class ReservedArtifactHandling {
    DIRECT_SAVE,
    LOCAL_UNARCHIVE
}

@Serializable
data class ReservedArtifact(
    val originalDisplayName: String,
    val tempArtifactPath: String,
    val extractionRootPath: String,
    val handling: ReservedArtifactHandling,
    val extractionLayout: ExtractionLayoutPolicy? = null,
    val resolvedExtractionDirectory: String? = null
)

@Serializable
data class OutputReservation(
    val reservationId: ReservationId,
    val boundOutputDirectoryUri: String,
    val artifact: ReservedArtifact,
    val directOutput: ReservedDirectOutput?,
    val extractionPlan: List<ReservedExtractionOutput>
) {
    constructor(
        reservationId: ReservationId,
        boundOutputDirectoryUri: String,
        tempArtifactPath: String,
        extractionRootPath: String,
        directOutput: ReservedDirectOutput?,
        extractionPlan: List<ReservedExtractionOutput>,
        extractionLayout: ExtractionLayoutPolicy? = null,
        resolvedExtractionDirectory: String? = null
    ) : this(
        reservationId = reservationId,
        boundOutputDirectoryUri = boundOutputDirectoryUri,
        artifact = ReservedArtifact(
            originalDisplayName = directOutput?.displayName
                ?: extractionPlan.firstOrNull()?.displayName
                ?: tempArtifactPath.substringAfterLast('/'),
            tempArtifactPath = tempArtifactPath,
            extractionRootPath = extractionRootPath,
            handling = if (directOutput != null) {
                ReservedArtifactHandling.DIRECT_SAVE
            } else {
                ReservedArtifactHandling.LOCAL_UNARCHIVE
            },
            extractionLayout = extractionLayout,
            resolvedExtractionDirectory = resolvedExtractionDirectory
        ),
        directOutput = directOutput,
        extractionPlan = extractionPlan
    )

    val tempArtifactPath: String
        get() = artifact.tempArtifactPath

    val extractionRootPath: String
        get() = artifact.extractionRootPath
}

@Serializable
data class FinalOutputRecord(
    val finalOutputId: FinalOutputId,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long?
)
