package com.romulus.mobile.app.platform

import android.content.Intent
import com.romulus.mobile.app.shell.AppLaunchIntent
import com.romulus.mobile.app.shell.LaunchSource
import com.romulus.mobile.app.shell.ShellRoute

class NotificationDeepLinkHandler {
    fun resolve(intent: Intent?): AppLaunchIntent? {
        val route = intent?.getStringExtra(EXTRA_START_ROUTE) ?: return null
        val preferredRoute = when (route) {
            ROUTE_DOWNLOADS -> ShellRoute.Downloads
            else -> null
        }

        return AppLaunchIntent(
            preferredRoute = preferredRoute,
            source = LaunchSource.NOTIFICATION
        )
    }

    private companion object {
        const val EXTRA_START_ROUTE = "start_route"
        const val ROUTE_DOWNLOADS = "downloads"
    }
}

class NotificationPermissionRequester {
    fun requestIfNeeded(): Nothing =
        throw UnsupportedOperationException("NotificationPermissionRequester is not wired yet")
}
