package com.romulus.spikes.spike2

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.sf.sevenzipjbinding.SevenZipException

val spike2Json = Json {
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
enum class ArchiveFamily {
    ZIP,
    RAR,
    SEVEN_ZIP,
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
}

@Serializable
enum class PassInputOrigin {
    GENERATED_INPUT,
    RECURSIVE_OUTPUT,
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
enum class FixtureInputMutation {
    REPLACE_WITH_GARBAGE_BYTES,
}

@Serializable
data class FixtureDescriptor(
    val id: String,
    val relativeInputPath: String,
    val hostInputPath: String,
    val archiveFamily: ArchiveFamily,
    val outputSubdirectory: String,
    val multipart: Boolean = false,
    val inputMutation: FixtureInputMutation? = null,
)

@Serializable
data class RunDefinition(
    val id: String,
    val title: String,
    val description: String,
    val destinationSubfolder: String,
    val fixtures: List<FixtureDescriptor>,
    val unarchiveEnabled: Boolean,
    val recursiveUnarchiveEnabled: Boolean,
    val renameRule: RenameRule?,
    val expectedOutcome: RunExpectation,
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
data class PassInputArchiveRecord(
    val fixtureId: String,
    val origin: PassInputOrigin,
    val archiveFamily: ArchiveFamily,
    val multipart: Boolean,
    val inputMutation: FixtureInputMutation?,
    val hostRelativeSourcePath: String?,
    val deviceSourcePath: String,
    val outputSubdirectory: String,
    val deleteAfterSuccess: Boolean,
)

@Serializable
data class PassInputManifest(
    val passId: String,
    val archives: List<PassInputArchiveRecord>,
)

@Serializable
data class ArchiveItemRecord(
    val fixtureId: String,
    val archiveFamily: ArchiveFamily,
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
data class OutputManifestEntry(
    val fixtureId: String,
    val passId: String,
    val outputRelativePath: String,
    val itemIndex: Int,
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
data class CleanupActionRecord(
    val fixtureId: String,
    val action: CleanupActionType,
    val path: String,
    val reason: String,
)

@Serializable
data class CleanupRecord(
    val actions: List<CleanupActionRecord>,
)

@Serializable
data class FailureRecord(
    val fixtureId: String,
    val archiveFamily: ArchiveFamily,
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
data class VolumeResolutionRecord(
    val fixtureId: String,
    val requestedName: String,
    val resolved: Boolean,
    val resolvedDevicePath: String?,
    val requestedAtUtc: String,
)

@Serializable
data class VolumeResolutionEnvelope(
    val records: List<VolumeResolutionRecord>,
)

data class StagedFixtureWorkspace(
    val sessionId: String,
    val sessionRoot: File,
    val stagedInputRoot: File,
)

data class PassArchiveInput(
    val fixture: FixtureDescriptor,
    val origin: PassInputOrigin,
    val sourceFile: File,
    val hostRelativeSourcePath: String?,
    val deleteAfterSuccess: Boolean,
)

data class OutputTarget(
    val file: File,
    val outputRelativePath: String,
    val renameApplied: Boolean,
    val collisionIndex: Int?,
    val pathRejected: Boolean,
)

data class ExtractionResult(
    val itemRecords: List<ArchiveItemRecord>,
    val outputEntries: List<OutputManifestEntry>,
    val volumeResolutions: List<VolumeResolutionRecord>,
    val failure: FailureRecord?,
)

data class RunExecutionResult(
    val status: RunStatusCode,
    val summary: String,
    val runtimeInit: RuntimeInitRecord,
    val cleanup: CleanupRecord,
    val volumeResolutions: List<VolumeResolutionRecord>,
)
