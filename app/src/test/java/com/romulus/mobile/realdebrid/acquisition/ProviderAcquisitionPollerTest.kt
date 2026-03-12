package com.romulus.mobile.realdebrid.acquisition

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.AddedMagnetDto
import com.romulus.mobile.realdebrid.AvailableHostDto
import com.romulus.mobile.realdebrid.FakeRealDebridApi
import com.romulus.mobile.realdebrid.MutableClock
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ProviderSelectionRequest
import com.romulus.mobile.realdebrid.TorrentFileDto
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.normalizeProviderPath
import com.romulus.mobile.realdebrid.budget.RequestBudget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAcquisitionPollerTest {
    @Test
    fun startReturnsWaitingStatusWithProgressAndResumeMarker() = runTest {
        val api = FakeRealDebridApi().apply {
            availableHosts = listOf(AvailableHostDto(host = "host-a"))
            enqueueAddedMagnet(AddedMagnetDto(id = "download-torrent"))
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "waiting_files_selection",
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 0)
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "waiting_files_selection",
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 1)
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "downloading",
                    progress = 42.5,
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 1)
                    )
                )
            )
        }
        val budget = RequestBudget(MutableClock())
        val poller = ProviderAcquisitionPoller(
            selectionService = ProviderSelectionService(budget, api),
            budget = budget,
            api = api
        )

        val result = poller.start(
            ProviderSelectionRequest(
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                normalizedPath = normalizeProviderPath("Show/file-a.mkv"),
                sizeBytes = null,
                occurrenceIndex = 1
            )
        ).getOrThrow()

        assertTrue(result is AcquisitionStatus.Waiting)
        result as AcquisitionStatus.Waiting
        assertEquals("downloading", result.statusLabel)
        assertEquals(42.5, result.progressPercent)
        assertEquals("download-torrent", result.resumeMarker.torrentId)
    }

    @Test
    fun startReturnsReadyLinksWithoutUnrestrictingThem() = runTest {
        val api = FakeRealDebridApi().apply {
            availableHosts = listOf(AvailableHostDto(host = "host-a"))
            enqueueAddedMagnet(AddedMagnetDto(id = "download-torrent"))
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "waiting_files_selection",
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 0)
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "queued",
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 1)
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "downloaded",
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 1)
                    ),
                    links = listOf("https://restricted.example/file-a")
                )
            )
        }
        val budget = RequestBudget(MutableClock())
        val poller = ProviderAcquisitionPoller(
            selectionService = ProviderSelectionService(budget, api),
            budget = budget,
            api = api
        )

        val result = poller.start(
            ProviderSelectionRequest(
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                normalizedPath = normalizeProviderPath("Show/file-a.mkv"),
                sizeBytes = null,
                occurrenceIndex = 1
            )
        ).getOrThrow()

        assertTrue(result is AcquisitionStatus.LinksReady)
        result as AcquisitionStatus.LinksReady
        assertEquals(
            listOf("https://restricted.example/file-a"),
            result.readyLinks.map { it.restrictedUrl }
        )
        assertTrue(api.unrestrictCalls.isEmpty())
    }

    @Test
    fun resumeContinuesFromSavedMarker() = runTest {
        val api = FakeRealDebridApi().apply {
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "queued",
                    files = listOf(
                        TorrentFileDto(id = 11, path = "Show/file-a.mkv", selected = 1)
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "queued",
                    progress = 5.0,
                    files = listOf(
                        TorrentFileDto(id = 11, path = "Show/file-a.mkv", selected = 1)
                    )
                )
            )
        }
        val budget = RequestBudget(MutableClock())
        val poller = ProviderAcquisitionPoller(
            selectionService = ProviderSelectionService(budget, api),
            budget = budget,
            api = api
        )

        val result = poller.resume(
            ProviderResumeMarker(
                torrentId = "download-torrent",
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                selectedProviderFileIds = listOf("11")
            )
        ).getOrThrow()

        assertTrue(result is AcquisitionStatus.Waiting)
        assertTrue(api.addMagnetCalls.isEmpty())
        assertEquals(listOf("download-torrent", "download-torrent"), api.torrentInfoCalls)
    }

    @Test
    fun resumeFailsWhenProviderAcquisitionBecomesTerminal() = runTest {
        val api = FakeRealDebridApi().apply {
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "queued",
                    files = listOf(
                        TorrentFileDto(id = 11, path = "Show/file-a.mkv", selected = 1)
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "dead",
                    files = listOf(
                        TorrentFileDto(id = 11, path = "Show/file-a.mkv", selected = 1)
                    )
                )
            )
        }
        val budget = RequestBudget(MutableClock())
        val poller = ProviderAcquisitionPoller(
            selectionService = ProviderSelectionService(budget, api),
            budget = budget,
            api = api
        )

        val result = poller.resume(
            ProviderResumeMarker(
                torrentId = "download-torrent",
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                selectedProviderFileIds = listOf("11")
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Provider acquisition failed with status: dead",
            result.exceptionOrNull()?.message
        )
    }
}
