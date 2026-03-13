package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ExactZipRequest
import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.captureResult
import com.romulus.mobile.realdebrid.inventory.TorrentInventoryService

internal class ExactZipResolver(
    private val inventoryService: TorrentInventoryService,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun findMatch(request: ExactZipRequest): Result<ProviderFileRecord> = captureResult {
        val inventoryResult = inventoryService.enumerate(
            ProviderInventoryRequest(request.sources)
        )
        val inventory = inventoryResult.getOrThrow()
        inventory.files.firstOrNull { file ->
            normalizePath(file.path) == normalizePath(request.exactPath)
        } ?: error("Exact zip path was not found: ${request.exactPath}")
    }

    suspend fun materialize(
        exactMatch: ProviderFileRecord,
        acquisitionStatus: AcquisitionStatus.LinksReady
    ): Result<ArchiveContainerLocator> = captureResult {
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

    private fun ProviderLocator.withResolvedTorrent(torrentId: String): ProviderLocator = copy(
        torrentId = torrentId
    )

    private fun normalizePath(path: String): String = path
        .replace('\\', '/')
        .trimStart('/')
}
