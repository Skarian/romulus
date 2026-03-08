package com.romulus.spikes.spike4

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.sf.sevenzipjbinding.SevenZipException

val spike4Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

fun nowUtc(): String = Instant.now().toString()

fun File.invariantRelativeTo(base: File): String = relativeTo(base).path.replace(File.separatorChar, '/')

fun Throwable.renderStackTrace(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString()
}

fun SevenZipException.renderExtendedStackTrace(): String {
    val writer = StringWriter()
    printStackTraceExtended(PrintWriter(writer))
    return writer.toString()
}

fun Throwable.renderSummary(): String = buildString {
    append(this@renderSummary::class.java.name)
    val detail = message?.trim().orEmpty()
    if (detail.isNotEmpty()) {
        append(": ")
        append(detail)
    }
}

@Serializable
enum class Spike4Stage {
    RESOLVER,
    PROVIDER_ACQUISITION,
    LINK_HANDLING,
    PROBE,
    RANGE_READ,
    ZIP_ENUMERATION,
    ARCHIVE_SELECTION,
    LOCAL_COPY,
    PRE_UNARCHIVE,
    UNARCHIVE,
    CLEANUP,
    RENAME,
    DIAGNOSTICS,
    ARTIFACT_WRITE,
}

@Serializable
enum class RunExpectation {
    SUCCESS,
    EXPECTED_FAILURE,
}

@Serializable
enum class RunStatusCode {
    PASSED,
    EXPECTED_FAILURE_OBSERVED,
    FAILED,
    BLOCKED,
}

@Serializable
enum class CleanupActionType {
    DELETED,
    RETAINED,
}

@Serializable
data class RenameRule(
    val pattern: String,
    val replacement: String,
)

@Serializable
data class Spike4RunDefinition(
    val id: String,
    val title: String,
    val description: String,
    val unarchiveEnabled: Boolean,
    val selectedInternalPaths: List<String>,
    val expectedOutcome: RunExpectation,
    val expectedFailureStage: Spike4Stage? = null,
)

@Serializable
data class DeviceMetadata(
    val manufacturer: String,
    val model: String,
    val product: String,
    val androidRelease: String,
    val sdkInt: Int,
    val abis: List<String>,
)

@Serializable
data class RuntimeInitRecord(
    val capturedAtUtc: String,
    val initializedSuccessfully: Boolean,
    val sevenZipVersion: String?,
    val sevenZipVersionDisplay: String?,
    val sevenZipJBindingVersion: String?,
    val usedPlatform: String?,
    val lastInitializationException: String?,
    val initializationProbeError: String?,
)

@Serializable
data class RunStatusRecord(
    val runId: String,
    val status: RunStatusCode,
    val summary: String,
)

@Serializable
data class SessionRecord(
    val sessionId: String,
    val startedAtUtc: String,
    val finishedAtUtc: String,
    val selectedDeviceSerial: String?,
    val appId: String,
    val device: DeviceMetadata,
    val runtime: RuntimeInitRecord,
    val runs: List<RunStatusRecord>,
)

@Serializable
data class PollingSummary(
    val attempts: Int,
    val elapsedMillis: Long,
    val lastObservedStatus: String?,
    val firstReturnedLinksElapsedMillis: Long?,
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
data class EntryIdentity(
    val archiveSource: String,
    val entryPath: String,
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long?,
)

@Serializable
data class EnumeratedEntry(
    val identity: EntryIdentity,
    val isDirectory: Boolean,
    val compressionMethod: Int,
    val encrypted: Boolean,
)

@Serializable
data class FilteredEntries(
    val ignoredEntries: List<EnumeratedEntry>,
    val visibleEntries: List<EnumeratedEntry>,
    val selectedEntries: List<EnumeratedEntry>,
)

@Serializable
data class QueueTaskRecord(
    val taskId: String,
    val entryIdentity: EntryIdentity,
    val originalDisplayName: String,
    val stagedArchiveRelativePath: String,
    val destinationSubfolder: String,
)

@Serializable
data class ResolverRecord(
    val runId: String,
    val host: String,
    val torrentId: String,
    val exactZipPath: String,
    val selectedProviderFileIds: List<Int>,
    val selectedProviderPaths: List<String>,
    val selectionPayload: String,
    val restrictedLinks: List<String>,
    val unrestrictedDownloadUrl: String,
    val pollingSummary: PollingSummary,
    val traceEvents: List<TraceEvent>,
    val providerAcquisitionSamples: List<ProviderAcquisitionSample>,
)

@Serializable
data class OutputManifestEntry(
    val taskId: String,
    val sourceEntryPath: String,
    val passId: String,
    val outputRelativePath: String,
    val bytesWritten: Long,
    val isArchiveCandidate: Boolean,
    val renameApplied: Boolean,
    val collisionIndex: Int?,
)

@Serializable
data class OutputManifest(
    val passId: String,
    val outputs: List<OutputManifestEntry>,
)

@Serializable
data class ArchiveItemRecord(
    val taskId: String,
    val sourceEntryPath: String,
    val passId: String,
    val itemIndex: Int,
    val archivePath: String?,
    val isFolder: Boolean,
    val encrypted: Boolean,
    val size: Long?,
    val packedSize: Long?,
    val askMode: String?,
    val outputRelativePath: String?,
    val isArchiveCandidate: Boolean,
    val renameApplied: Boolean,
    val collisionIndex: Int?,
    val bytesWritten: Long,
    val operationResult: String?,
    val pathRejected: Boolean,
)

@Serializable
data class FailureRecord(
    val taskId: String,
    val sourceEntryPath: String,
    val archivePath: String,
    val errorType: String,
    val message: String?,
    val operationResults: List<String>,
    val stackTrace: String?,
    val firstCause: String?,
    val lastCause: String?,
    val firstPotentialCause: String?,
    val lastPotentialCause: String?,
)

@Serializable
data class CleanupActionRecord(
    val taskId: String,
    val action: CleanupActionType,
    val path: String,
    val reason: String,
)

@Serializable
data class CleanupRecord(
    val actions: List<CleanupActionRecord>,
)

@Serializable
data class IdentityTraceRecord(
    val taskId: String,
    val providerFilePath: String,
    val selectedEntryPath: String,
    val stagedArchiveRelativePath: String,
    val finalOutputs: List<String>,
)

@Serializable
data class DiagnosticsManifest(
    val contractVersion: Int,
    val sessionId: String,
    val appId: String,
    val androidRelease: String,
    val deviceModel: String,
    val exportTimestamp: String,
    val redactionPolicyVersion: Int,
)

@Serializable
data class DiagnosticsEvent(
    val timestamp: String,
    val sessionId: String,
    val domain: String,
    val event: String,
    val outcome: String,
    val runId: String? = null,
    val taskId: String? = null,
    val snapshotId: String? = null,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class FailureEvent(
    val timestamp: String,
    val sessionId: String,
    val domain: String,
    val event: String,
    val outcome: String,
    val stage: Spike4Stage,
    val errorCode: String,
    val message: String,
    val runId: String? = null,
    val taskId: String? = null,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class DiagnosticsSummary(
    val eventCountsByDomain: Map<String, Int>,
    val failureCountsByDomain: Map<String, Int>,
    val latestFailureByDomain: Map<String, FailureEvent>,
)

@Serializable
data class RemoteArchiveInfo(
    val contentLength: Long,
    val acceptsRanges: Boolean,
    val rangeModeHint: String?,
)

@Serializable
data class RangeReadResult(
    val requestedStart: Long,
    val requestedEndInclusive: Long,
    val actualStart: Long,
    val actualEndInclusive: Long,
    val bytes: ByteArray,
)

@Serializable
data class HttpTraceEvent(
    val timestamp: String,
    val caseId: String,
    val stage: Spike4Stage,
    val method: String,
    val path: String,
    val requestRange: String?,
    val responseCode: Int?,
    val contentLength: Long?,
    val contentRange: String?,
    val elapsedMillis: Long,
    val failureMessage: String?,
)

data class ArchiveTaskInput(
    val taskId: String,
    val sourceEntryPath: String,
    val sourceFile: File,
    val providerFilePath: String,
    val deleteAfterSuccess: Boolean,
)

data class ExtractionResult(
    val itemRecords: List<ArchiveItemRecord>,
    val outputEntries: List<OutputManifestEntry>,
    val failure: FailureRecord?,
)

class Spike4FailureException(
    val stage: Spike4Stage,
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class RealDebridApiException(
    message: String,
    val httpStatus: Int,
    val providerCode: Int? = null,
    val providerMessage: String? = null,
    val endpoint: String,
    val method: String,
) : RuntimeException(message)
