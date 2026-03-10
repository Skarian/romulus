package com.romulus.mobile.source.snapshot

import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.ingest.SourceMode
import java.time.Instant

@JvmInline
value class SnapshotId(val value: String)

@JvmInline
value class SourceEntryId(val value: String)

data class SourceTorrentRef(val magnetUri: String, val partLabel: String?)

data class SourceSnapshotEntry(
    val entryId: SourceEntryId,
    val displayName: String,
    val subfolder: String,
    val torrents: List<SourceTorrentRef>,
    val normalizedPath: String,
    val ignoreGlobs: List<String>,
    val renameRule: RenameRule?,
    val unarchiveConfigured: Boolean,
    val unarchiveDefault: Boolean,
    val recursiveConfigured: Boolean,
    val recursiveUnarchiveDefault: Boolean
)

data class SourceSnapshot(
    val snapshotId: SnapshotId,
    val acceptedAt: Instant,
    val entries: List<SourceSnapshotEntry>
)

enum class SourceRefreshOutcome {
    ACCEPTED,
    RETAINED_PRIOR,
    FAILED_WITHOUT_SNAPSHOT
}

data class AcceptedSourceSummary(
    val mode: SourceMode,
    val rawValue: String,
    val persistedUri: String?,
    val lastRefreshOutcome: SourceRefreshOutcome?
)

data class SourceReadiness(
    val acceptedMode: SourceMode?,
    val isUsable: Boolean,
    val hasUsableSnapshot: Boolean,
    val brokenReason: String?
)

sealed interface HomeSourceState {
    data class SourceLoadError(val message: String, val refreshAvailable: Boolean) :
        HomeSourceState

    data class Content(
        val snapshotId: SnapshotId,
        val rows: List<HomeSourceRow>,
        val warning: HomeSourceWarning?,
        val refreshAvailable: Boolean
    ) : HomeSourceState
}

sealed interface HomeSourceWarning {
    data class LatestRefreshFailed(val message: String) : HomeSourceWarning

    data class MissingSnapshotFallback(val message: String) : HomeSourceWarning
}

data class HomeSourceRow(
    val entryId: SourceEntryId,
    val displayName: String,
    val folderContext: String
)

sealed interface SourceRefreshTrigger {
    data object HomeManualRefresh : SourceRefreshTrigger

    data object ColdLaunch : SourceRefreshTrigger

    data object SettingsSave : SourceRefreshTrigger
}

sealed interface SourceRefreshResult {
    data class Replaced(val snapshotId: SnapshotId) : SourceRefreshResult

    data class RetainedPrior(val priorSnapshotId: SnapshotId, val message: String) :
        SourceRefreshResult

    data class FailedWithoutSnapshot(val message: String) : SourceRefreshResult
}
