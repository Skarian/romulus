package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ExactZipRequest
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.acquisition.ProviderAcquisitionPoller
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.captureResult
import com.romulus.mobile.realdebrid.inventory.TorrentInventoryService

internal class ExactZipResolver(
    private val inventoryService: TorrentInventoryService,
    private val acquisitionPoller: ProviderAcquisitionPoller,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun resolve(request: ExactZipRequest): Result<ArchiveContainerLocator> = captureResult {
        val inventoryResult = inventoryService.enumerate(
            ProviderInventoryRequest(request.sources)
        )
        val inventory = inventoryResult.getOrThrow()
        val exactMatch = inventory.files.firstOrNull { file ->
            normalizePath(file.path) == normalizePath(request.exactPath)
        } ?: error("Exact zip path was not found: ${request.exactPath}")

        when (
            val acquisitionStatus = acquisitionPoller.start(exactMatch.locator).getOrThrow()
        ) {
            is AcquisitionStatus.Waiting -> {
                error("Exact zip container is not ready yet")
            }

            is AcquisitionStatus.LinksReady -> {
                val matchingLink = acquisitionStatus.readyLinks.singleOrNull()
                    ?: error("Exact zip link mapping was not available")
                val unrestricted = budget.run { api.unrestrictLink(matchingLink.restrictedUrl) }
                ArchiveContainerLocator(
                    archiveUrl = unrestricted.downloadUrl,
                    originalName = unrestricted.filename ?: exactMatch.originalName,
                    providerLocator = exactMatch.locator.withResolvedTorrent(
                        torrentId = acquisitionStatus.resumeMarker.torrentId
                    )
                )
            }
        }
    }

    private fun ProviderLocator.withResolvedTorrent(torrentId: String): ProviderLocator = copy(
        torrentId = torrentId
    )

    private fun normalizePath(path: String): String = path
        .replace('\\', '/')
        .trimStart('/')
}
