package com.romulus.mobile.app.startup

import com.romulus.mobile.app.platform.UriGrantRegistry
import com.romulus.mobile.app.shell.ShellRoute
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
    private val downloadsFacade: com.romulus.mobile.downloads.DownloadsFacade
) {
    suspend fun bootstrap(request: StartupBootstrapRequest): StartupRouteDecision {
        val setup = setupStateStore.read()
        if (!setup.isCompleted) {
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
            StartupRouteDecision.Shell(initialRoute = ShellRoute.Downloads)
        } else {
            StartupRouteDecision.Shell(initialRoute = ShellRoute.Home)
        }
    }
}
