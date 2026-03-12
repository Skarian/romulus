package com.romulus.mobile.realdebrid.acquisition

import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ProviderSelectionCandidate
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.captureResult
import com.romulus.mobile.realdebrid.withSelectionIds
import kotlinx.coroutines.delay
import retrofit2.HttpException

internal class ProviderSelectionService(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun start(locator: ProviderLocator): Result<ProviderResumeMarker> = captureResult {
        val host = budget.run { api.getAvailableHosts() }.firstOrNull()
            ?: error("No Real-Debrid hosts are available")
        val addedTorrent = budget.run {
            api.addMagnet(
                magnet = locator.sourceMagnetUri,
                host = host
            )
        }
        val info = readTorrentInfoWithRetry(addedTorrent.id)
        val keyedFiles = info.files.withSelectionIds()
        val selectedSelectionIds = locator.providerFileIds.sorted()
        val selectedFiles = keyedFiles.filter { candidate ->
            candidate.selectionId in selectedSelectionIds
        }
        check(
            selectedFiles
                .map(ProviderSelectionCandidate::selectionId)
                .sorted() == selectedSelectionIds
        ) {
            "Queued provider selection could not be resolved on fresh provider torrent"
        }
        val selectedProviderFileIds = selectedFiles
            .map { candidate -> candidate.file.id.toString() }
            .sorted()
        budget.run {
            api.selectFiles(
                torrentId = addedTorrent.id,
                fileIdsCsv = selectedProviderFileIds.joinToString(",")
            )
        }
        ProviderResumeMarker(
            torrentId = addedTorrent.id,
            sourceMagnetUri = locator.sourceMagnetUri,
            selectedProviderFileIds = selectedProviderFileIds
        )
    }

    suspend fun verify(marker: ProviderResumeMarker): Result<ProviderResumeMarker> = captureResult {
        val expectedSelection = marker.selectedProviderFileIds.sorted()
        repeat(SELECTION_VERIFICATION_ATTEMPTS - 1) {
            val info = budget.run { api.getTorrentInfo(marker.torrentId) }
            failIfTerminalStatus(info.status)
            if (selectedProviderFileIds(info) == expectedSelection) {
                return@captureResult marker
            }
            delay(SELECTION_VERIFICATION_DELAY_MILLIS)
        }
        val finalInfo = budget.run { api.getTorrentInfo(marker.torrentId) }
        failIfTerminalStatus(finalInfo.status)
        val actualSelection = selectedProviderFileIds(finalInfo)
        check(actualSelection == expectedSelection) {
            "Provider selection did not stick for torrent ${marker.torrentId}"
        }
        marker
    }

    private fun selectedProviderFileIds(info: TorrentInfoDto): List<String> = info.files
        .filter { it.selected == SELECTED_FLAG }
        .map { it.id.toString() }
        .sorted()

    private suspend fun readTorrentInfoWithRetry(torrentId: String): TorrentInfoDto {
        repeat(INITIAL_INFO_MAX_ATTEMPTS - 1) {
            try {
                return budget.run { api.getTorrentInfo(torrentId) }
            } catch (error: HttpException) {
                if (error.code() != HTTP_NOT_FOUND) {
                    throw error
                }
                delay(INITIAL_INFO_RETRY_DELAY_MILLIS)
            }
        }
        return budget.run { api.getTorrentInfo(torrentId) }
    }

    private fun failIfTerminalStatus(status: String?) {
        val normalizedStatus = status
            ?.trim()
            ?.lowercase()
            .orEmpty()
        check(normalizedStatus !in TERMINAL_FAILURE_STATUSES) {
            "Provider selection failed with status: ${status ?: "unknown"}"
        }
    }

    private companion object {
        const val SELECTION_VERIFICATION_ATTEMPTS = 5
        const val SELECTION_VERIFICATION_DELAY_MILLIS = 500L
        const val INITIAL_INFO_MAX_ATTEMPTS = 3
        const val INITIAL_INFO_RETRY_DELAY_MILLIS = 500L
        const val HTTP_NOT_FOUND = 404
        const val SELECTED_FLAG = 1
        val TERMINAL_FAILURE_STATUSES = setOf("magnet_error", "error", "virus", "dead")
    }
}
