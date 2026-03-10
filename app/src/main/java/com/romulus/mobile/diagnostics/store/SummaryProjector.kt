package com.romulus.mobile.diagnostics.store

import com.romulus.mobile.diagnostics.events.DiagnosticEvent

@Suppress("RedundantSuspendModifier", "UnusedParameter", "UnusedPrivateProperty")
internal class SummaryProjector(private val diagnosticsStore: DiagnosticsStore) {
    suspend fun apply(event: DiagnosticEvent): Result<Unit> = Result.failure(
        UnsupportedOperationException("SummaryProjector is not wired yet")
    )
}
