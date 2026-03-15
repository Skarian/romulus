package com.romulus.mobile.realdebrid.inventory

import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderInventory
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.TorrentInfoDto
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.captureResult
import com.romulus.mobile.realdebrid.withSelectionIds
import java.util.LinkedHashSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException

internal class TorrentInventoryService(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun enumerate(request: ProviderInventoryRequest): Result<ProviderInventory> =
        captureResult {
            val temporaryTorrentIds = LinkedHashSet<String>()
            val files = request.sources.flatMap { source ->
                val host = budget
                    .run { api.getAvailableHosts() }
                    .firstOrNull()
                    ?.host
                    ?: error("No Real-Debrid hosts are available")
                val addedTorrent = budget.run {
                    api.addMagnet(
                        magnet = source.magnetUri,
                        host = host
                    )
                }
                temporaryTorrentIds += addedTorrent.id
                val info = readTorrentInfoWithRetry(addedTorrent.id)
                info.files.withSelectionIds().map { candidate ->
                    val providerFileId = candidate.selectionId
                    ProviderFileRecord(
                        providerFileId = providerFileId,
                        originalName = candidate.file.path
                            .substringAfterLast('/')
                            .substringAfterLast('\\'),
                        path = candidate.file.path,
                        sizeBytes = candidate.file.bytes,
                        partLabel = source.partLabel,
                        locator = ProviderLocator(
                            sourceMagnetUri = source.magnetUri,
                            torrentId = addedTorrent.id,
                            providerFileIds = listOf(providerFileId),
                            selectedProviderFileId = providerFileId,
                            path = candidate.file.path,
                            partLabel = source.partLabel
                        )
                    )
                }
            }
            temporaryTorrentIds.forEach { torrentId ->
                try {
                    budget.run { api.deleteTorrent(torrentId) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                }
            }
            ProviderInventory(files = files)
        }

    private suspend fun readTorrentInfoWithRetry(torrentId: String): TorrentInfoDto =
        retryInitialInfoRead {
            budget.run { api.getTorrentInfo(torrentId) }
        }

    private suspend fun retryInitialInfoRead(block: suspend () -> TorrentInfoDto): TorrentInfoDto {
        repeat(INITIAL_INFO_MAX_ATTEMPTS - 1) {
            try {
                return block()
            } catch (error: HttpException) {
                if (error.code() != HTTP_NOT_FOUND) {
                    throw error
                }
                delay(INITIAL_INFO_RETRY_DELAY_MILLIS)
            }
        }
        return block()
    }

    private companion object {
        const val INITIAL_INFO_MAX_ATTEMPTS = 3
        const val INITIAL_INFO_RETRY_DELAY_MILLIS = 500L
        const val HTTP_NOT_FOUND = 404
    }
}
