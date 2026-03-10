package com.romulus.mobile.remotezip

import com.romulus.mobile.remotezip.copy.SelectedEntryCopier
import com.romulus.mobile.remotezip.enumerate.RemoteZipEnumerator
import com.romulus.mobile.remotezip.enumerate.RemoteZipSeekableChannel
import com.romulus.mobile.remotezip.probe.HttpRangeReader
import com.romulus.mobile.remotezip.probe.RangeProbeService
import com.romulus.mobile.remotezip.probe.RangeReadResult
import java.nio.file.Paths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteZipInternalScaffoldTest {
    private val rangeReader = object : HttpRangeReader {
        override suspend fun head(url: String): Result<RangeReadResult> = Result.failure(
            UnsupportedOperationException("head is not wired in tests")
        )

        override suspend fun readRange(
            url: String,
            startInclusive: Long,
            endInclusive: Long
        ): Result<RangeReadResult> = Result.failure(
            UnsupportedOperationException("readRange is not wired in tests")
        )
    }

    @Test
    fun internalServicesFailExplicitlyUntilWired() = runTest {
        val probeService = RangeProbeService(rangeReader = rangeReader)
        val enumerator = RemoteZipEnumerator(
            probeService = probeService,
            seekableChannelFactory = { probe -> RemoteZipSeekableChannel(probe, rangeReader) }
        )
        val copier = SelectedEntryCopier(
            probeService = probeService,
            enumerator = enumerator,
            rangeReader = rangeReader
        )

        val probeResult = probeService.probe("https://example.com/archive.zip")
        val enumerateResult = enumerator.enumerate(
            EnumerateRemoteZipRequest(archiveUrl = "https://example.com/archive.zip")
        )
        val copyResult = copier.copy(
            CopySelectedEntryRequest(
                archiveUrl = "https://example.com/archive.zip",
                identity = ArchiveEntryIdentity(
                    localHeaderOffset = 1L,
                    compressedSize = 2L,
                    uncompressedSize = 3L,
                    crc32 = 4L,
                    normalizedPath = "folder/file.txt"
                ),
                destination = Paths.get("ignored"),
                resumeByteOffset = 0L,
                onProgress = {}
            )
        )

        assertTrue(probeResult.isFailure)
        assertTrue(enumerateResult.isFailure)
        assertTrue(copyResult.isFailure)
        assertEquals("RangeProbeService is not wired yet", probeResult.exceptionOrNull()?.message)
    }
}
