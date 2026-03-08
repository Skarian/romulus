package com.romulus.spikes.spike3.zip

import com.romulus.spikes.spike3.TestSupport
import com.romulus.spikes.spike3.errors.FailureCodes
import com.romulus.spikes.spike3.errors.Spike3FailureException
import com.romulus.spikes.spike3.http.HttpTraceRecorder
import com.romulus.spikes.spike3.http.OkHttpRangeClient
import com.romulus.spikes.spike3.model.RangeMode
import com.romulus.spikes.spike3.server.FixtureHttpServer
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteSeekableByteChannelTest {
    @Test
    fun probesSizeAndReadsExpectedBytes() {
        FixtureHttpServer(TestSupport.fixtureRoot(), 0).use { server ->
            val baseUri = server.start()
            val traceRecorder = HttpTraceRecorder()
            val client = OkHttpRangeClient(recorder = traceRecorder)
            val archiveUrl = TestSupport.archiveUrl(baseUri, RangeMode.FULL, "regular.zip")
            val archiveInfo = client.probe("channel-success", archiveUrl)
            val localBytes = Files.readAllBytes(TestSupport.fixtureRoot().resolve("regular.zip"))

            RemoteSeekableByteChannel(
                caseId = "channel-success",
                archiveUrl = archiveUrl,
                archiveSize = archiveInfo.contentLength,
                rangeHttpClient = client,
                tailPrefetchBytes = 64,
                readPrefetchBytes = 64,
            ).use { channel ->
                val buffer = ByteBuffer.allocate(32)
                val bytesRead = channel.read(buffer)
                assertEquals(32, bytesRead)
                assertEquals(archiveInfo.contentLength, channel.size())
                assertContentEquals(localBytes.copyOfRange(0, 32), buffer.array())
            }
        }
    }

    @Test
    fun failsWhenServerReturns200ForArchiveBodyRead() {
        FixtureHttpServer(TestSupport.fixtureRoot(), 0).use { server ->
            val baseUri = server.start()
            val client = OkHttpRangeClient(recorder = HttpTraceRecorder())
            val archiveUrl = TestSupport.archiveUrl(baseUri, RangeMode.NONE, "regular.zip")

            RemoteSeekableByteChannel(
                caseId = "channel-full-body-failure",
                archiveUrl = archiveUrl,
                archiveSize = TestSupport.fixtureSize("regular.zip"),
                rangeHttpClient = client,
            ).use { channel ->
                val failure = assertFailsWith<Spike3FailureException> {
                    channel.read(ByteBuffer.allocate(16))
                }
                assertEquals(FailureCodes.FULL_BODY_RESPONSE, failure.errorCode)
            }
        }
    }
}
