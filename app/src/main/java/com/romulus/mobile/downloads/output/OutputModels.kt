package com.romulus.mobile.downloads.output

import java.nio.file.Path

@JvmInline
value class ReservationId(val value: String)

@JvmInline
value class FinalOutputId(val value: String)

@JvmInline
value class TempFileToken(val value: String)

data class ReservedDirectOutput(
    val finalOutputId: FinalOutputId,
    val finalPath: Path,
    val displayName: String
)

data class ReservedExtractionOutput(
    val finalOutputId: FinalOutputId,
    val archiveEntryPath: String,
    val finalPath: Path,
    val displayName: String
)

data class OutputReservation(
    val reservationId: ReservationId,
    val boundOutputDirectoryUri: String,
    val tempArtifact: Path,
    val extractionRoot: Path,
    val directOutput: ReservedDirectOutput?,
    val extractionPlan: List<ReservedExtractionOutput>
)

data class FinalOutputRecord(
    val finalOutputId: FinalOutputId,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long?
)
