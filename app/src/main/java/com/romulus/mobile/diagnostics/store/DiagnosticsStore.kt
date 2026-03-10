package com.romulus.mobile.diagnostics.store

import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.events.DiagnosticsManifest
import com.romulus.mobile.diagnostics.events.DiagnosticsSummary
import java.nio.file.Path
import java.time.Instant

internal data class DiagnosticsArtifactSnapshot(
    val manifest: Path,
    val timeline: Path,
    val failures: Path,
    val summary: Path,
    val totalBytes: Long
)

internal data class StagedInternalClear(val backupRoot: Path)

internal data class DiagnosticsExportBundle(val bundlePath: Path, val createdAt: Instant)

internal interface DiagnosticsFilesystem

@Suppress("RedundantSuspendModifier", "UnusedParameter", "UnusedPrivateProperty")
internal class DiagnosticsStore(private val filesystem: DiagnosticsFilesystem) {
    suspend fun appendTimeline(event: DiagnosticEvent): Result<Unit> = unwiredFailure()

    suspend fun appendFailure(event: DiagnosticEvent): Result<Unit> = unwiredFailure()

    suspend fun writeManifest(manifest: DiagnosticsManifest): Result<Unit> = unwiredFailure()

    suspend fun writeSummary(summary: DiagnosticsSummary): Result<Unit> = unwiredFailure()

    suspend fun snapshot(): Result<DiagnosticsArtifactSnapshot> = Result.failure(
        UnsupportedOperationException("DiagnosticsStore is not wired yet")
    )

    suspend fun stageInternalClear(): Result<StagedInternalClear> = Result.failure(
        UnsupportedOperationException("DiagnosticsStore is not wired yet")
    )

    suspend fun commitInternalClear(staged: StagedInternalClear): Result<Unit> = unwiredFailure()

    suspend fun rollbackInternalClear(staged: StagedInternalClear): Result<Unit> = unwiredFailure()

    private fun unwiredFailure(): Result<Unit> = Result.failure(
        UnsupportedOperationException("DiagnosticsStore is not wired yet")
    )
}
