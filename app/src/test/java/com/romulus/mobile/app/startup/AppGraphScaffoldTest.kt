package com.romulus.mobile.app.startup

import com.romulus.mobile.app.platform.NotificationPermissionRequester
import org.junit.Assert.assertEquals
import org.junit.Test

class AppGraphScaffoldTest {
    @Test
    fun notificationPermissionRequesterOnlyRequestsOnce() {
        val requester = NotificationPermissionRequester()
        var hasPermission = false
        var requestCount = 0

        requester.bindHost(
            hasPermission = { hasPermission },
            requestPermission = {
                requestCount += 1
                hasPermission = true
            }
        )

        requester.requestIfNeeded()
        requester.requestIfNeeded()

        assertEquals(1, requestCount)
    }

    @Test
    fun notificationPermissionRequesterSkipsPromptWhenAlreadyGranted() {
        val requester = NotificationPermissionRequester()
        var requestCount = 0

        requester.bindHost(
            hasPermission = { true },
            requestPermission = { requestCount += 1 }
        )

        requester.requestIfNeeded()

        assertEquals(0, requestCount)
    }
}
