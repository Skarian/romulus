package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.remotezip.ArchiveEntryDescriptor
import com.romulus.mobile.source.ingest.matchesIgnoreRules
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import java.nio.charset.StandardCharsets
import java.util.UUID

internal class ArchiveBrowseBuilder(
    private val loadArchiveBrowse: suspend (
        SnapshotId,
        SourceSnapshotEntry
    ) -> Result<ArchiveBrowseLoadResult>
) {
    @Suppress("ChainMethodContinuation", "LongMethod", "ReturnCount")
    suspend fun build(snapshotId: SnapshotId, entry: SourceSnapshotEntry): BrowseResult {
        val archiveBrowse = loadArchiveBrowse(snapshotId, entry).getOrElse { failure ->
            return BrowseResult.Failed(
                when (failure) {
                    is ArchiveBrowseEnumerationException -> BrowseFailure.ArchiveEnumeration(
                        failure.message ?: "Archive entries could not be enumerated."
                    )

                    else -> BrowseFailure.ArchiveResolver(
                        failure.message ?: "Exact archive could not be resolved."
                    )
                }
            )
        }

        when (archiveBrowse) {
            is ArchiveBrowseLoadResult.Preparing -> {
                return BrowseResult.Preparing(
                    mode = BrowseMode.ARCHIVE_SELECTION,
                    statusLabel = archiveBrowse.statusLabel,
                    progressPercent = archiveBrowse.progressPercent,
                    timeoutAtEpochMillis = archiveBrowse.timeoutAtEpochMillis
                )
            }

            is ArchiveBrowseLoadResult.Ready -> {
                return loadedResult(
                    snapshotId = snapshotId,
                    entry = entry,
                    outerZip = archiveBrowse.outerZip,
                    remoteZip = archiveBrowse.entries
                )
            }
        }
    }

    @Suppress("ChainMethodContinuation")
    private fun loadedResult(
        snapshotId: SnapshotId,
        entry: SourceSnapshotEntry,
        outerZip: ArchiveContainerLocator,
        remoteZip: com.romulus.mobile.remotezip.EnumeratedRemoteZip
    ): BrowseResult.Loaded {
        val archiveComparator = compareBy<ArchiveEntryDescriptor>(
            { descriptor -> descriptor.entryPath.substringAfterLast('/').lowercase() },
            { descriptor -> descriptor.entryPath.lowercase() }
        )
        val items = remoteZip.entries
            .filterNot { descriptor ->
                descriptor.entryPath
                    .substringAfterLast('/')
                    .matchesIgnoreRules(entry.ignoreGlobs)
            }
            .sortedWith(archiveComparator)
            .map { descriptor ->
                val displayName = descriptor.entryPath.substringAfterLast('/').ifBlank {
                    descriptor.entryPath
                }
                SelectableItem.ArchiveEntry(
                    itemId = SelectableItemId(
                        "${entry.entryId.value}:${stableItemId(entry, descriptor)}"
                    ),
                    snapshotId = snapshotId,
                    entryId = entry.entryId,
                    originalDisplayName = displayName,
                    sizeBytes = descriptor.sizeBytes,
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
                        partLabel = outerZip.providerLocator.partLabel,
                        providerFileId = outerZip.providerLocator.selectedProviderFileId
                    ),
                    preparationKey = ArchivePreparationKey(
                        snapshotId = snapshotId,
                        entryId = entry.entryId
                    ),
                    archiveEntryIdentity = descriptor.identity
                )
            }

        return BrowseResult.Loaded(
            mode = BrowseMode.ARCHIVE_SELECTION,
            items = items
        )
    }

    private fun stableItemId(
        entry: SourceSnapshotEntry,
        descriptor: ArchiveEntryDescriptor
    ): String {
        val seed = listOf(
            entry.entryId.value,
            descriptor.identity.normalizedPath,
            descriptor.identity.localHeaderOffset.toString(),
            descriptor.identity.compressedSize.toString(),
            descriptor.identity.uncompressedSize.toString(),
            descriptor.identity.crc32.toString()
        ).joinToString("|")
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
