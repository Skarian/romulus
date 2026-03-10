package com.romulus.mobile.realdebrid

import com.romulus.mobile.realdebrid.acquisition.ProviderAcquisitionPoller
import com.romulus.mobile.realdebrid.acquisition.ProviderSelectionService
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.inventory.TorrentInventoryService
import com.romulus.mobile.realdebrid.links.ExactZipResolver
import com.romulus.mobile.realdebrid.links.UnrestrictedLinkResolver
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDebridInternalScaffoldTest {
    private val budget = RequestBudget(Clock.systemUTC())
    private val api = object : RealDebridApi {}

    @Test
    fun requestBudgetRunsImmediatelyWithoutRetryWindow() = runTest {
        val value = budget.run { 7 }

        assertEquals(7, value)
    }

    @Test
    fun internalServicesFailExplicitlyUntilWired() = runTest {
        val inventoryService = TorrentInventoryService(budget = budget, api = api)
        val selectionService = ProviderSelectionService(budget = budget, api = api)
        val acquisitionPoller = ProviderAcquisitionPoller(
            selectionService = selectionService,
            budget = budget,
            api = api
        )
        val linkResolver = UnrestrictedLinkResolver(budget = budget, api = api)
        val exactZipResolver = ExactZipResolver(
            inventoryService = inventoryService,
            acquisitionPoller = acquisitionPoller,
            budget = budget,
            api = api
        )

        val inventoryResult = inventoryService.enumerate(ProviderInventoryRequest(emptyList()))
        val startResult = acquisitionPoller.start(
            ProviderLocator(
                sourceMagnetUri = "magnet:?xt=urn:btih:test",
                torrentId = "torrent",
                providerFileIds = listOf("file-1"),
                selectedProviderFileId = "file-1",
                path = "folder/file.mkv",
                partLabel = null
            )
        )
        val resolveResult = linkResolver.resolve(
            ProviderReadyLink(
                providerFileId = "file-1",
                restrictedUrl = "https://example.com/restricted"
            )
        )
        val exactZipResult = exactZipResolver.resolve(
            ExactZipRequest(
                sources = emptyList(),
                exactPath = "folder/archive.zip"
            )
        )

        assertTrue(inventoryResult.isFailure)
        assertTrue(startResult.isFailure)
        assertTrue(resolveResult.isFailure)
        assertTrue(exactZipResult.isFailure)
        assertEquals(
            "TorrentInventoryService is not wired yet",
            inventoryResult.exceptionOrNull()?.message
        )
    }
}
