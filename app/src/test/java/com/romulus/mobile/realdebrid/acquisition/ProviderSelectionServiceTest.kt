package com.romulus.mobile.realdebrid.acquisition

import com.romulus.mobile.realdebrid.AddedMagnetDto
import com.romulus.mobile.realdebrid.AvailableHostDto
import com.romulus.mobile.realdebrid.FakeRealDebridApi
import com.romulus.mobile.realdebrid.MutableClock
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.SelectFilesCall
import com.romulus.mobile.realdebrid.TorrentFileDto
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.normalizeProviderPath
import com.romulus.mobile.realdebrid.providerSelectionId
import com.romulus.mobile.realdebrid.budget.RequestBudget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectionServiceTest {
    @Test
    fun startCreatesFreshProviderTorrentAndSelectsRequestedFiles() = runTest {
        val api = FakeRealDebridApi().apply {
            availableHosts = listOf(AvailableHostDto(host = "host-a"))
            enqueueAddedMagnet(AddedMagnetDto(id = "download-torrent"))
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "waiting_files_selection",
                    files = listOf(
                        TorrentFileDto(id = 21, path = "Show/file-a.mkv", selected = 0),
                        TorrentFileDto(id = 22, path = "Show/file-b.mkv", selected = 0)
                    )
                )
            )
        }
        val service = ProviderSelectionService(
            budget = RequestBudget(MutableClock()),
            api = api
        )

        val marker = service.start(
            ProviderLocator(
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                torrentId = "browse-torrent",
                providerFileIds = listOf(
                    providerSelectionId(
                        normalizedPath = normalizeProviderPath("Show/file-a.mkv"),
                        sizeBytes = null,
                        occurrenceIndex = 1
                    )
                ),
                selectedProviderFileId = providerSelectionId(
                    normalizedPath = normalizeProviderPath("Show/file-a.mkv"),
                    sizeBytes = null,
                    occurrenceIndex = 1
                ),
                path = "Show/file-a.mkv",
                partLabel = "Part 1"
            )
        ).getOrThrow()

        assertEquals("download-torrent", marker.torrentId)
        assertEquals(listOf("21"), marker.selectedProviderFileIds)
        assertEquals(
            listOf(SelectFilesCall("download-torrent", "21")),
            api.selectFilesCalls
        )
    }

    @Test
    fun verifyRequiresSelectedIdsToStick() = runTest {
        val api = FakeRealDebridApi().apply {
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "downloading",
                    files = listOf(
                        TorrentFileDto(id = 11, path = "Show/file-a.mkv", selected = 1),
                        TorrentFileDto(id = 12, path = "Show/file-b.mkv", selected = 0)
                    )
                )
            )
        }
        val service = ProviderSelectionService(
            budget = RequestBudget(MutableClock()),
            api = api
        )

        val marker = service.verify(
            com.romulus.mobile.realdebrid.ProviderResumeMarker(
                torrentId = "download-torrent",
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                selectedProviderFileIds = listOf("11")
            )
        ).getOrThrow()

        assertEquals("download-torrent", marker.torrentId)
    }

    @Test
    fun verifyFailsWhenProviderSelectionBecomesTerminal() = runTest {
        val api = FakeRealDebridApi().apply {
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "virus",
                    files = listOf(
                        TorrentFileDto(id = 11, path = "Show/file-a.mkv", selected = 1)
                    )
                )
            )
        }
        val service = ProviderSelectionService(
            budget = RequestBudget(MutableClock()),
            api = api
        )

        val result = service.verify(
            ProviderResumeMarker(
                torrentId = "download-torrent",
                sourceMagnetUri = "magnet:?xt=urn:btih:source",
                selectedProviderFileIds = listOf("11")
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Provider selection failed with status: virus",
            result.exceptionOrNull()?.message
        )
    }
}
