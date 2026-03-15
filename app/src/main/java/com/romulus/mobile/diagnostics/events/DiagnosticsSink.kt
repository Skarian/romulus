package com.romulus.mobile.diagnostics.events

import com.romulus.mobile.diagnostics.DiagnosticsEnvironment
import com.romulus.mobile.diagnostics.settings.DiagnosticsSettingsService
import com.romulus.mobile.diagnostics.store.DiagnosticsStore
import com.romulus.mobile.diagnostics.store.RetentionRotator
import com.romulus.mobile.diagnostics.store.SummaryProjector
import java.io.IOException
import kotlinx.serialization.SerializationException

internal class DiagnosticsSink(
    private val settingsService: DiagnosticsSettingsService,
    private val diagnosticsStore: DiagnosticsStore,
    private val summaryProjector: SummaryProjector,
    private val retentionRotator: RetentionRotator,
    private val redactor: DiagnosticsRedactor,
    private val environment: DiagnosticsEnvironment
) {
    fun emit(event: DiagnosticEvent): Result<Unit> {
        if (!settingsService.observe().value.enabled) {
            return Result.success(Unit)
        }

        return try {
            val redacted = redactor.redact(event)
            ensureScaffold()
            diagnosticsStore.appendTimeline(redacted).getOrThrow()
            if (redacted.isFailureOutcome()) {
                diagnosticsStore.appendFailure(redacted).getOrThrow()
            }
            val events = diagnosticsStore.readTimelineEvents().getOrThrow()
            diagnosticsStore.writeSummary(summaryProjector.project(events)).getOrThrow()
            retentionRotator.enforce().getOrThrow()
            Result.success(Unit)
        } catch (_: IOException) {
            Result.success(Unit)
        } catch (_: IllegalStateException) {
            Result.success(Unit)
        } catch (_: SecurityException) {
            Result.success(Unit)
        } catch (_: SerializationException) {
            Result.success(Unit)
        }
    }

    fun ensureScaffold(): Result<Unit> = runCatching {
        diagnosticsStore.writeManifest(environment.manifest(exportedAt = null)).getOrThrow()
        val events = diagnosticsStore.readTimelineEvents().getOrThrow()
        diagnosticsStore.writeSummary(summaryProjector.project(events)).getOrThrow()
    }
}
