package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderInventory
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderSourceRef
import com.romulus.mobile.realdebrid.normalizeProviderPath
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.torrentmeta.TorrentFileSelectionIntent
import com.romulus.mobile.source.torrentmeta.TorrentMetadataFileRecord
import com.romulus.mobile.source.torrentmeta.TorrentMetadataInventory

private typealias ProviderInventoryEnumerator =
    suspend (ProviderInventoryRequest) -> Result<ProviderInventory>

internal class CachedStandardBrowseInventoryService(
    private val cacheStore: StandardBrowseInventoryCacheStore,
    private val enumerateProviderFiles: ProviderInventoryEnumerator
) {
    suspend fun load(
        snapshotId: SnapshotId,
        entry: SourceSnapshotEntry
    ): Result<TorrentMetadataInventory> {
        cacheStore.read(snapshotId, entry.entryId)?.let { cached ->
            return Result.success(cached)
        }

        val inventoryResult = enumerateProviderFiles(
            ProviderInventoryRequest(
                sources = entry.torrents.map { source ->
                    ProviderSourceRef(
                        magnetUri = source.magnetUri,
                        partLabel = source.partLabel
                    )
                }
            )
        ).map { providerInventory ->
            providerInventory.toTorrentMetadataInventory()
        }

        inventoryResult.onSuccess { inventory ->
            cacheStore.write(snapshotId, entry.entryId, inventory)
        }
        return inventoryResult
    }
}

private fun ProviderInventory.toTorrentMetadataInventory(): TorrentMetadataInventory {
    val occurrences = mutableMapOf<Pair<String, Long?>, Int>()
    return TorrentMetadataInventory(
        files = files.map { file ->
            file.toTorrentMetadataFileRecord(occurrences)
        }
    )
}

private fun ProviderFileRecord.toTorrentMetadataFileRecord(
    occurrences: MutableMap<Pair<String, Long?>, Int>
): TorrentMetadataFileRecord {
    val normalizedPath = normalizeProviderPath(path)
    val occurrenceKey = normalizedPath to sizeBytes
    val occurrenceIndex = occurrences.getOrDefault(occurrenceKey, 0) + 1
    occurrences[occurrenceKey] = occurrenceIndex
    return TorrentMetadataFileRecord(
        originalName = originalName,
        path = "/$normalizedPath",
        sizeBytes = sizeBytes,
        partLabel = partLabel,
        selectionIntent = TorrentFileSelectionIntent(
            sourceMagnetUri = locator.sourceMagnetUri,
            normalizedPath = normalizedPath,
            sizeBytes = sizeBytes,
            occurrenceIndex = occurrenceIndex
        )
    )
}
