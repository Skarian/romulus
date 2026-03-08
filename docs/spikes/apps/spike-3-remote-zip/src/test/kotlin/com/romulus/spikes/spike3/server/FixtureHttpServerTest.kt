package com.romulus.spikes.spike3.server

import com.romulus.spikes.spike3.TestSupport
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureHttpServerTest {
    private val client = OkHttpClient()

    @Test
    fun servesHealthAndRangeModes() {
        FixtureHttpServer(TestSupport.fixtureRoot(), 0).use { server ->
            val baseUri = server.start()

            client.newCall(Request.Builder().url("${baseUri}health").get().build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("OK", response.body!!.string())
            }

            client.newCall(
                Request.Builder()
                    .url("${baseUri}range/regular.zip")
                    .header("Range", "bytes=0-31")
                    .get()
                    .build(),
            ).execute().use { response ->
                assertEquals(206, response.code)
                assertEquals("bytes", response.header("Accept-Ranges"))
                assertEquals("full", response.header("X-Spike-Range-Mode"))
                assertEquals(32, response.body!!.bytes().size)
            }

            client.newCall(
                Request.Builder()
                    .url("${baseUri}no-range/regular.zip")
                    .header("Range", "bytes=0-31")
                    .get()
                    .build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("none", response.header("X-Spike-Range-Mode"))
                assertTrue(response.body!!.bytes().isNotEmpty())
            }

            client.newCall(
                Request.Builder()
                    .url("${baseUri}capped-range/regular.zip")
                    .header("Range", "bytes=0-8191")
                    .get()
                    .build(),
            ).execute().use { response ->
                assertEquals(416, response.code)
                assertEquals("capped", response.header("X-Spike-Range-Mode"))
            }
        }
    }
}
