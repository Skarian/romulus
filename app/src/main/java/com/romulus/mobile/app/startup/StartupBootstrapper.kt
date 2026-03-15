package com.romulus.mobile.app.startup

import com.romulus.mobile.app.platform.UriGrantRegistry
import com.romulus.mobile.app.shell.ShellRoute
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.source.snapshot.SourceRefreshTrigger

sealed interface StartupRouteDecision {
    data object Setup : StartupRouteDecision

    data class Shell(val initialRoute: ShellRoute) : StartupRouteDecision
}

class StartupBootstrapper(
    private val setupStateStore: SetupStateStore,
    private val uriGrantRegistry: UriGrantRegistry,
    private val appReadinessCoordinator: AppReadinessCoordinator,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: com.romulus.mobile.downloads.DownloadsFacade,
    private val diagnosticsFacade: DiagnosticsFacade
) {
    suspend fun bootstrap(request: StartupBootstrapRequest): StartupRouteDecision {
        diagnosticsFacade.record(
            domain = DiagnosticDomain.APP_SHELL,
            event = "bootstrap",
            outcome = "started"
        )
        val setup = setupStateStore.read()
        if (!setup.isCompleted) {
            diagnosticsFacade.record(
                domain = DiagnosticDomain.APP_SHELL,
                event = "bootstrap",
                outcome = "setup-required"
            )
            return StartupRouteDecision.Setup
        }

        uriGrantRegistry.restorePersistedGrants()

        val sourceReadiness = sourceFacade.readStartupReadiness()
        if (sourceReadiness.acceptedMode == SourceMode.URL) {
            sourceFacade.refresh(SourceRefreshTrigger.ColdLaunch)
        }

        appReadinessCoordinator.readStartupReadiness()
        downloadsFacade.requestAppLaunchRecovery()

        return if (request.launchIntent?.preferredRoute == ShellRoute.Downloads) {
            diagnosticsFacade.record(
                domain = DiagnosticDomain.APP_SHELL,
                event = "bootstrap",
                outcome = "succeeded",
                context = mapOf("route" to "Downloads")
            )
            StartupRouteDecision.Shell(initialRoute = ShellRoute.Downloads)
        } else {
            diagnosticsFacade.record(
                domain = DiagnosticDomain.APP_SHELL,
                event = "bootstrap",
                outcome = "succeeded",
                context = mapOf("route" to "Home")
            )
            StartupRouteDecision.Shell(initialRoute = ShellRoute.Home)
        }
    }
}
