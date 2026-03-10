package com.romulus.mobile.diagnostics

import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.export.DiagnosticsClearResult
import com.romulus.mobile.diagnostics.export.DiagnosticsExportResult
import com.romulus.mobile.diagnostics.settings.DiagnosticsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("RedundantSuspendModifier", "UnusedParameter")
class DiagnosticsFacade {
    private val settings = MutableStateFlow(DiagnosticsSettings(enabled = false))

    fun observeSettings(): StateFlow<DiagnosticsSettings> = settings.asStateFlow()

    suspend fun setEnabled(enabled: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("DiagnosticsFacade is not wired yet"))

    suspend fun emit(event: DiagnosticEvent): Result<Unit> =
        Result.failure(UnsupportedOperationException("DiagnosticsFacade is not wired yet"))

    suspend fun export(): DiagnosticsExportResult =
        DiagnosticsExportResult.Failed("DiagnosticsFacade is not wired yet")

    suspend fun clear(): DiagnosticsClearResult =
        DiagnosticsClearResult.Failed("DiagnosticsFacade is not wired yet")
}
