package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.FinalOutputId
import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.OutputCleanupScope
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.TempFileToken
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.source.InstantAsEpochMilliSerializer
import com.romulus.mobile.source.browse.ArchivePreparationKey
import com.romulus.mobile.source.browse.SelectableItemId
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.torrentmeta.TorrentFileSelectionIntent
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TaskId(val value: String)

@Serializable
data class SourceQueueMetadata(
    val entryDisplayName: String,
    val partLabel: String?,
    val providerFileId: String?
)

@Serializable
data class NamingIntent(val applyRename: Boolean, val renameRule: RenameRule?)

@Serializable
data class StorageTargetContext(val subfolder: String)

@Serializable
sealed interface QueueExecutionContext {
    @Serializable
    data class StandardFile(val selectionIntent: TorrentFileSelectionIntent) : QueueExecutionContext

    @Serializable
    data class ArchiveEntry(
        val preparationKey: ArchivePreparationKey,
        val archiveEntryIdentity: ArchiveEntryIdentity
    ) : QueueExecutionContext
}

@Serializable
data class QueueTaskInput(
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId,
    val selectedItemId: SelectableItemId,
    val originalDisplayName: String,
    val originalSizeBytes: Long?,
    val sourceMetadata: SourceQueueMetadata,
    val namingIntent: NamingIntent,
    val unarchiveIntent: Boolean,
    val recursiveUnarchiveIntent: Boolean,
    val storageTarget: StorageTargetContext,
    val executionContext: QueueExecutionContext
)

@Serializable
data class QueueTask(
    val taskId: TaskId,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val createdAt: Instant,
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId,
    val selectedItemId: SelectableItemId,
    val originalDisplayName: String,
    val originalSizeBytes: Long?,
    val sourceMetadata: SourceQueueMetadata,
    val namingIntent: NamingIntent,
    val unarchiveIntent: Boolean,
    val recursiveUnarchiveIntent: Boolean,
    val storageTarget: StorageTargetContext,
    val executionContext: QueueExecutionContext
)

@Serializable
sealed interface QueueTaskState {
    @Serializable
    data object Queued : QueueTaskState

    @Serializable
    data class Resolving(
        @Serializable(with = InstantAsEpochMilliSerializer::class)
        val enteredAt: Instant
    ) : QueueTaskState

    @Serializable
    data class Preparing(val metadata: PreparingMetadata) : QueueTaskState

    @Serializable
    data class Running(val checkpoint: TransferCheckpoint) : QueueTaskState

    @Serializable
    data class Paused(val checkpoint: TransferCheckpoint) : QueueTaskState

    @Serializable
    data class RetryScheduled(
        @Serializable(with = InstantAsEpochMilliSerializer::class)
        val retryAt: Instant,
        val attemptIndex: Int
    ) : QueueTaskState

    @Serializable
    data object Completed : QueueTaskState

    @Serializable
    data class Failed(val reason: FailureReason) : QueueTaskState

    @Serializable
    data class Cancelled(val reason: String?) : QueueTaskState
}

@Serializable
enum class QueueVisibility {
    VISIBLE,
    HIDDEN
}

@Serializable
enum class PendingQueueAction {
    PAUSE,
    RESUME,
    CANCEL
}

@Serializable
enum class ControlSignal {
    NONE,
    PAUSE,
    CANCEL
}

@Serializable
sealed interface QueueActionCommand {
    val taskId: TaskId

    @Serializable
    data class Pause(override val taskId: TaskId) : QueueActionCommand

    @Serializable
    data class Resume(override val taskId: TaskId) : QueueActionCommand

    @Serializable
    data class Cancel(override val taskId: TaskId) : QueueActionCommand

    @Serializable
    data class Retry(override val taskId: TaskId) : QueueActionCommand

    @Serializable
    data class Restart(override val taskId: TaskId) : QueueActionCommand
}

@Serializable
data class QueueActionRequest(
    val taskId: TaskId,
    val action: PendingQueueAction,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val requestedAt: Instant
)

@Serializable
data class ClaimLease(
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val claimedAt: Instant,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val leaseExpiresAt: Instant
) {
    companion object
}

@Serializable
data class PreparingMetadata(
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val enteredAt: Instant,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val timeoutAt: Instant,
    val lastProviderStatus: String?,
    val lastProviderProgress: Double?,
    val resumeMarker: ProviderResumeMarker?
)

@Serializable
data class TransferCheckpoint(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val lastPersistedAt: Instant,
    val tempFileToken: TempFileToken?,
    val resumeByteOffset: Long
)

@Serializable
sealed interface FinalizationCursor {
    @Serializable
    data class DirectSave(val stage: DirectSaveStage) : FinalizationCursor

    @Serializable
    data class Unarchive(
        val pendingPasses: List<PendingArchivePassCursor>,
        val activePassReservedEntries: List<String>,
        val currentPromotionOutputId: FinalOutputId?,
        val completionPending: Boolean
    ) : FinalizationCursor
}

@Serializable
enum class DirectSaveStage {
    PROMOTING,
    COMPLETION_PENDING
}

@Serializable
data class PendingArchivePassCursor(
    val archiveFilePath: String,
    val lineage: String,
    val passIndex: Int
)

@Serializable
sealed interface FailureReason {
    @Serializable
    data class AuthRequired(val message: String) : FailureReason

    @Serializable
    data class ProviderFailure(val stage: String, val message: String) : FailureReason

    @Serializable
    data class OutputFailure(val message: String) : FailureReason

    @Serializable
    data class DirectoryAccessFailure(val message: String) : FailureReason
}

@Serializable
data class QueueClaim(
    val task: QueueTask,
    val state: QueueTaskState,
    val lease: ClaimLease,
    val attemptCount: Int,
    val pendingAction: QueueActionRequest?,
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>,
    val finalizationCursor: FinalizationCursor? = null
)

@Serializable
data class QueueRowRecord(
    val task: QueueTask,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val updatedAt: Instant,
    val state: QueueTaskState,
    val visibility: QueueVisibility,
    val attemptCount: Int,
    val lease: ClaimLease?,
    val pendingAction: QueueActionRequest?,
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>,
    val cleanupScopes: List<OutputCleanupScope> = emptyList(),
    val finalizationCursor: FinalizationCursor? = null
)

@Serializable
sealed interface EnqueueResult {
    @Serializable
    data class Enqueued(val taskIds: List<TaskId>) : EnqueueResult

    @Serializable
    @Suppress("ClassSignature")
    data class EnqueuedPendingDispatch(
        val taskIds: List<TaskId>,
        val message: String
    ) : EnqueueResult

    @Serializable
    data class Rejected(val message: String) : EnqueueResult

    @Serializable
    data class Failed(val message: String) : EnqueueResult
}
