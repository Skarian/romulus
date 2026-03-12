package com.romulus.mobile.downloads

import com.romulus.mobile.downloads.attempts.DownloadStream
import com.romulus.mobile.downloads.attempts.DownloadTransport
import com.romulus.mobile.downloads.attempts.ProviderRuntimeGateway
import com.romulus.mobile.downloads.config.DownloadSettingsReadiness
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.config.DownloadSettingsStore
import com.romulus.mobile.downloads.config.OutputDirectoryAccess
import com.romulus.mobile.downloads.output.ArchiveRuntime
import com.romulus.mobile.downloads.output.ArchiveRuntimeEntry
import com.romulus.mobile.downloads.output.ArchiveRuntimePassResult
import com.romulus.mobile.downloads.output.ArchiveRuntimeExtractedArtifact
import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.FinalizationControl
import com.romulus.mobile.downloads.output.OutputFilesystem
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.failIfStopped
import com.romulus.mobile.downloads.queue.DownloadLedgerStore
import com.romulus.mobile.downloads.queue.NamingIntent
import com.romulus.mobile.downloads.queue.QueueExecutionContext
import com.romulus.mobile.downloads.queue.QueueRowRecord
import com.romulus.mobile.downloads.queue.QueueTaskState
import com.romulus.mobile.downloads.queue.QueueTask
import com.romulus.mobile.downloads.queue.QueueTaskInput
import com.romulus.mobile.downloads.queue.QueueActionRequest
import com.romulus.mobile.downloads.queue.SourceQueueMetadata
import com.romulus.mobile.downloads.queue.StorageTargetContext
import com.romulus.mobile.downloads.queue.TaskId
import com.romulus.mobile.downloads.queue.TransferCheckpoint
import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ProviderSelectionRequest
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ResolvedDownloadUnit
import com.romulus.mobile.realdebrid.auth.TokenReadiness
import com.romulus.mobile.downloads.work.CompletionNotificationSnapshot
import com.romulus.mobile.downloads.work.NotificationApi
import com.romulus.mobile.downloads.work.ProgressNotificationSnapshot
import com.romulus.mobile.source.browse.SelectableItemId
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.torrentmeta.TorrentFileSelectionIntent
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeDownloadSettingsStore : DownloadSettingsStore {
    var persistedState: DownloadSettingsState? = null

    override suspend fun read(): DownloadSettingsState? = persistedState

    override suspend fun write(state: DownloadSettingsState): Result<Unit> {
        persistedState = state
        return Result.success(Unit)
    }
}

internal class ConfigurableDownloadLedgerStore(
    private val delegate: DownloadLedgerStore
) : DownloadLedgerStore by delegate {
    var persistStateError: Throwable? = null
    var persistReservationError: Throwable? = null
    var enterFinalizationError: Throwable? = null
    var persistFinalizationCursorError: Throwable? = null
    var completeTaskError: Throwable? = null
    var onClaimActionRecoveryTasks: (() -> Unit)? = null
    var renewClaimCount: Int = 0

    override suspend fun persistState(
        taskId: TaskId,
        state: QueueTaskState
    ): Result<Unit> = persistStateError?.let(Result.Companion::failure)
        ?: delegate.persistState(taskId, state)

    override suspend fun persistReservation(
        taskId: TaskId,
        reservation: OutputReservation
    ): Result<Unit> = persistReservationError?.let(Result.Companion::failure)
        ?: delegate.persistReservation(taskId, reservation)

    override suspend fun enterFinalization(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: com.romulus.mobile.downloads.queue.FinalizationCursor
    ): Result<Unit> = enterFinalizationError?.let(Result.Companion::failure)
        ?: delegate.enterFinalization(taskId, checkpoint, cursor)

    override suspend fun persistFinalizationCursor(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: com.romulus.mobile.downloads.queue.FinalizationCursor
    ): Result<Unit> = persistFinalizationCursorError?.let(Result.Companion::failure)
        ?: delegate.persistFinalizationCursor(taskId, checkpoint, cursor)

    override suspend fun completeTask(
        taskId: TaskId,
        outputs: List<FinalOutputRecord>
    ): Result<Unit> = completeTaskError?.let(Result.Companion::failure)
        ?: delegate.completeTask(taskId, outputs)

    override suspend fun claimActionRecoveryTasks(now: Instant, limit: Int) =
        delegate.claimActionRecoveryTasks(now, limit).also {
            onClaimActionRecoveryTasks?.invoke()
        }

    override suspend fun renewClaim(taskId: TaskId): Result<Unit> {
        renewClaimCount += 1
        return delegate.renewClaim(taskId)
    }
}

internal class FakeOutputDirectoryAccess(
    private var usableUris: Set<String>
) : OutputDirectoryAccess {
    override suspend fun check(outputDirectoryUri: String?): DownloadSettingsReadiness =
        if (outputDirectoryUri != null && outputDirectoryUri in usableUris) {
            DownloadSettingsReadiness(
                isUsable = true,
                brokenReason = null
            )
        } else {
            DownloadSettingsReadiness(
                isUsable = false,
                brokenReason = "Download directory is no longer accessible"
            )
        }

    fun updateUsableUris(next: Set<String>) {
        usableUris = next
    }
}

internal class FakeProviderRuntimeGateway(
    initialReadiness: TokenReadiness = TokenReadiness(
        isUsable = true,
        brokenReason = null
    )
) : ProviderRuntimeGateway {
    private val readiness = MutableStateFlow(initialReadiness)
    var startResult: Result<AcquisitionStatus> = Result.failure(IllegalStateException("Unset"))
    var resumeResult: Result<AcquisitionStatus> = Result.failure(IllegalStateException("Unset"))
    var resolveResult: Result<ResolvedDownloadUnit> = Result.failure(IllegalStateException("Unset"))

    override fun observeTokenReadiness(): StateFlow<TokenReadiness> = readiness

    override suspend fun readTokenReadiness(): TokenReadiness = readiness.value

    override suspend fun startAcquisition(
        request: ProviderSelectionRequest
    ): Result<AcquisitionStatus> = startResult

    override suspend fun resumeAcquisition(
        marker: ProviderResumeMarker
    ): Result<AcquisitionStatus> = resumeResult

    override suspend fun resolveReadyLink(
        link: ProviderReadyLink
    ): Result<ResolvedDownloadUnit> = resolveResult

    fun updateReadiness(next: TokenReadiness) {
        readiness.value = next
    }
}

internal class RecordingWorkerLauncher : com.romulus.mobile.downloads.work.WorkerLauncher {
    var immediateLaunchCount: Int = 0
    var immediateLaunchAttempts: Int = 0
    var nextImmediateLaunchFailure: Throwable? = null
    val scheduledInstants = mutableListOf<Instant>()

    override suspend fun launchNow(): Result<Unit> {
        immediateLaunchAttempts += 1
        nextImmediateLaunchFailure?.let { throwable ->
            nextImmediateLaunchFailure = null
            return Result.failure(throwable)
        }
        immediateLaunchCount += 1
        return Result.success(Unit)
    }

    override suspend fun launchAt(instant: Instant): Result<Unit> {
        scheduledInstants += instant
        return Result.success(Unit)
    }
}

internal class FakeOutputFilesystem(
    private val rootDirectory: File
) : OutputFilesystem {
    val writtenOutputs = mutableMapOf<String, ByteArray>()
    var writeFailure: Throwable? = null
    var copyChunkSize: Int = 1
    var onWriteChunk: (() -> Unit)? = null

    override suspend fun listRelativePaths(
        outputDirectoryUri: String,
        subfolder: String
    ): Result<Set<String>> = Result.success(
        writtenOutputs.keys.filter { path ->
            path.substringBeforeLast('/', "") == subfolder.trim().trim('/')
        }.toSet()
    )

    override suspend fun writeArtifactToFinalOutput(
        artifactPath: String,
        outputDirectoryUri: String,
        relativePath: String,
        control: FinalizationControl?
    ): Result<Long> = runCatching {
        check(relativePath !in writtenOutputs) {
            "Final output path is already occupied: $relativePath"
        }
        control.failIfStopped()
        val bytes = File(artifactPath).readBytes()
        val outputFile = rootDirectory.resolve(relativePath)
        outputFile.parentFile?.mkdirs()
        val copied = ArrayList<Byte>(bytes.size)
        runCatching {
            var offset = 0
            while (offset < bytes.size) {
                val nextOffset = (offset + copyChunkSize).coerceAtMost(bytes.size)
                copied.addAll(bytes.copyOfRange(offset, nextOffset).toList())
                outputFile.writeBytes(copied.toByteArray())
                writtenOutputs[relativePath] = copied.toByteArray()
                onWriteChunk?.invoke()
                control.failIfStopped()
                control?.pulse()?.getOrElse { throwable ->
                    throw throwable
                }
                offset = nextOffset
            }
            writeFailure?.let { throwable ->
                throw throwable
            }
        }.getOrElse { throwable ->
            writtenOutputs.remove(relativePath)
            outputFile.delete()
            throw throwable
        }
        bytes.size.toLong()
    }

    override suspend fun deleteFinalOutput(
        outputDirectoryUri: String,
        relativePath: String
    ): Result<Unit> = runCatching {
        writtenOutputs.remove(relativePath)
        rootDirectory.resolve(relativePath).delete()
        Unit
    }

    override fun isSupportedArchive(localArtifactPath: String): Boolean =
        localArtifactPath.substringAfterLast('.', "").lowercase() in setOf("zip", "rar", "7z")
}

internal class FakeDownloadTransport(
    private val bytes: ByteArray,
    private val resumeAccepted: Boolean = true
) : DownloadTransport {
    override suspend fun open(url: String, resumeByteOffset: Long): Result<DownloadStream> {
        val responseBytes = if (resumeByteOffset <= 0L) {
            bytes
        } else {
            bytes.copyOfRange(resumeByteOffset.toInt(), bytes.size)
        }
        return Result.success(
            DownloadStream(
                inputStream = ByteArrayInputStream(responseBytes),
                totalBytes = bytes.size.toLong(),
                resumeAccepted = resumeAccepted,
                closeable = Closeable {}
            )
        )
    }
}

internal class FakeArchiveRuntime : ArchiveRuntime {
    val inspections = mutableMapOf<String, List<ArchiveRuntimeEntry>>()
    val extractions = mutableMapOf<String, FakeArchiveExtraction>()

    override suspend fun inspect(archiveFile: File): Result<List<ArchiveRuntimeEntry>> =
        Result.success(checkNotNull(inspections[archiveFile.name]))

    override suspend fun extract(
        archiveFile: File,
        targetFiles: Map<String, File>,
        control: FinalizationControl?
    ): Result<ArchiveRuntimePassResult> {
        val extraction = checkNotNull(extractions[archiveFile.name])
        val outputs = extraction.entries.map { entry ->
            val targetFile = checkNotNull(targetFiles[entry.rawEntryPath])
            targetFile.parentFile?.mkdirs()
            control.failIfStopped()
            targetFile.writeBytes(entry.bytes)
            control?.pulse()?.getOrElse { throwable ->
                throw throwable
            }
            control.failIfStopped()
            ArchiveRuntimeExtractedArtifact(
                rawEntryPath = entry.rawEntryPath,
                localArtifactPath = targetFile.absolutePath,
                sizeBytes = entry.bytes.size.toLong(),
                isArchiveCandidate = entry.isArchiveCandidate
            )
        }
        return Result.success(
            ArchiveRuntimePassResult(
                outputs = outputs,
                failureMessage = extraction.failureMessage
            )
        )
    }
}

internal data class FakeArchiveExtractionEntry(
    val rawEntryPath: String,
    val bytes: ByteArray,
    val isArchiveCandidate: Boolean
)

internal data class FakeArchiveExtraction(
    val entries: List<FakeArchiveExtractionEntry>,
    val failureMessage: String? = null
)

internal class RecordingNotificationApi : NotificationApi {
    val progressSnapshots = mutableListOf<ProgressNotificationSnapshot>()
    val completionSnapshots = mutableListOf<CompletionNotificationSnapshot>()
    var clearCount: Int = 0

    override fun showProgress(snapshot: ProgressNotificationSnapshot) {
        progressSnapshots += snapshot
    }

    override fun clearProgress() {
        clearCount += 1
    }

    override fun showCompletion(snapshot: CompletionNotificationSnapshot) {
        completionSnapshots += snapshot
    }
}

internal fun sampleQueueTaskInput(
    name: String,
    namingIntent: NamingIntent = NamingIntent(
        applyRename = false,
        renameRule = null
    ),
    unarchiveIntent: Boolean = false,
    recursiveUnarchiveIntent: Boolean = false
): QueueTaskInput = QueueTaskInput(
    snapshotId = SnapshotId("snapshot"),
    entryId = SourceEntryId("entry"),
    selectedItemId = SelectableItemId("item"),
    originalDisplayName = name,
    originalSizeBytes = 1_024L,
    sourceMetadata = SourceQueueMetadata(
        entryDisplayName = "Entry",
        partLabel = null,
        providerFileId = "provider-file"
    ),
    namingIntent = namingIntent,
    unarchiveIntent = unarchiveIntent,
    recursiveUnarchiveIntent = recursiveUnarchiveIntent,
    storageTarget = StorageTargetContext(subfolder = "shows"),
    executionContext = QueueExecutionContext.StandardFile(
        selectionIntent = TorrentFileSelectionIntent(
            sourceMagnetUri = "magnet:?xt=urn:btih:test",
            normalizedPath = "shows/$name",
            sizeBytes = 1_024L,
            occurrenceIndex = 1
        )
    )
)

internal fun sampleQueueTask(
    taskId: TaskId = TaskId("task-1"),
    createdAt: Instant = Instant.parse("2026-03-10T18:00:00Z"),
    name: String = "Episode.mkv",
    namingIntent: NamingIntent = NamingIntent(
        applyRename = false,
        renameRule = null
    ),
    unarchiveIntent: Boolean = false,
    recursiveUnarchiveIntent: Boolean = false
): QueueTask {
    val input = sampleQueueTaskInput(
        name = name,
        namingIntent = namingIntent,
        unarchiveIntent = unarchiveIntent,
        recursiveUnarchiveIntent = recursiveUnarchiveIntent
    )
    return QueueTask(
        taskId = taskId,
        createdAt = createdAt,
        snapshotId = input.snapshotId,
        entryId = input.entryId,
        selectedItemId = input.selectedItemId,
        originalDisplayName = input.originalDisplayName,
        originalSizeBytes = input.originalSizeBytes,
        sourceMetadata = input.sourceMetadata,
        namingIntent = input.namingIntent,
        unarchiveIntent = input.unarchiveIntent,
        recursiveUnarchiveIntent = input.recursiveUnarchiveIntent,
        storageTarget = input.storageTarget,
        executionContext = input.executionContext
    )
}

internal fun renameIntent(pattern: String, replacement: String): NamingIntent = NamingIntent(
    applyRename = true,
    renameRule = RenameRule(
        pattern = pattern,
        replacement = replacement
    )
)
