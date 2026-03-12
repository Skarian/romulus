package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.AddedMagnetDto
import com.romulus.mobile.realdebrid.FakeRealDebridApi
import com.romulus.mobile.realdebrid.MutableClock
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderSourceRef
import com.romulus.mobile.realdebrid.TorrentFileDto
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.UnrestrictedLinkDto
import com.romulus.mobile.realdebrid.normalizeProviderPath
import com.romulus.mobile.realdebrid.providerSelectionId
import com.romulus.mobile.realdebrid.acquisition.ProviderAcquisitionPoller
import com.romulus.mobile.realdebrid.acquisition.ProviderSelectionService
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.inventory.TorrentInventoryService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactZipResolverTest {
    @Test
    fun resolvesExactZipLocatorFromInventoryAndAcquisition() = runTest {
        val api = FakeRealDebridApi().apply {
            availableHosts = listOf("host-a")
            enqueueAddedMagnet(AddedMagnetDto(id = "browse-torrent"))
            enqueueAddedMagnet(AddedMagnetDto(id = "download-torrent"))
            enqueueTorrentInfo(
                "browse-torrent",
                TorrentInfoDto(
                    id = "browse-torrent",
                    files = listOf(
                        TorrentFileDto(
                            id = 22,
                            path = "Show/archive.zip",
                            bytes = 300L
                        )
                    )
                )
            )
            enqueueTorrentInfo(
                "download-torrent",
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "queued",
                    files = listOf(
                        TorrentFileDto(
                            id = 22,
                            path = "Show/archive.zip",
                            bytes = 300L,
                            selected = 1
                        )
                    )
                ),
                TorrentInfoDto(
                    id = "download-torrent",
                    status = "downloaded",
                    files = listOf(
                        TorrentFileDto(
                            id = 22,
                            path = "Show/archive.zip",
                            bytes = 300L,
                            selected = 1
                        )
                    ),
                    links = listOf("https://restricted.example/archive")
                )
            )
            respondUnrestrict(
                "https://restricted.example/archive",
                Result.success(
                    UnrestrictedLinkDto(
                        filename = "archive.zip",
                        downloadUrl = "https://download.example/archive.zip"
                    )
                )
            )
        }
        val budget = RequestBudget(MutableClock())
        val inventoryService = TorrentInventoryService(budget, api)
        val acquisitionPoller = ProviderAcquisitionPoller(
            selectionService = ProviderSelectionService(budget, api),
            budget = budget,
            api = api
        )
        val resolver = ExactZipResolver(
            inventoryService = inventoryService,
            acquisitionPoller = acquisitionPoller,
            budget = budget,
            api = api
        )

        val result = resolver.resolve(
            com.romulus.mobile.realdebrid.ExactZipRequest(
                sources = listOf(
                    ProviderSourceRef("magnet:?xt=urn:btih:source", null)
                ),
                exactPath = "Show/archive.zip"
            )
        ).getOrThrow()

        val selectionId = selectionId(path = "Show/archive.zip", sizeBytes = 300L)
        assertEquals("https://download.example/archive.zip", result.archiveUrl)
        assertEquals("archive.zip", result.originalName)
        assertEquals("download-torrent", result.providerLocator.torrentId)
        assertEquals(listOf(selectionId), result.providerLocator.providerFileIds)
        assertEquals(selectionId, result.providerLocator.selectedProviderFileId)
    }

    @Test
    fun failsWhenExactPathIsMissing() = runTest {
        val api = FakeRealDebridApi().apply {
            availableHosts = listOf("host-a")
            enqueueAddedMagnet(AddedMagnetDto(id = "browse-torrent"))
            enqueueTorrentInfo(
                "browse-torrent",
                TorrentInfoDto(
                    id = "browse-torrent",
                    files = listOf(
                        TorrentFileDto(
                            id = 22,
                            path = "Show/other-file.mkv",
                            bytes = 300L
                        )
                    )
                )
            )
        }
        val budget = RequestBudget(MutableClock())
        val resolver = ExactZipResolver(
            inventoryService = TorrentInventoryService(budget, api),
            acquisitionPoller = ProviderAcquisitionPoller(
                selectionService = ProviderSelectionService(budget, api),
                budget = budget,
                api = api
            ),
            budget = budget,
            api = api
        )

        val result = resolver.resolve(
            com.romulus.mobile.realdebrid.ExactZipRequest(
                sources = listOf(
                    ProviderSourceRef("magnet:?xt=urn:btih:source", null)
                ),
                exactPath = "Show/archive.zip"
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Exact zip path was not found: Show/archive.zip",
            result.exceptionOrNull()?.message
        )
    }

    private fun selectionId(path: String, sizeBytes: Long?): String = providerSelectionId(
        normalizedPath = normalizeProviderPath(path),
        sizeBytes = sizeBytes,
        occurrenceIndex = 1
    )
}
