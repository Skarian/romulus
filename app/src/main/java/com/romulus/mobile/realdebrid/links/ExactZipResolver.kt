package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ExactZipRequest
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.acquisition.ProviderAcquisitionPoller
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.inventory.TorrentInventoryService

@Suppress("UnusedParameter", "UnusedPrivateProperty")
internal class ExactZipResolver(
    private val inventoryService: TorrentInventoryService,
    private val acquisitionPoller: ProviderAcquisitionPoller,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun resolve(request: ExactZipRequest): Result<ArchiveContainerLocator> = budget.run {
        Result.failure(UnsupportedOperationException("ExactZipResolver is not wired yet"))
    }
}
