package com.romulus.mobile.app.shell

import com.romulus.mobile.diagnostics.DiagnosticsFacade
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellNavigatorTest {
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
}
