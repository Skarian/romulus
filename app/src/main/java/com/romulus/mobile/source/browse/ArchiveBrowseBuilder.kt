package com.romulus.mobile.source.browse

import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry

internal class ArchiveBrowseBuilder {
    @Suppress("UnusedParameter")
    fun build(snapshotId: SnapshotId, entry: SourceSnapshotEntry): BrowseResult =
        BrowseResult.Failed(
            BrowseFailure.ArchiveResolver("Archive selection is not implemented yet.")
        )
}
