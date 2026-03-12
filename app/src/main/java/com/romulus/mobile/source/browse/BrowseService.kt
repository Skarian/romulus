package com.romulus.mobile.source.browse

import com.romulus.mobile.source.ingest.isArchiveSelectionPath
import com.romulus.mobile.source.snapshot.SnapshotStore

internal class BrowseService(
    private val snapshotStore: SnapshotStore,
    private val standardBrowseBuilder: StandardBrowseBuilder,
    private val archiveBrowseBuilder: ArchiveBrowseBuilder
) {
    suspend fun load(request: BrowseRequest): BrowseResult {
        val snapshot = snapshotStore.read(request.snapshotId)
        val entry = snapshot?.entries?.firstOrNull { it.entryId == request.entryId }

        return when {
            snapshot == null -> BrowseResult.Failed(BrowseFailure.MissingEntry(request.entryId))
            entry == null -> BrowseResult.Failed(BrowseFailure.MissingEntry(request.entryId))
            isArchiveSelectionPath(entry.normalizedPath) -> {
                archiveBrowseBuilder.build(snapshot.snapshotId, entry)
            }

            else -> {
                standardBrowseBuilder.build(snapshot.snapshotId, entry)
            }
        }
    }
}
