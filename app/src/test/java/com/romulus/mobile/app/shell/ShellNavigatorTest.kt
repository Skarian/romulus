package com.romulus.mobile.app.shell

import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellNavigatorTest {
    @Test
    fun enterShellDoesNotResetExistingRoute() {
        val navigator = ShellNavigator(DiagnosticsFacade())

        navigator.enterShell(ShellRoute.Home)
        navigator.selectTab(ShellRoute.Settings)
        navigator.enterShell(ShellRoute.Home)

        assertEquals(ShellRoute.Settings, navigator.observeRoute().value)
    }

    @Test
    fun launchIntentCanRouteToDownloads() {
        val navigator = ShellNavigator(DiagnosticsFacade())

        navigator.acceptLaunchIntent(
            AppLaunchIntent(
                preferredRoute = ShellRoute.Downloads,
                source = LaunchSource.NOTIFICATION
            )
        )

        assertEquals(ShellRoute.Downloads, navigator.observeRoute().value)
    }

    @Test
    fun homeTabRestoresLastFilesRoute() {
        val navigator = ShellNavigator(DiagnosticsFacade())

        navigator.enterShell(ShellRoute.Home)
        navigator.openFiles(
            snapshotId = SnapshotId("snapshot"),
            entryId = SourceEntryId("entry"),
            entryDisplayName = "Source name"
        )
        navigator.selectTab(ShellRoute.Downloads)
        navigator.selectTab(ShellRoute.Home)

        assertEquals(
            ShellRoute.Files(
                snapshotId = SnapshotId("snapshot"),
                entryId = SourceEntryId("entry"),
                entryDisplayName = "Source name"
            ),
            navigator.observeRoute().value
        )
    }

    @Test
    fun returnToHomeRootClearsLastFilesRoute() {
        val navigator = ShellNavigator(DiagnosticsFacade())

        navigator.enterShell(ShellRoute.Home)
        navigator.openFiles(
            snapshotId = SnapshotId("snapshot"),
            entryId = SourceEntryId("entry"),
            entryDisplayName = "Source name"
        )

        navigator.returnToHomeRoot()
        navigator.selectTab(ShellRoute.Downloads)
        navigator.selectTab(ShellRoute.Home)

        assertEquals(ShellRoute.Home, navigator.observeRoute().value)
    }
}
