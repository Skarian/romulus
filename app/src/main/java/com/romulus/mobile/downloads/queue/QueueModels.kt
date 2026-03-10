package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.TempFileToken
import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.source.browse.SelectableItemId
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import java.time.Instant

@JvmInline
value class TaskId(val value: String)

data class SourceQueueMetadata(
    val entryDisplayName: String,
    val partLabel: String?,
    val providerFileId: String?
)

data class NamingIntent(val applyRename: Boolean, val renameRule: RenameRule?)

data class StorageTargetContext(val subfolder: String)

sealed interface QueueExecutionContext {
    data class StandardFile(val providerLocator: ProviderLocator) : QueueExecutionContext

    data class ArchiveEntry(
        val outerZip: ArchiveContainerLocator,
        val archiveEntryIdentity: ArchiveEntryIdentity
    ) : QueueExecutionContext
}

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

data class QueueTask(
    val taskId: TaskId,
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

sealed interface QueueTaskState {
    data object Queued : QueueTaskState

    data class Resolving(val enteredAt: Instant) : QueueTaskState

    data class Preparing(val metadata: PreparingMetadata) : QueueTaskState

    data class Running(val checkpoint: TransferCheckpoint) : QueueTaskState

    data class Paused(val checkpoint: TransferCheckpoint) : QueueTaskState

    data class RetryScheduled(val retryAt: Instant, val attemptIndex: Int) : QueueTaskState

    data object Completed : QueueTaskState

    data class Failed(val reason: FailureReason) : QueueTaskState

    data class Cancelled(val reason: String?) : QueueTaskState
}

enum class QueueVisibility {
    VISIBLE,
    HIDDEN
}

enum class PendingQueueAction {
    PAUSE,
    CANCEL
}

enum class ControlSignal {
    NONE,
    PAUSE,
    CANCEL
}

sealed interface QueueActionCommand {
    val taskId: TaskId

    data class Pause(override val taskId: TaskId) : QueueActionCommand

    data class Resume(override val taskId: TaskId) : QueueActionCommand

    data class Cancel(override val taskId: TaskId) : QueueActionCommand

    data class Retry(override val taskId: TaskId) : QueueActionCommand

    data class Restart(override val taskId: TaskId) : QueueActionCommand
}

data class QueueActionRequest(
    val taskId: TaskId,
    val action: PendingQueueAction,
    val requestedAt: Instant
)

data class ClaimLease(val claimedAt: Instant, val leaseExpiresAt: Instant)

data class PreparingMetadata(
    val enteredAt: Instant,
    val timeoutAt: Instant,
    val lastProviderStatus: String?,
    val lastProviderProgress: Double?,
    val resumeMarker: ProviderResumeMarker?
)

data class TransferCheckpoint(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val lastPersistedAt: Instant,
    val tempFileToken: TempFileToken?,
    val resumeByteOffset: Long
)

sealed interface FailureReason {
    data class AuthRequired(val message: String) : FailureReason

    data class ProviderFailure(val stage: String, val message: String) : FailureReason

    data class OutputFailure(val message: String) : FailureReason

    data class DirectoryAccessFailure(val message: String) : FailureReason
}

data class QueueClaim(
    val task: QueueTask,
    val state: QueueTaskState,
    val lease: ClaimLease,
    val attemptCount: Int,
    val pendingAction: QueueActionRequest?,
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>
)

data class QueueRowRecord(
    val task: QueueTask,
    val updatedAt: Instant,
    val state: QueueTaskState,
    val visibility: QueueVisibility,
    val attemptCount: Int,
    val lease: ClaimLease?,
    val pendingAction: QueueActionRequest?,
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>
)

sealed interface EnqueueResult {
    data class Enqueued(val taskIds: List<TaskId>) : EnqueueResult

    data class Rejected(val message: String) : EnqueueResult

    data class Failed(val message: String) : EnqueueResult
}
