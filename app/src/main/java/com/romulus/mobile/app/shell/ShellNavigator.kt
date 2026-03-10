package com.romulus.mobile.app.shell

import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShellNavigator(diagnosticsFacade: DiagnosticsFacade) {
    private val route = MutableStateFlow<ShellRoute>(ShellRoute.Home)

    @Suppress("UnusedPrivateProperty")
    private val diagnosticsSettings = diagnosticsFacade.observeSettings()

    fun observeRoute(): StateFlow<ShellRoute> = route.asStateFlow()

    fun enterShell(initialRoute: ShellRoute) {
        route.value = initialRoute
    }

    fun selectTab(route: ShellRoute) {
        this.route.value = route
    }

    fun openFiles(snapshotId: SnapshotId, entryId: SourceEntryId) {
        route.value = ShellRoute.Files(
            snapshotId = snapshotId,
            entryId = entryId
        )
    }

    fun acceptLaunchIntent(intent: AppLaunchIntent?) {
        intent?.preferredRoute?.let { route.value = it }
    }
}
