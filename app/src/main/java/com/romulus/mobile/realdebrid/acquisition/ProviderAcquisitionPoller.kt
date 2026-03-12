package com.romulus.mobile.realdebrid.acquisition

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ProviderSelectionRequest
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.captureResult

internal class ProviderAcquisitionPoller(
    private val selectionService: ProviderSelectionService,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun start(request: ProviderSelectionRequest): Result<AcquisitionStatus> =
        captureResult {
            val marker = selectionService.start(request).getOrThrow()
            val verifiedMarker = selectionService.verify(marker).getOrThrow()
            inspect(verifiedMarker)
        }

    suspend fun start(locator: ProviderLocator): Result<AcquisitionStatus> = captureResult {
        val marker = selectionService.start(locator).getOrThrow()
        val verifiedMarker = selectionService.verify(marker).getOrThrow()
        inspect(verifiedMarker)
    }

    suspend fun resume(marker: ProviderResumeMarker): Result<AcquisitionStatus> = captureResult {
        val verifiedMarker = selectionService.verify(marker).getOrThrow()
        inspect(verifiedMarker)
    }

    private suspend fun inspect(marker: ProviderResumeMarker): AcquisitionStatus {
        val info = budget.run { api.getTorrentInfo(marker.torrentId) }
        val normalizedStatus = info.status
            ?.trim()
            ?.lowercase()
            .orEmpty()
        check(normalizedStatus !in TERMINAL_FAILURE_STATUSES) {
            "Provider acquisition failed with status: ${info.status ?: "unknown"}"
        }

        val readyLinks = readyLinks(info, marker)
        return if (readyLinks.isEmpty()) {
            AcquisitionStatus.Waiting(
                statusLabel = info.status ?: "unknown",
                progressPercent = info.progress,
                resumeMarker = marker
            )
        } else {
            AcquisitionStatus.LinksReady(
                resumeMarker = marker,
                readyLinks = readyLinks
            )
        }
    }

    private fun readyLinks(
        info: TorrentInfoDto,
        marker: ProviderResumeMarker
    ): List<ProviderReadyLink> {
        val selectedFiles = info.files.filter { file ->
            file.selected == SELECTED_FLAG &&
                marker.selectedProviderFileIds.contains(file.id.toString())
        }
        val selectedFileLinks = selectedFiles.mapNotNull { file ->
            val candidateLinks = listOfNotNull(file.link, file.unrestrictedLink)
            val fallbackReadyLink = candidateLinks.firstOrNull { it.isNotBlank() }
            fallbackReadyLink?.let(::ProviderReadyLink)
        }
        val distinctSelectedFileLinks = selectedFileLinks.distinct()
        if (distinctSelectedFileLinks.isNotEmpty()) {
            return distinctSelectedFileLinks
        }

        val providerReadyLinks = info.links
            .filter(String::isNotBlank)
            .map(::ProviderReadyLink)
            .distinct()
        check(providerReadyLinks.size <= 1 || selectedFiles.size > 1) {
            "Provider returned multiple ready links for one selected file"
        }
        return providerReadyLinks
    }

    private companion object {
        const val SELECTED_FLAG = 1
        val TERMINAL_FAILURE_STATUSES = setOf("magnet_error", "error", "virus", "dead")
    }
}
