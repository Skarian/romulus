package com.romulus.mobile.app.shell

import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShellNavigator(
    diagnosticsFacade: DiagnosticsFacade,
    private val diagnosticsScope: CoroutineScope
) {
    private val route = MutableStateFlow<ShellRoute>(ShellRoute.Home)
    private var lastHomeRoute: ShellRoute = ShellRoute.Home
    private var shellEntered = false

    private val diagnosticsSettings = diagnosticsFacade.observeSettings()
    private val diagnosticsFacade = diagnosticsFacade

    fun observeRoute(): StateFlow<ShellRoute> = route.asStateFlow()

    fun enterShell(initialRoute: ShellRoute) {
        if (shellEntered) {
            return
        }
        shellEntered = true
        rememberHomeRoute(initialRoute)
        route.value = initialRoute
        recordNavigationEvent("enter-shell", initialRoute)
    }

    fun selectTab(route: ShellRoute) {
        rememberHomeRoute(this.route.value)
        this.route.value = when (route) {
            ShellRoute.Home -> lastHomeRoute
            else -> route
        }
        recordNavigationEvent("select-tab", this.route.value)
    }

    fun openFiles(snapshotId: SnapshotId, entryId: SourceEntryId, entryDisplayName: String) {
        val filesRoute = ShellRoute.Files(
            snapshotId = snapshotId,
            entryId = entryId,
            entryDisplayName = entryDisplayName
        )
        lastHomeRoute = filesRoute
        route.value = filesRoute
        recordNavigationEvent("open-files", filesRoute)
    }

    fun returnToHomeRoot() {
        lastHomeRoute = ShellRoute.Home
        route.value = ShellRoute.Home
        recordNavigationEvent("return-home", ShellRoute.Home)
    }

    fun acceptLaunchIntent(intent: AppLaunchIntent?) {
        intent?.preferredRoute?.let {
            shellEntered = true
            rememberHomeRoute(it)
            route.value = it
            recordNavigationEvent("accept-launch-intent", it)
        }
    }

    private fun rememberHomeRoute(route: ShellRoute) {
        if (route == ShellRoute.Home || route is ShellRoute.Files) {
            lastHomeRoute = route
        }
    }

    private fun recordNavigationEvent(event: String, route: ShellRoute) {
        if (!diagnosticsSettings.value.enabled) {
            return
        }
        diagnosticsScope.launch {
            diagnosticsFacade.record(
                domain = DiagnosticDomain.APP_SHELL,
                event = event,
                outcome = "observed",
                context = mapOf("route" to route::class.java.simpleName)
            )
        }
    }
}
