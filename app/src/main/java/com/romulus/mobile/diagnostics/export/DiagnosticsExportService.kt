package com.romulus.mobile.diagnostics.export

import android.net.Uri
import com.romulus.mobile.diagnostics.DiagnosticsEnvironment
import com.romulus.mobile.diagnostics.events.DiagnosticsSink
import com.romulus.mobile.diagnostics.store.DiagnosticsStore
import com.romulus.mobile.diagnostics.store.StagedInternalClear
import java.io.IOException
import java.time.Clock

internal class DiagnosticsExportService(
    private val diagnosticsStore: DiagnosticsStore,
    private val bundleWriter: DiagnosticsBundleWriter,
    private val diagnosticsSink: DiagnosticsSink,
    private val environment: DiagnosticsEnvironment,
    private val clock: Clock
) {
    fun exportCurrent(destinationUri: Uri, targetLabel: String): DiagnosticsExportResult = try {
        diagnosticsSink.ensureScaffold().getOrThrow()
        val snapshot = diagnosticsStore.snapshot().getOrThrow()
        bundleWriter
            .write(
                snapshot = snapshot,
                manifest = environment.manifest(exportedAt = clock.instant()),
                destinationUri = destinationUri
            ).getOrThrow()
        DiagnosticsExportResult.Exported(targetLabel)
    } catch (error: IOException) {
        exportFailure(error)
    } catch (error: IllegalStateException) {
        exportFailure(error)
    } catch (error: SecurityException) {
        exportFailure(error)
    }

    fun clearAll(): DiagnosticsClearResult = try {
        val staged = diagnosticsStore.stageInternalClear().getOrThrow()
        commitClear(staged)
    } catch (error: IOException) {
        clearFailure(error)
    } catch (error: IllegalStateException) {
        clearFailure(error)
    } catch (error: SecurityException) {
        clearFailure(error)
    }

    private fun commitClear(staged: StagedInternalClear): DiagnosticsClearResult = try {
        diagnosticsStore.commitInternalClear(staged).getOrThrow()
        DiagnosticsClearResult.Cleared
    } catch (error: IOException) {
        rollbackFailure(staged, error)
    } catch (error: IllegalStateException) {
        rollbackFailure(staged, error)
    } catch (error: SecurityException) {
        rollbackFailure(staged, error)
    }

    private fun rollbackFailure(
        staged: StagedInternalClear,
        error: Exception
    ): DiagnosticsClearResult {
        diagnosticsStore.rollbackInternalClear(staged).getOrThrow()
        return clearFailure(error)
    }

    private fun exportFailure(error: Exception): DiagnosticsExportResult =
        DiagnosticsExportResult.Failed(error.message ?: "Diagnostics export failed")

    private fun clearFailure(error: Exception): DiagnosticsClearResult =
        DiagnosticsClearResult.Failed(error.message ?: "Diagnostics clear failed")
}
