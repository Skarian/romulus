package com.romulus.mobile.source.browse

import com.romulus.mobile.source.ingest.isWithinScope
import com.romulus.mobile.source.ingest.matchesIgnoreRules
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.torrentmeta.TorrentMetadataFileRecord
import com.romulus.mobile.source.torrentmeta.TorrentMetadataInventory
import java.nio.charset.StandardCharsets
import java.util.UUID

private typealias TorrentMetadataEnumerator =
    suspend (SnapshotId, SourceSnapshotEntry) -> Result<TorrentMetadataInventory>

internal class StandardBrowseBuilder(
    private val enumerateTorrentMetadata: TorrentMetadataEnumerator
) {
    suspend fun build(snapshotId: SnapshotId, entry: SourceSnapshotEntry): BrowseResult {
        val inventory = enumerateTorrentMetadata(snapshotId, entry).getOrElse { error ->
            return BrowseResult.Failed(
                BrowseFailure.StandardResolver(
                    error.message ?: "Source files could not be resolved."
                )
            )
        }

        val items = inventory.files
            .filter { file -> file.path.isWithinScope(entry.normalizedPath) }
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
                        providerFileId = null
                    ),
                    selectionIntent = file.selectionIntent
                )
            }

        return BrowseResult.Loaded(mode = BrowseMode.STANDARD, items = items)
    }

    private fun stableItemId(entry: SourceSnapshotEntry, file: TorrentMetadataFileRecord): String {
        val seed = listOf(
            entry.entryId.value,
            file.selectionIntent.sourceMagnetUri,
            file.selectionIntent.normalizedPath,
            file.selectionIntent.sizeBytes?.toString() ?: "unknown",
            file.selectionIntent.occurrenceIndex.toString()
        ).joinToString("|")
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
