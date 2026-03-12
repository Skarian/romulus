package com.romulus.mobile.realdebrid.inventory

import com.romulus.mobile.realdebrid.AddedMagnetDto
import com.romulus.mobile.realdebrid.AddMagnetCall
import com.romulus.mobile.realdebrid.AvailableHostDto
import com.romulus.mobile.realdebrid.FakeRealDebridApi
import com.romulus.mobile.realdebrid.MutableClock
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderSourceRef
import com.romulus.mobile.realdebrid.TorrentFileDto
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.normalizeProviderPath
import com.romulus.mobile.realdebrid.providerSelectionId
import com.romulus.mobile.realdebrid.budget.RequestBudget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TorrentInventoryServiceTest {
    @Test
    fun enumeratesInventoryAcrossSourcesAndPreservesPartLabels() = runTest {
        val api = FakeRealDebridApi().apply {
            availableHosts = listOf(
                AvailableHostDto(host = "host-a"),
                AvailableHostDto(host = "host-b")
            )
            enqueueAddedMagnet(AddedMagnetDto(id = "torrent-1"))
            enqueueAddedMagnet(AddedMagnetDto(id = "torrent-2"))
            enqueueTorrentInfo(
                "torrent-1",
                TorrentInfoDto(
                    id = "torrent-1",
                    files = listOf(
                        TorrentFileDto(
                            id = 11,
                            path = "Show/file-a.mkv",
                            bytes = 100L
                        )
                    )
                )
            )
            enqueueTorrentInfo(
                "torrent-2",
                TorrentInfoDto(
                    id = "torrent-2",
                    files = listOf(
                        TorrentFileDto(
                            id = 22,
                            path = "Show/file-b.mkv",
                            bytes = 200L
                        )
                    )
                )
            )
        }
        val service = TorrentInventoryService(
            budget = RequestBudget(MutableClock()),
            api = api
        )

        val result = service.enumerate(
            ProviderInventoryRequest(
                sources = listOf(
                    ProviderSourceRef("magnet:?xt=urn:btih:first", "Part 1"),
                    ProviderSourceRef("magnet:?xt=urn:btih:second", "Part 2")
                )
            )
        ).getOrThrow()

        assertEquals(
            listOf(
                AddMagnetCall("magnet:?xt=urn:btih:first", "host-a"),
                AddMagnetCall("magnet:?xt=urn:btih:second", "host-a")
            ),
            api.addMagnetCalls
        )
        assertEquals(2, result.files.size)
        assertEquals("Part 1", result.files[0].partLabel)
        assertEquals("Part 2", result.files[1].partLabel)
        assertEquals(
            listOf(
                providerSelectionId(
                    normalizedPath = normalizeProviderPath("Show/file-a.mkv"),
                    sizeBytes = 100L,
                    occurrenceIndex = 1
                )
            ),
            result.files[0].locator.providerFileIds
        )
        assertEquals("torrent-2", result.files[1].locator.torrentId)
    }
}
