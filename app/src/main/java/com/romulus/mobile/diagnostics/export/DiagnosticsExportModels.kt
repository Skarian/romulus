package com.romulus.mobile.diagnostics.export

sealed interface DiagnosticsExportResult {
    data class Exported(val targetLabel: String) : DiagnosticsExportResult

    data class Failed(val message: String) : DiagnosticsExportResult
}

sealed interface DiagnosticsClearResult {
    data object Cleared : DiagnosticsClearResult

    data class Failed(val message: String) : DiagnosticsClearResult
}
