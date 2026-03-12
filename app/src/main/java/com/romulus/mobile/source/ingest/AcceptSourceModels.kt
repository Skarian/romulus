package com.romulus.mobile.source.ingest

import com.romulus.mobile.source.snapshot.SnapshotId
import kotlinx.serialization.Serializable

@Serializable
enum class SourceMode {
    URL,
    FILE
}

@Serializable
data class RenameRule(val pattern: String, val replacement: String)

data class AcceptSourceCommand(
    val mode: SourceMode,
    val rawValue: String,
    val persistedUri: String?
)

sealed interface SourceValidationIssue {
    data class InvalidVersion(val found: Int) : SourceValidationIssue

    data class InvalidSubfolder(val subfolder: String) : SourceValidationIssue

    data class InvalidPath(val path: String) : SourceValidationIssue

    data class InvalidIgnoreRule(val pattern: String) : SourceValidationIssue

    data class InvalidRenameRule(val message: String) : SourceValidationIssue

    data class InvalidRecursiveUnarchive(val message: String) : SourceValidationIssue
}

sealed interface AcceptSourceResult {
    data class Accepted(val snapshotId: SnapshotId) : AcceptSourceResult

    data class Rejected(val issues: List<SourceValidationIssue>) : AcceptSourceResult

    data class Failed(val message: String) : AcceptSourceResult
}
