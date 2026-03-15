package com.romulus.mobile.downloads.queue

import java.time.Instant

data class QueueSummary(val completed: Int, val total: Int, val failed: Int, val cancelled: Int)

enum class QueuePresentationState {
    QUEUED,
    RESOLVING,
    PREPARING,
    RUNNING,
    PAUSED,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class QueueActionKind {
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
    RESTART
}

data class DownloadRowViewState(
    val taskId: TaskId,
    val originalDisplayName: String,
    val state: QueuePresentationState,
    val stateLabel: String,
    val allowedActions: Set<QueueActionKind>,
    val progressLabel: String?,
    val createdAt: Instant,
    val details: DownloadRowDetailsViewState
)

data class DownloadRowDetailsViewState(
    val sourceEntry: String,
    val outputSubfolder: String?,
    val outputSummary: String,
    val currentState: String,
    val updatedAt: Instant,
    val originalSizeBytes: Long?,
    val failureReason: String?,
    val partLabel: String?,
    val attemptCount: Int?,
    val preparing: PreparingRowDetailsViewState?,
    val transfer: TransferRowDetailsViewState?
)

data class PreparingRowDetailsViewState(
    val enteredAt: Instant,
    val timeoutAt: Instant,
    val lastProviderStatus: String?,
    val lastProviderProgress: Double?
)

data class TransferRowDetailsViewState(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val progressPercentLabel: String
)

data class DownloadsProjection(
    val summary: QueueSummary,
    val rows: List<DownloadRowViewState>,
    val activeDownloads: Boolean
)
