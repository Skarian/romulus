package com.romulus.spikes.spike3.model

import java.nio.file.Path
import java.time.Instant

enum class RangeMode(val pathSegment: String, val traceLabel: String) {
    FULL("range", "full"),
    NONE("no-range", "none"),
    CAPPED("capped-range", "capped"),
}

enum class FailureStage {
    PROBE,
    RANGE_READ,
    ZIP_ENUMERATION,
    DOWNLOAD,
    FILTER,
    ARTIFACT_WRITE,
}

data class EntryIdentity(
    val fixtureName: String,
    val entryPath: String,
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long?,
)

data class EnumeratedEntry(
    val identity: EntryIdentity,
    val isDirectory: Boolean,
    val compressionMethod: Int,
    val encrypted: Boolean,
)

data class FilteredEntries(
    val ignoredEntries: List<EnumeratedEntry>,
    val visibleEntries: List<EnumeratedEntry>,
    val selectedEntries: List<EnumeratedEntry>,
)

sealed interface ExpectedOutcome {
    data object Enumerate : ExpectedOutcome
    data object DownloadSelected : ExpectedOutcome
    data class Failure(val stage: FailureStage, val errorCode: String) : ExpectedOutcome
}

data class Spike3Case(
    val id: String,
    val fixtureName: String,
    val endpointMode: RangeMode,
    val ignoreGlobs: List<String>,
    val selectedEntries: List<EntryIdentity>,
    val expectedOutcome: ExpectedOutcome,
)

data class CaseFailure(
    val caseId: String,
    val stage: FailureStage,
    val errorCode: String,
    val message: String,
    val causeClass: String?,
)

enum class CaseStatus {
    PASSED,
    FAILED,
}

data class CaseResult(
    val case: Spike3Case,
    val status: CaseStatus,
    val matchedExpectation: Boolean,
    val candidateWritten: Boolean,
    val downloadedFiles: List<Path>,
    val failure: CaseFailure?,
)

data class RunSummary(
    val runId: String,
    val runDirectory: Path,
    val results: List<CaseResult>,
) {
    val allExpectationsMet: Boolean = results.all { it.status == CaseStatus.PASSED && it.matchedExpectation }
}

data class RemoteArchiveInfo(
    val contentLength: Long,
    val acceptsRanges: Boolean,
    val rangeModeHint: String?,
)

data class RangeReadResult(
    val requestedStart: Long,
    val requestedEndInclusive: Long,
    val actualStart: Long,
    val actualEndInclusive: Long,
    val bytes: ByteArray,
)

data class HttpTraceEvent(
    val timestamp: Instant,
    val caseId: String,
    val stage: FailureStage,
    val method: String,
    val path: String,
    val requestRange: String?,
    val responseCode: Int?,
    val contentLength: Long?,
    val contentRange: String?,
    val elapsedMillis: Long,
    val failureMessage: String?,
)
