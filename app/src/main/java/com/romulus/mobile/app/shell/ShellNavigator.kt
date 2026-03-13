package com.romulus.mobile.app.shell

import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShellNavigator(diagnosticsFacade: DiagnosticsFacade) {
    private val route = MutableStateFlow<ShellRoute>(ShellRoute.Home)
    private var lastHomeRoute: ShellRoute = ShellRoute.Home
    private var shellEntered = false

    @Suppress("UnusedPrivateProperty")
    private val diagnosticsSettings = diagnosticsFacade.observeSettings()

    fun observeRoute(): StateFlow<ShellRoute> = route.asStateFlow()

    fun enterShell(initialRoute: ShellRoute) {
        if (shellEntered) {
            return
        }
        shellEntered = true
        rememberHomeRoute(initialRoute)
        route.value = initialRoute
    }

    fun selectTab(route: ShellRoute) {
        rememberHomeRoute(this.route.value)
        this.route.value = when (route) {
            ShellRoute.Home -> lastHomeRoute
            else -> route
        }
    }

    fun openFiles(snapshotId: SnapshotId, entryId: SourceEntryId, entryDisplayName: String) {
        val filesRoute = ShellRoute.Files(
            snapshotId = snapshotId,
            entryId = entryId,
            entryDisplayName = entryDisplayName
        )
        lastHomeRoute = filesRoute
        route.value = filesRoute
    }

    fun returnToHomeRoot() {
        lastHomeRoute = ShellRoute.Home
        route.value = ShellRoute.Home
    }

    fun acceptLaunchIntent(intent: AppLaunchIntent?) {
        intent?.preferredRoute?.let {
            shellEntered = true
            rememberHomeRoute(it)
            route.value = it
        }
    }

    private fun rememberHomeRoute(route: ShellRoute) {
        if (route == ShellRoute.Home || route is ShellRoute.Files) {
            lastHomeRoute = route
        }
    }
}
