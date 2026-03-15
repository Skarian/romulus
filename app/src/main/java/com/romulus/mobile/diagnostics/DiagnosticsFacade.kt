package com.romulus.mobile.diagnostics

import android.app.Application
import android.net.Uri
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.events.DiagnosticsRedactor
import com.romulus.mobile.diagnostics.events.DiagnosticsSink
import com.romulus.mobile.diagnostics.export.AndroidDiagnosticsExportFilesystem
import com.romulus.mobile.diagnostics.export.DiagnosticsBundleWriter
import com.romulus.mobile.diagnostics.export.DiagnosticsClearResult
import com.romulus.mobile.diagnostics.export.DiagnosticsExportResult
import com.romulus.mobile.diagnostics.export.DiagnosticsExportService
import com.romulus.mobile.diagnostics.settings.DiagnosticsSettings
import com.romulus.mobile.diagnostics.settings.DiagnosticsSettingsService
import com.romulus.mobile.diagnostics.settings.SharedPreferencesDiagnosticsSettingsStore
import com.romulus.mobile.diagnostics.store.AndroidDiagnosticsFilesystem
import com.romulus.mobile.diagnostics.store.DiagnosticsStore
import com.romulus.mobile.diagnostics.store.RetentionRotator
import com.romulus.mobile.diagnostics.store.SummaryProjector
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class DiagnosticsFacade internal constructor(
    private val settingsService: DiagnosticsSettingsService?,
    private val sink: DiagnosticsSink?,
    private val exportService: DiagnosticsExportService?,
    private val clock: Clock?,
    private val sessionSeed: String?
) {
    private val settings = MutableStateFlow(DiagnosticsSettings(enabled = false))

    constructor() : this(
        settingsService = null,
        sink = null,
        exportService = null,
        clock = null,
        sessionSeed = null
    )

    fun observeSettings(): StateFlow<DiagnosticsSettings> =
        settingsService?.observe() ?: settings.asStateFlow()

    suspend fun setEnabled(enabled: Boolean): Result<Unit> =
        settingsService?.setEnabled(enabled) ?: unwiredFailure()

    fun emit(event: DiagnosticEvent): Result<Unit> = sink?.emit(event) ?: unwiredFailure()

    fun record(
        domain: DiagnosticDomain,
        event: String,
        outcome: String,
        taskId: String? = null,
        snapshotId: String? = null,
        context: Map<String, String> = emptyMap()
    ): Result<Unit> {
        val currentClock = clock
        val currentSessionSeed = sessionSeed
        if (currentClock == null || currentSessionSeed == null) {
            return unwiredFailure()
        }
        return emit(
            DiagnosticEvent(
                timestamp = currentClock.instant(),
                sessionId = currentSessionSeed,
                domain = domain,
                event = event,
                outcome = outcome,
                taskId = taskId,
                snapshotId = snapshotId,
                context = context
            )
        )
    }

    fun export(destinationUri: Uri, targetLabel: String): DiagnosticsExportResult =
        exportService?.exportCurrent(destinationUri, targetLabel) ?: DiagnosticsExportResult.Failed(
            "DiagnosticsFacade is not wired yet"
        )

    fun clear(): DiagnosticsClearResult =
        exportService?.clearAll() ?: DiagnosticsClearResult.Failed(
            "DiagnosticsFacade is not wired yet"
        )

    companion object {
        fun create(application: Application): DiagnosticsFacade {
            val clock = Clock.systemUTC()
            val json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
            val environment = DiagnosticsEnvironment.create(application, clock)
            val store = DiagnosticsStore(
                filesystem = AndroidDiagnosticsFilesystem(application),
                json = json
            )
            val settingsService = DiagnosticsSettingsService(
                store = SharedPreferencesDiagnosticsSettingsStore(application)
            )
            val summaryProjector = SummaryProjector()
            val sink = DiagnosticsSink(
                settingsService = settingsService,
                diagnosticsStore = store,
                summaryProjector = summaryProjector,
                retentionRotator = RetentionRotator(
                    diagnosticsStore = store,
                    summaryProjector = summaryProjector
                ),
                redactor = DiagnosticsRedactor(),
                environment = environment
            )
            return DiagnosticsFacade(
                settingsService = settingsService,
                sink = sink,
                exportService = DiagnosticsExportService(
                    diagnosticsStore = store,
                    bundleWriter = DiagnosticsBundleWriter(
                        exportFilesystem = AndroidDiagnosticsExportFilesystem(application),
                        json = json
                    ),
                    diagnosticsSink = sink,
                    environment = environment,
                    clock = clock
                ),
                clock = clock,
                sessionSeed = environment.sessionSeed
            )
        }
    }

    private fun unwiredFailure(): Result<Unit> = Result.failure(
        UnsupportedOperationException("DiagnosticsFacade is not wired yet")
    )
}

@Suppress("TooGenericExceptionCaught")
internal suspend inline fun <T> DiagnosticsFacade.capture(
    domain: DiagnosticDomain,
    event: String,
    taskId: String? = null,
    snapshotId: String? = null,
    context: Map<String, String> = emptyMap(),
    block: suspend () -> T
): Result<T> {
    record(
        domain = domain,
        event = event,
        outcome = "started",
        taskId = taskId,
        snapshotId = snapshotId,
        context = context
    )
    return try {
        val value = block()
        record(
            domain = domain,
            event = event,
            outcome = "succeeded",
            taskId = taskId,
            snapshotId = snapshotId,
            context = context
        )
        Result.success(value)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        record(
            domain = domain,
            event = event,
            outcome = "failed",
            taskId = taskId,
            snapshotId = snapshotId,
            context = context + throwable.toDiagnosticContext()
        )
        Result.failure(throwable)
    }
}

internal fun Throwable.toDiagnosticContext(): Map<String, String> = buildMap {
    put("errorType", this@toDiagnosticContext::class.java.simpleName)
    message?.takeIf(String::isNotBlank)?.let { put("message", it) }
}
