package com.romulus.spikes.spike1.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScopeKind {
    ROOT,
    DIRECTORY,
    EXACT_PATH,
}

@Serializable
enum class RunStatus {
    PASS,
    FAIL,
    EXPECTED_FAILURE,
    BLOCKED,
    SKIPPED_DEPENDENCY,
}

@Serializable
enum class MatrixGateStatus {
    PASS,
    FAIL,
    BLOCKED,
}

@Serializable
enum class Spike1Profile {
    LIKELY_CACHED,
    KNOWN_UNCACHED,
}

@Serializable
enum class KnownUncachedStatus {
    IN_PROGRESS,
    PASS,
    TERMINAL_FAILURE,
    INCOMPLETE,
}

@Serializable
enum class EvidenceStatus {
    ANSWERED,
    UNCONFIRMED,
    UNOBSERVED,
}

@Serializable
enum class LinkMappingStatus {
    EXACT_COUNT_PROVIDER_ORDER,
    COUNT_MISMATCH,
}

@Serializable
data class RunCase(
    val id: String,
    val specLabel: String,
    val executionOrder: Int,
    val scopeKind: ScopeKind,
    val scopePath: String? = null,
    val desiredPaths: List<String> = emptyList(),
    val downloadsSelectedFiles: Boolean = false,
    val deterministicFailure: Boolean = false,
)

@Serializable
data class MatrixPlan(
    val runs: List<RunCase>,
)

@Serializable
data class SelectionResolution(
    val scopeKind: ScopeKind,
    val scopePath: String? = null,
    val desiredPaths: List<String>,
    val candidateFiles: List<TorrentFileDto>,
    val resolvedFiles: List<TorrentFileDto>,
    val payload: String,
    val usesAllLiteral: Boolean,
)

@Serializable
data class LinkMappingEntry(
    val fileId: Int,
    val path: String,
    val restrictedLink: String,
)

@Serializable
data class UnrestrictCallRecord(
    val restrictedLink: String,
    val responseFilename: String,
    val downloadUrl: String,
    val fileSize: Long? = null,
)

@Serializable
data class DownloadManifestEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class TraceEvent(
    val timestamp: String,
    val stage: String,
    val method: String? = null,
    val endpoint: String? = null,
    val statusCode: Int? = null,
    val providerCode: Int? = null,
    val summary: String,
)

@Serializable
data class PollingSummary(
    val attempts: Int,
    val elapsedMillis: Long,
    val lastObservedStatus: String? = null,
    val firstNonPreselectionStatus: String? = null,
    val firstNonPreselectionElapsedMillis: Long? = null,
    val firstReturnedLinksElapsedMillis: Long? = null,
)

@Serializable
data class ProviderAcquisitionSample(
    val timestamp: String,
    val status: String? = null,
    val progress: Double? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
    val ended: String? = null,
    val linkCount: Int = 0,
)

@Serializable
data class LifecycleMarker(
    val timestamp: String,
    val event: String,
    val note: String? = null,
)

@Serializable
data class RunOutcome(
    val runId: String,
    val specLabel: String,
    val executionOrder: Int,
    val status: RunStatus,
    val message: String,
    val torrentId: String? = null,
    val selectedFileIds: List<Int> = emptyList(),
    val selectedPaths: List<String> = emptyList(),
    val providerSelectedFileIdsAfterSelection: List<Int> = emptyList(),
    val providerSelectedPathsAfterSelection: List<String> = emptyList(),
    val candidateFileCount: Int = 0,
    val selectionPayload: String? = null,
    val selectionUsedAllLiteral: Boolean = false,
    val observedStatuses: List<String> = emptyList(),
    val linkCount: Int = 0,
    val unrestrictCallCount: Int = 0,
    val downloadCount: Int = 0,
    val reusedPriorTorrent: Boolean = false,
    val mismatchedLinkMapping: Boolean = false,
    val linkMappingStatus: LinkMappingStatus? = null,
    val blockerCode: String? = null,
    val httpStatus: Int? = null,
    val providerCode: Int? = null,
    val transientFailureObserved: Boolean = false,
)

@Serializable
data class EvidenceAnswer(
    val id: String,
    val question: String,
    val status: EvidenceStatus,
    val answer: String,
)

@Serializable
data class MatrixSummary(
    val startedAt: String,
    val finishedAt: String,
    val gateStatus: MatrixGateStatus,
    val blockerCode: String? = null,
    val blockerMessage: String? = null,
    val artifactRoot: String,
    val envFile: String,
    val runOrder: List<String>,
    val runOutcomes: List<RunOutcome>,
    val evidenceQuestions: List<EvidenceAnswer>,
)

@Serializable
data class KnownUncachedSummary(
    val startedAt: String,
    val finishedAt: String,
    val status: KnownUncachedStatus,
    val artifactRoot: String,
    val envFile: String,
    val torrentId: String? = null,
    val selectedPath: String,
    val selectedFileIds: List<Int> = emptyList(),
    val observedStatuses: List<String> = emptyList(),
    val lastObservedStatus: String? = null,
    val lastProgress: Double? = null,
    val lastSpeed: Long? = null,
    val lastSeeders: Int? = null,
    val firstReturnedLinksAt: String? = null,
    val totalSamples: Int = 0,
    val sessionPollAttempts: Int = 0,
    val message: String,
)

@Serializable
data class KnownUncachedCheckpoint(
    val invocationDir: String,
    val torrentId: String,
    val magnetHash: String,
    val selectedPath: String,
    val selectedFileIds: List<Int>,
    val selectionPayload: String,
    val startedAt: String,
    val lastUpdatedAt: String,
    val host: String,
    val firstReturnedLinksAt: String? = null,
)

@Serializable
data class DeterministicFailureRecord(
    val invalidMagnet: String,
    val cachedHost: String,
    val httpStatus: Int,
    val providerCode: Int? = null,
    val providerMessage: String? = null,
)
