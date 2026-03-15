package com.romulus.mobile.source.browse

import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.ExtractionLayoutPolicy
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.snapshot.UnarchivePolicy
import com.romulus.mobile.source.torrentmeta.TorrentFileSelectionIntent
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class SelectableItemId(val value: String)

data class SelectionPolicy(
    val renameRule: RenameRule?,
    val renameAvailable: Boolean,
    val unarchivePolicy: UnarchivePolicy?
)

val SelectionPolicy.defaultExtractionLayout: ExtractionLayoutPolicy
    get() = unarchivePolicy?.layout ?: ExtractionLayoutPolicy(
        mode = com.romulus.mobile.source.snapshot.ExtractionLayoutMode.FLAT
    )

data class SelectableItemSourceContext(
    val entryDisplayName: String,
    val outputSubfolder: String,
    val partLabel: String?,
    val providerFileId: String?
)

sealed interface SelectableItem {
    val itemId: SelectableItemId
    val snapshotId: SnapshotId
    val entryId: SourceEntryId
    val originalDisplayName: String
    val sizeBytes: Long?
    val selectionPolicy: SelectionPolicy
    val sourceContext: SelectableItemSourceContext

    data class StandardFile(
        override val itemId: SelectableItemId,
        override val snapshotId: SnapshotId,
        override val entryId: SourceEntryId,
        override val originalDisplayName: String,
        override val sizeBytes: Long?,
        override val selectionPolicy: SelectionPolicy,
        override val sourceContext: SelectableItemSourceContext,
        val selectionIntent: TorrentFileSelectionIntent
    ) : SelectableItem

    data class ArchiveEntry(
        override val itemId: SelectableItemId,
        override val snapshotId: SnapshotId,
        override val entryId: SourceEntryId,
        override val originalDisplayName: String,
        override val sizeBytes: Long?,
        override val selectionPolicy: SelectionPolicy,
        override val sourceContext: SelectableItemSourceContext,
        val preparationKey: ArchivePreparationKey,
        val archiveEntryIdentity: ArchiveEntryIdentity
    ) : SelectableItem
}

data class BrowseRequest(val snapshotId: SnapshotId, val entryId: SourceEntryId)

sealed interface BrowseResult {
    data class Loaded(val mode: BrowseMode, val items: List<SelectableItem>) : BrowseResult

    data class Preparing(
        val mode: BrowseMode,
        val statusLabel: String?,
        val progressPercent: Double?,
        val timeoutAtEpochMillis: Long
    ) : BrowseResult

    data class Failed(val failure: BrowseFailure) : BrowseResult
}

enum class BrowseMode {
    STANDARD,
    ARCHIVE_SELECTION
}

sealed interface BrowseFailure {
    data class MissingEntry(val entryId: SourceEntryId) : BrowseFailure

    data class StandardResolver(val message: String) : BrowseFailure

    data class ArchiveResolver(val message: String) : BrowseFailure

    data class ArchiveEnumeration(val message: String) : BrowseFailure
}
