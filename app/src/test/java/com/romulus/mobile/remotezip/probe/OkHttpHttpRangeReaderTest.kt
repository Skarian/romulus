package com.romulus.mobile.remotezip.probe

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpHttpRangeReaderTest {
    @Test
    fun readRangeStreamsRequestedBytesEvenWhenContentLengthReflectsWholeArchive() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Range", "bytes 0-0/6051899850")
                .setHeader("Content-Length", "6051899850")
                .setBody("X")
        )
        server.start()
        try {
            val reader = OkHttpHttpRangeReader(OkHttpClient())

            val result = reader.readRange(
                url = server.url("/archive.zip").toString(),
                startInclusive = 0L,
                endInclusive = 0L
            ).getOrThrow()

            assertEquals(206, result.statusCode)
            assertEquals("bytes 0-0/6051899850", result.contentRange)
            assertEquals(1, result.body.size)
            assertEquals('X'.code.toByte(), result.body.single())
        } finally {
            server.shutdown()
        }
    }
}
