@file:Suppress("TooManyFunctions")

package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.ReservedArtifactHandling
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class QueueSummaryProjector(
    ledgerStore: DownloadLedgerStore,
    dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val projection = ledgerStore
        .observeRows(includeHidden = true)
        .map(::buildProjection)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = buildProjection(emptyList())
        )
    private val activeDownloads = projection
        .map { projected -> projected.activeDownloads }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = projection.value.activeDownloads
        )

    fun observeProjection(): StateFlow<DownloadsProjection> = projection

    fun observeActiveDownloads(): StateFlow<Boolean> = activeDownloads
}

private fun buildProjection(rows: List<QueueRowRecord>): DownloadsProjection {
    val visibleRows = rows.filter { row -> row.visibility == QueueVisibility.VISIBLE }
    val summary = QueueSummary(
        completed = visibleRows.count { row -> row.state == QueueTaskState.Completed },
        total = visibleRows.size,
        failed = visibleRows.count { row -> row.state is QueueTaskState.Failed },
        cancelled = visibleRows.count { row -> row.state is QueueTaskState.Cancelled }
    )
    val visibleRowStates = visibleRows
        .sortedByDescending { row -> row.task.createdAt }
        .map(::toRowViewState)

    return DownloadsProjection(
        summary = summary,
        rows = visibleRowStates,
        activeDownloads = rows.any { row -> row.state.isActive() }
    )
}

private fun toRowViewState(row: QueueRowRecord): DownloadRowViewState = DownloadRowViewState(
    taskId = row.task.taskId,
    originalDisplayName = row.task.originalDisplayName,
    state = row.state.toPresentationState(),
    stateLabel = row.state.toStateLabel(),
    allowedActions = row.state.allowedActions(),
    progressLabel = row.state.toProgressLabel(),
    createdAt = row.task.createdAt,
    details = DownloadRowDetailsViewState(
        sourceEntry = row.task.sourceMetadata.entryDisplayName,
        outputSubfolder = row.task.storageTarget.subfolder
            .takeIf(String::isNotBlank),
        outputSummary = row.outputSummary(),
        currentState = row.state.toStateLabel(),
        updatedAt = row.updatedAt,
        originalSizeBytes = row.task.originalSizeBytes,
        failureReason = row.state.failureReasonMessage(),
        partLabel = row.task.sourceMetadata.partLabel,
        attemptCount = row.attemptCount.takeIf { count -> count > 0 },
        preparing = row.state.preparingDetails(),
        transfer = row.state.transferDetails()
    )
)

@Suppress("ReturnCount")
private fun QueueRowRecord.outputSummary(): String {
    if (reservation?.artifact?.handling == ReservedArtifactHandling.LOCAL_UNARCHIVE ||
        (reservation == null && task.shouldDescribeExtraction())
    ) {
        return extractionSummary()
    }

    val finalOutputs = finalOutputs
    if (finalOutputs.isNotEmpty()) {
        return finalOutputs.single().displayName
    }

    reservation?.directOutput?.let { directOutput ->
        return directOutput.displayName
    }

    val renameRule = task.namingIntent.renameRule
    if (task.namingIntent.applyRename && renameRule != null) {
        return runCatching {
            Regex(renameRule.pattern).replace(
                input = task.originalDisplayName,
                replacement = renameRule.replacement
            )
        }.getOrDefault(task.originalDisplayName)
    }
    return task.originalDisplayName
}

private fun QueueRowRecord.extractionSummary(): String {
    val previewNames = when {
        finalOutputs.isNotEmpty() ->
            finalOutputs
                .sortedBy(FinalOutputRecord::relativePath)
                .map(FinalOutputRecord::displayName)

        reservation?.extractionPlan?.isNotEmpty() == true ->
            checkNotNull(reservation)
                .extractionPlan
                .sortedBy { output -> output.relativePath }
                .map { output -> output.displayName }

        else -> emptyList()
    }
    val fileCount = previewNames.size.takeIf { count -> count > 0 }
    val visibleNames = previewNames
        .take(EXTRACTION_PREVIEW_LIMIT)
        .joinToString(separator = ", ")
        .takeIf(String::isNotBlank)
    val remainingCount = (previewNames.size - EXTRACTION_PREVIEW_LIMIT)
        .coerceAtLeast(0)
        .takeIf { count -> count > 0 }
    return buildString {
        append("Flattened extraction")
        if (fileCount != null) {
            append(" (")
            append(fileCount)
            append(if (fileCount == 1) " file" else " files")
            append(')')
        }
        if (visibleNames != null) {
            append(": ")
            append(visibleNames)
            if (remainingCount != null) {
                append(" +")
                append(remainingCount)
                append(" more")
            }
        }
    }
}

private fun QueueTask.shouldDescribeExtraction(): Boolean =
    unarchiveIntent && originalDisplayName.substringAfterLast('.', "").lowercase() in
        SUPPORTED_ARCHIVE_EXTENSIONS

private fun QueueTaskState.toPresentationState(): QueuePresentationState = when (this) {
    QueueTaskState.Queued -> QueuePresentationState.QUEUED
    is QueueTaskState.Resolving -> QueuePresentationState.RESOLVING
    is QueueTaskState.Preparing -> QueuePresentationState.PREPARING
    is QueueTaskState.Running -> QueuePresentationState.RUNNING
    is QueueTaskState.Paused -> QueuePresentationState.PAUSED
    is QueueTaskState.RetryScheduled -> QueuePresentationState.RETRY_SCHEDULED
    QueueTaskState.Completed -> QueuePresentationState.COMPLETED
    is QueueTaskState.Failed -> QueuePresentationState.FAILED
    is QueueTaskState.Cancelled -> QueuePresentationState.CANCELLED
}

private fun QueueTaskState.toStateLabel(): String = when (this) {
    QueueTaskState.Queued -> "Queued"
    is QueueTaskState.Resolving -> "Resolving"
    is QueueTaskState.Preparing -> "Preparing"
    is QueueTaskState.Running -> "Downloading"
    is QueueTaskState.Paused -> "Paused"
    is QueueTaskState.RetryScheduled -> "Retry scheduled"
    QueueTaskState.Completed -> "Done"
    is QueueTaskState.Failed -> "Failed"
    is QueueTaskState.Cancelled -> "Cancelled"
}

private fun QueueTaskState.allowedActions(): Set<QueueActionKind> = when (this) {
    QueueTaskState.Queued,
    is QueueTaskState.Resolving,
    is QueueTaskState.Preparing,
    is QueueTaskState.RetryScheduled -> setOf(QueueActionKind.CANCEL)

    is QueueTaskState.Running -> setOf(
        QueueActionKind.PAUSE,
        QueueActionKind.CANCEL
    )

    is QueueTaskState.Paused -> setOf(
        QueueActionKind.RESUME,
        QueueActionKind.CANCEL
    )

    is QueueTaskState.Failed -> setOf(
        QueueActionKind.RETRY,
        QueueActionKind.RESTART
    )

    is QueueTaskState.Cancelled,
    QueueTaskState.Completed -> setOf(QueueActionKind.RESTART)
}

private fun QueueTaskState.toProgressLabel(): String? = when (this) {
    is QueueTaskState.Preparing -> metadata.lastProviderProgress?.let { progress ->
        "${progress.toInt()}%"
    }

    is QueueTaskState.Running -> checkpoint.toProgressPercentLabel()
    is QueueTaskState.Paused -> checkpoint.toProgressPercentLabel()
    else -> null
}

private fun QueueTaskState.preparingDetails(): PreparingRowDetailsViewState? = when (this) {
    is QueueTaskState.Preparing -> PreparingRowDetailsViewState(
        enteredAt = metadata.enteredAt,
        timeoutAt = metadata.timeoutAt,
        lastProviderStatus = metadata.lastProviderStatus,
        lastProviderProgress = metadata.lastProviderProgress
    )

    else -> null
}

private fun QueueTaskState.transferDetails(): TransferRowDetailsViewState? = when (this) {
    is QueueTaskState.Running -> checkpoint.toTransferDetails()
    is QueueTaskState.Paused -> checkpoint.toTransferDetails()
    else -> null
}

private fun TransferCheckpoint.toTransferDetails(): TransferRowDetailsViewState =
    TransferRowDetailsViewState(
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        progressPercentLabel = toProgressPercentLabel()
    )

private fun TransferCheckpoint.toProgressPercentLabel(): String {
    if (totalBytes == null || totalBytes <= 0L) {
        return "--%"
    }
    val progress = (downloadedBytes.toDouble() / totalBytes.toDouble()) * PERCENT_SCALE
    return "${progress.toInt().coerceIn(0, FULL_PERCENT)}%"
}

private fun QueueTaskState.failureReasonMessage(): String? = when (this) {
    is QueueTaskState.Failed -> when (val failure = reason) {
        is FailureReason.AuthRequired -> failure.message
        is FailureReason.ProviderFailure -> "${failure.stage}: ${failure.message}"
        is FailureReason.OutputFailure -> failure.message
        is FailureReason.DirectoryAccessFailure -> failure.message
    }

    is QueueTaskState.Cancelled -> reason
    else -> null
}

private fun QueueTaskState.isActive(): Boolean = when (this) {
    QueueTaskState.Queued,
    is QueueTaskState.Resolving,
    is QueueTaskState.Preparing,
    is QueueTaskState.Running,
    is QueueTaskState.Paused,
    is QueueTaskState.RetryScheduled -> true

    QueueTaskState.Completed,
    is QueueTaskState.Failed,
    is QueueTaskState.Cancelled -> false
}

private const val PERCENT_SCALE = 100.0
private const val FULL_PERCENT = 100
private const val EXTRACTION_PREVIEW_LIMIT = 2
private val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z")
