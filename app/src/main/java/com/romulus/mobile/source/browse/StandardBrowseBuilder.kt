package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ProviderInventory
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderSourceRef
import com.romulus.mobile.source.ingest.isWithinScope
import com.romulus.mobile.source.ingest.matchesIgnoreRules
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import java.nio.charset.StandardCharsets
import java.util.UUID

private typealias ProviderInventoryEnumerator =
    suspend (ProviderInventoryRequest) -> Result<ProviderInventory>

internal class StandardBrowseBuilder(
    private val enumerateProviderFiles: ProviderInventoryEnumerator
) {
    suspend fun build(snapshotId: SnapshotId, entry: SourceSnapshotEntry): BrowseResult {
        val inventory = enumerateProviderFiles(
            ProviderInventoryRequest(
                sources = entry.torrents.map { torrent ->
                    ProviderSourceRef(
                        magnetUri = torrent.magnetUri,
                        partLabel = torrent.partLabel
                    )
                }
            )
        ).getOrElse { error ->
            return BrowseResult.Failed(
                BrowseFailure.StandardResolver(
                    error.message ?: "Source files could not be resolved."
                )
            )
        }

        val items = inventory.files
            .filter { file -> file.isWithinScope(entry.normalizedPath) }
            .filterNot { file -> file.originalName.matchesIgnoreRules(entry.ignoreGlobs) }
            .sortedBy { file -> file.originalName.lowercase() }
            .map { file ->
                SelectableItem.StandardFile(
                    itemId = SelectableItemId(
                        "${entry.entryId.value}:${stableItemId(entry, file)}"
                    ),
                    snapshotId = snapshotId,
                    entryId = entry.entryId,
                    originalDisplayName = file.originalName,
                    sizeBytes = file.sizeBytes,
                    selectionPolicy = SelectionPolicy(
                        renameRule = entry.renameRule,
                        renameAvailable = entry.renameRule != null,
                        unarchiveToggleVisible = entry.unarchiveConfigured,
                        unarchiveDefault = entry.unarchiveConfigured && entry.unarchiveDefault,
                        recursiveToggleVisible = entry.recursiveConfigured,
                        recursiveUnarchiveDefault = entry.recursiveConfigured &&
                            entry.recursiveUnarchiveDefault
                    ),
                    sourceContext = SelectableItemSourceContext(
                        entryDisplayName = entry.displayName,
                        outputSubfolder = entry.subfolder,
                        partLabel = file.partLabel,
                        providerFileId = file.providerFileId
                    ),
                    providerLocator = file.locator
                )
            }

        return BrowseResult.Loaded(mode = BrowseMode.STANDARD, items = items)
    }

    private fun stableItemId(
        entry: SourceSnapshotEntry,
        file: com.romulus.mobile.realdebrid.ProviderFileRecord
    ): String {
        val seed = listOf(
            entry.entryId.value,
            file.locator.sourceMagnetUri,
            file.path,
            file.locator.selectedProviderFileId
        ).joinToString("|")
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
