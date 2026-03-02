package com.romulus.mobile.domain.source

import kotlinx.serialization.Serializable

@Serializable
enum class SourceMode {
    FILE,
    URL
}

@Serializable
data class SourceTorrent(
    val partIndex: Int,
    val url: String,
    val partName: String?
)

@Serializable
data class RenameRule(
    val pattern: String,
    val replacement: String
)

@Serializable
data class SourceEntry(
    val index: Int,
    val displayName: String,
    val subfolder: String,
    val torrents: List<SourceTorrent>,
    val rename: RenameRule?,
    val ignoreGlobs: List<String>
)

@Serializable
data class SourceIssue(
    val entryIndex: Int?,
    val message: String
)

@Serializable
data class SourceSnapshot(
    val snapshotId: String,
    val sourceMode: SourceMode,
    val sourceValue: String,
    val generatedAtEpochMs: Long,
    val entries: List<SourceEntry>,
    val issues: List<SourceIssue>,
    val stale: Boolean
)

sealed interface RefreshResult {
    data class Success(val snapshot: SourceSnapshot) : RefreshResult

    data class Failed(val message: String, val usedCachedSnapshot: Boolean) : RefreshResult
}
