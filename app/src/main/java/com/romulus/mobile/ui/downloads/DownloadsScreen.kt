@file:Suppress("LongMethod", "CyclomaticComplexMethod")

package com.romulus.mobile.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romulus.mobile.downloads.queue.DownloadRowViewState
import com.romulus.mobile.downloads.queue.QueueActionCommand
import com.romulus.mobile.downloads.queue.QueueActionKind
import com.romulus.mobile.downloads.queue.QueuePresentationState
import com.romulus.mobile.downloads.queue.TaskId
import com.romulus.mobile.ui.components.RomulusAlertDialog
import com.romulus.mobile.ui.components.RomulusButtonText
import com.romulus.mobile.ui.components.RomulusDialogActionText
import com.romulus.mobile.ui.components.RomulusDialogBodyText
import com.romulus.mobile.ui.components.RomulusDialogTitle
import com.romulus.mobile.ui.components.RomulusIconButton
import com.romulus.mobile.ui.components.TableDataRow
import com.romulus.mobile.ui.components.TableHeaderRow
import com.romulus.mobile.ui.components.romulusButtonColors
import com.romulus.mobile.ui.components.warmOverlayColor
import com.romulus.mobile.ui.formatByteCount
import com.romulus.mobile.ui.formatByteCountOrUnknown
import com.romulus.mobile.ui.formatTimestamp
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlinx.coroutines.flow.collect

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val selectedDetail = remember(state.projection.rows, state.detailTaskId) {
        state.projection.rows.firstOrNull { row -> row.taskId == state.detailTaskId }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is DownloadsEffect.Message) {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ResponsiveScreenContainer(
        modifier = modifier.fillMaxSize(),
        compactVerticalPadding = true
    ) { metrics ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val summaryLabel = buildSummaryLabel(
                    completed = state.projection.summary.completed,
                    total = state.projection.summary.total,
                    failed = state.projection.summary.failed,
                    cancelled = state.projection.summary.cancelled
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (summaryLabel.isNotEmpty()) {
                        Text(
                            text = summaryLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = viewModel::openClearHistory,
                    colors = romulusButtonColors()
                ) {
                    RomulusButtonText("Clear history")
                }
            }

            TableHeaderRow {
                Text(
                    text = "File",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Progress",
                    modifier = Modifier.width(110.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "",
                    modifier = Modifier.width(40.dp)
                )
            }

            if (state.projection.rows.isEmpty()) {
                Text(
                    text = "No active downloads",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.projection.rows, key = { row -> row.taskId.value }) { row ->
                        DownloadRow(
                            row = row,
                            onOpenDetails = { viewModel.openDetails(row.taskId) },
                            onAction = viewModel::performAction
                        )
                    }
                }
            }
        }
    }

    selectedDetail?.let { row ->
        RomulusAlertDialog(
            onDismissRequest = viewModel::dismissDetails,
            title = { RomulusDialogTitle(row.originalDisplayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RomulusDialogBodyText("Source entry: ${row.details.sourceEntry}")
                    RomulusDialogBodyText("Original file: ${row.originalDisplayName}")
                    RomulusDialogBodyText("Output target: ${row.details.outputSummary}")
                    RomulusDialogBodyText("Created: ${row.createdAt.formatTimestamp()}")
                    RomulusDialogBodyText("Updated: ${row.details.updatedAt.formatTimestamp()}")
                    RomulusDialogBodyText(
                        text = "File size: " +
                            row.details.originalSizeBytes.formatByteCountOrUnknown()
                    )
                    RomulusDialogBodyText(
                        "Download sub-folder: ${row.details.outputSubfolder ?: "N/A"}"
                    )
                    RomulusDialogBodyText("State: ${row.details.currentState}")
                    RomulusDialogBodyText(
                        "Failure reason: ${row.details.failureReason ?: "N/A"}"
                    )
                    RomulusDialogBodyText("Part: ${row.details.partLabel ?: "N/A"}")
                    row.details.attemptCount?.let { attemptCount ->
                        RomulusDialogBodyText("Attempt count: $attemptCount")
                    }
                    row.details.preparing?.let { preparing ->
                        RomulusDialogBodyText(
                            "Preparing started: ${preparing.enteredAt.formatTimestamp()}"
                        )
                        RomulusDialogBodyText(
                            "Timeout deadline: ${preparing.timeoutAt.formatTimestamp()}"
                        )
                        RomulusDialogBodyText(
                            "Provider status: ${preparing.lastProviderStatus ?: "N/A"}"
                        )
                        RomulusDialogBodyText(
                            text = "Provider progress: ${
                                preparing.lastProviderProgress?.toInt()?.let { progress ->
                                    "$progress%"
                                } ?: "N/A"
                            }"
                        )
                    }
                    row.details.transfer?.let { transfer ->
                        val transferSummary = buildTransferBytesLabel(
                            downloadedBytes = transfer.downloadedBytes,
                            totalBytes = transfer.totalBytes
                        )
                        RomulusDialogBodyText(
                            text = "Transfer progress: ${transfer.progressPercentLabel} " +
                                "($transferSummary)"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDetails) {
                    RomulusDialogActionText("Close")
                }
            }
        )
    }

    state.clearHistoryDialog?.let { dialog ->
        RomulusAlertDialog(
            onDismissRequest = viewModel::dismissClearHistory,
            title = { RomulusDialogTitle("Clear history") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RomulusDialogBodyText("Hide completed and cancelled downloads from the list?")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RomulusDialogBodyText("Include failed")
                        Checkbox(
                            checked = dialog.includeFailed,
                            onCheckedChange = viewModel::updateClearHistoryIncludeFailed
                        )
                    }
                    dialog.errorMessage?.let { message ->
                        RomulusDialogBodyText(
                            text = message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmClearHistory(dialog.includeFailed) }
                ) {
                    RomulusDialogActionText("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearHistory) {
                    RomulusDialogActionText("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DownloadRow(
    row: DownloadRowViewState,
    onOpenDetails: () -> Unit,
    onAction: (QueueActionCommand) -> Unit
) {
    var menuExpanded by remember(row.taskId) { mutableStateOf(false) }

    TableDataRow {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = row.originalDisplayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RomulusIconButton(
                    modifier = Modifier.size(28.dp),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    outlined = false,
                    onClick = onOpenDetails
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Show download details"
                    )
                }
                Text(
                    text = row.details.originalSizeBytes.formatByteCountOrUnknown(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .width(110.dp)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            row.progressLabel?.let { progressLabel ->
                Text(
                    text = progressLabel,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            StatusTag(
                label = row.stateLabel,
                state = row.state
            )
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            RomulusIconButton(
                modifier = Modifier.size(28.dp),
                onClick = { menuExpanded = true }
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More actions"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = warmOverlayColor(),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                orderedActions(row.allowedActions).forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAction(action.toCommand(row.taskId))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTag(label: String, state: QueuePresentationState) {
    val containerColor = when (state) {
        QueuePresentationState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        QueuePresentationState.FAILED,
        QueuePresentationState.CANCELLED -> MaterialTheme.colorScheme.errorContainer
        QueuePresentationState.RUNNING,
        QueuePresentationState.PREPARING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val contentColor = when (state) {
        QueuePresentationState.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
        QueuePresentationState.FAILED,
        QueuePresentationState.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
        QueuePresentationState.RUNNING,
        QueuePresentationState.PREPARING -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

private data class DownloadActionItem(val kind: QueueActionKind, val label: String) {
    fun toCommand(taskId: TaskId): QueueActionCommand = when (kind) {
        QueueActionKind.PAUSE -> QueueActionCommand.Pause(taskId = taskId)
        QueueActionKind.RESUME -> QueueActionCommand.Resume(taskId = taskId)
        QueueActionKind.CANCEL -> QueueActionCommand.Cancel(taskId = taskId)
        QueueActionKind.RETRY -> QueueActionCommand.Retry(taskId = taskId)
        QueueActionKind.RESTART -> QueueActionCommand.Restart(taskId = taskId)
    }
}

@Suppress("FunctionExpressionBody")
private fun orderedActions(actions: Set<QueueActionKind>): List<DownloadActionItem> {
    val orderedKinds = listOf(
        QueueActionKind.PAUSE to "Pause",
        QueueActionKind.RESUME to "Resume",
        QueueActionKind.CANCEL to "Cancel",
        QueueActionKind.RETRY to "Retry",
        QueueActionKind.RESTART to "Restart"
    )
    return orderedKinds.mapNotNull { (kind, label) ->
        if (kind in actions) {
            DownloadActionItem(kind = kind, label = label)
        } else {
            null
        }
    }
}

private fun buildSummaryLabel(completed: Int, total: Int, failed: Int, cancelled: Int): String =
    if (total == 0) {
        ""
    } else {
        buildString {
            append("$completed/$total completed")
            if (failed > 0) {
                append(" • $failed failed")
            }
            if (cancelled > 0) {
                append(" • $cancelled cancelled")
            }
        }
    }

private fun buildTransferBytesLabel(downloadedBytes: Long, totalBytes: Long?): String {
    val downloaded = downloadedBytes.formatByteCount()
    val total = totalBytes?.formatByteCount() ?: "Unknown size"
    return "$downloaded / $total"
}
