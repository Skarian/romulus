package com.romulus.mobile.remotezip

import com.romulus.mobile.remotezip.copy.SelectedEntryCopier
import com.romulus.mobile.remotezip.enumerate.RemoteZipEnumerator
import com.romulus.mobile.remotezip.enumerate.RemoteZipSeekableChannel
import com.romulus.mobile.remotezip.probe.HttpRangeReader
import com.romulus.mobile.remotezip.probe.RangeProbeService
import com.romulus.mobile.remotezip.probe.RangeReadResult
import com.romulus.mobile.remotezip.probe.RemoteZipProbe
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.math.min
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteZipServicesTest {
    @Test
    fun enumerateReturnsDuplicateSafeEntriesForDuplicatePaths() = runTest {
        val archiveBytes = zipBytes(
            "folder/duplicate.txt" to "first",
            "folder/duplicate.txt" to "second",
            "folder/other.bin" to "third"
        )
        val enumerator = RemoteZipEnumerator(
            probeService = RangeProbeService(ByteArrayRangeReader(archiveBytes)),
            seekableChannelFactory = { probe ->
                RemoteZipSeekableChannel(probe, ByteArrayRangeReader(archiveBytes))
            }
        )

        val result = enumerator.enumerate(
            EnumerateRemoteZipRequest("https://example.com/archive.zip")
        ).getOrThrow()

        assertEquals(3, result.entries.size)
        assertEquals(2, result.entries.count { it.entryPath == "folder/duplicate.txt" })
        assertEquals(
            3,
            result.entries.map(ArchiveEntryDescriptor::identity).distinct().size
        )
    }

    @Test
    fun selectedEntryCopySupportsResumeWithoutWholeArchiveFallback() = runTest {
        val archiveBytes = zipBytes(
            "folder/keep.txt" to "abcdefghij",
            "folder/skip.txt" to "skip-me"
        )
        val probeService = RangeProbeService(ByteArrayRangeReader(archiveBytes))
        val enumerator = RemoteZipEnumerator(
            probeService = probeService,
            seekableChannelFactory = { probe ->
                RemoteZipSeekableChannel(probe, ByteArrayRangeReader(archiveBytes))
            }
        )
        val entry = enumerator.enumerate(
            EnumerateRemoteZipRequest("https://example.com/archive.zip")
        ).getOrThrow().entries.first { descriptor ->
            descriptor.entryPath == "folder/keep.txt"
        }
        val destination = Files.createTempDirectory("remotezip-test")
            .resolve("keep.txt")
        Files.write(destination, "abc".toByteArray(UTF_8))
        val progressEvents = mutableListOf<Long>()
        val copier = SelectedEntryCopier(
            probeService = probeService,
            seekableChannelFactory = { probe ->
                RemoteZipSeekableChannel(probe, ByteArrayRangeReader(archiveBytes))
            }
        )

        copier.copy(
            CopySelectedEntryRequest(
                archiveUrl = "https://example.com/archive.zip",
                identity = entry.identity,
                destination = destination,
                resumeByteOffset = 3L,
                onProgress = { downloadedBytes -> progressEvents += downloadedBytes }
            )
        ).getOrThrow()

        assertEquals("abcdefghij", String(Files.readAllBytes(destination), UTF_8))
        assertTrue(progressEvents.isNotEmpty())
        assertEquals(10L, progressEvents.last())
    }

    @Test
    fun probeFailsWhenRangesAreNotAdvertised() = runTest {
        val archiveBytes = zipBytes("folder/file.txt" to "hello")
        val probeService = RangeProbeService(
            ByteArrayRangeReader(
                archiveBytes = archiveBytes,
                acceptsRanges = false
            )
        )

        val result = probeService.probe("https://example.com/archive.zip")

        assertTrue(result.isFailure)
        assertEquals(
            "Remote archive https://example.com/archive.zip does not advertise byte-range support",
            result.exceptionOrNull()?.message
        )
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipArchiveOutputStream(output).use { zip ->
            entries.forEachIndexed { index, (path, contents) ->
                val entry = ZipArchiveEntry(path).apply {
                    method = ZipArchiveEntry.DEFLATED
                    unixMode = 0b110100100
                    time = index.toLong()
                }
                zip.putArchiveEntry(entry)
                zip.write(contents.toByteArray(UTF_8))
                zip.closeArchiveEntry()
            }
            zip.finish()
        }
        return output.toByteArray()
    }

    private class ByteArrayRangeReader(
        private val archiveBytes: ByteArray,
        private val acceptsRanges: Boolean = true
    ) : HttpRangeReader {
        override suspend fun head(url: String): Result<RangeReadResult> = Result.success(
            RangeReadResult(
                statusCode = 200,
                acceptsRanges = acceptsRanges,
                contentRange = null,
                body = ByteArray(0),
                contentLength = archiveBytes.size.toLong()
            )
        )

        override suspend fun readRange(
            url: String,
            startInclusive: Long,
            endInclusive: Long
        ): Result<RangeReadResult> {
            if (startInclusive >= archiveBytes.size) {
                return Result.failure(
                    RemoteZipFailureException(
                        stage = "ZIP_ENUMERATION",
                        code = "RANGE_WINDOW_REJECTED",
                        message = "Requested range is outside the archive"
                    )
                )
            }
            val actualEndInclusive = min(endInclusive, archiveBytes.size.toLong() - 1L)
            val body = archiveBytes.copyOfRange(
                startInclusive.toInt(),
                actualEndInclusive.toInt() + 1
            )
            return Result.success(
                RangeReadResult(
                    statusCode = 206,
                    acceptsRanges = acceptsRanges,
                    contentRange = "bytes $startInclusive-$actualEndInclusive/${archiveBytes.size}",
                    body = body,
                    contentLength = body.size.toLong()
                )
            )
        }
    }
}
