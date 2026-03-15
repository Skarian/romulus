package com.romulus.mobile.downloads.attempts

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpDownloadTransportTest {
    @Test
    fun createClientUsesLargeTransferTimeouts() {
        val client = OkHttpDownloadTransport.createClient()

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(TimeUnit.MINUTES.toMillis(5).toInt(), client.readTimeoutMillis)
        assertEquals(0, client.callTimeoutMillis)
    }
}
