package com.romulus.mobile.diagnostics.export

import java.nio.file.Path

sealed interface DiagnosticsExportResult {
    data class Exported(val bundlePath: Path) : DiagnosticsExportResult

    data class ExportedWithRetentionFailure(val bundlePath: Path, val message: String) :
        DiagnosticsExportResult

    data class Failed(val message: String) : DiagnosticsExportResult
}

sealed interface DiagnosticsClearResult {
    data object Cleared : DiagnosticsClearResult

    data class Failed(val message: String) : DiagnosticsClearResult
}
