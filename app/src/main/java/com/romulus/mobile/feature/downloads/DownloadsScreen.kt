package com.romulus.mobile.feature.downloads

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.domain.downloads.DownloadState
import com.romulus.mobile.ui.components.TableDataRow
import com.romulus.mobile.ui.components.TableHeaderRow
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import com.romulus.mobile.ui.layout.rememberResponsiveMetrics
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTaskEntity>,
    onRetry: (String) -> Unit,
    onRestart: (String) -> Unit,
    onCancel: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDeletePartial: (String) -> Unit
) {
    val orderedTasks = tasks.sortedWith(
        compareByDescending<DownloadTaskEntity> { it.queueIndex }
            .thenByDescending { it.createdAtEpochMs }
    )
    var selected by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var pendingPartialDelete by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    val progressColumnWidth = 110.dp
    val menuColumnWidth = 40.dp
    val baseMetrics = rememberResponsiveMetrics()
    val compactMetrics = baseMetrics.copy(
        verticalPadding = maxOf(8.dp, baseMetrics.verticalPadding - 8.dp),
        contentSpacing = maxOf(4.dp, baseMetrics.contentSpacing - 4.dp)
    )

    ResponsiveScreenContainer(
        modifier = Modifier.fillMaxSize(),
        metrics = compactMetrics,
        verticalArrangement = Arrangement.spacedBy(compactMetrics.contentSpacing)
    ) { metrics ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            TableHeaderRow {
                Text(
                    text = "File",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Progress",
                    modifier = Modifier.width(progressColumnWidth),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "",
                    modifier = Modifier.width(menuColumnWidth),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (orderedTasks.isEmpty()) {
                Text("No downloads yet")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(orderedTasks, key = { it.id }) { task ->
                        var menuExpanded by remember(task.id) { mutableStateOf(false) }
                        TableDataRow {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = task.displayFilename,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        modifier = Modifier.size(24.dp),
                                        onClick = { selected = task }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = "Show download details"
                                        )
                                    }
                                    Text(
                                        text = task.sizeBytes.formatBytes(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .width(progressColumnWidth)
                                    .align(Alignment.CenterVertically),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = task.progressPercentLabel(),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                StatusTag(task.state)
                            }

                            Box(
                                modifier = Modifier
                                    .width(menuColumnWidth)
                                    .align(Alignment.CenterVertically),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    modifier = Modifier.size(24.dp),
                                    onClick = { menuExpanded = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More actions"
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    when (task.state) {
                                        DownloadState.QUEUED,
                                        DownloadState.RESOLVING_LINK,
                                        DownloadState.RETRY_SCHEDULED,
                                        DownloadState.RETRYING -> {
                                            DropdownMenuItem(
                                                text = { Text("Cancel") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onCancel(task.id)
                                                }
                                            )
                                        }

                                        DownloadState.RUNNING -> {
                                            DropdownMenuItem(
                                                text = { Text("Pause") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onPause(task.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Cancel") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onCancel(task.id)
                                                }
                                            )
                                        }

                                        DownloadState.PAUSED -> {
                                            DropdownMenuItem(
                                                text = { Text("Resume") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onResume(task.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Cancel") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onCancel(task.id)
                                                }
                                            )
                                            if (task.partialExists) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete partial") },
                                                    onClick = {
                                                        menuExpanded = false
                                                        pendingPartialDelete = task
                                                    }
                                                )
                                            }
                                        }

                                        DownloadState.FAILED -> {
                                            DropdownMenuItem(
                                                text = { Text("Retry") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onRetry(task.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Restart") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onRestart(task.id)
                                                }
                                            )
                                            if (task.partialExists) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete partial") },
                                                    onClick = {
                                                        menuExpanded = false
                                                        pendingPartialDelete = task
                                                    }
                                                )
                                            }
                                        }

                                        DownloadState.CANCELLED -> {
                                            DropdownMenuItem(
                                                text = { Text("Restart") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onRestart(task.id)
                                                }
                                            )
                                            if (task.partialExists) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete partial") },
                                                    onClick = {
                                                        menuExpanded = false
                                                        pendingPartialDelete = task
                                                    }
                                                )
                                            }
                                        }

                                        DownloadState.COMPLETED -> {
                                            DropdownMenuItem(
                                                text = { Text("Restart") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onRestart(task.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { task ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(task.displayFilename) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Source entry: ${task.sourceDisplayName}")
                    Text("Original file: ${task.originalFilename}")
                    Text("Modified file: ${task.displayFilename}")
                    Text("Created: ${task.createdAtLabel()}")
                    Text("Updated: ${task.updatedAtLabel()}")
                    Text("File size: ${task.sizeBytes.formatBytes()}")
                    Text("Download progress: ${task.progressPercentLabel()} (${task.progressBytesLabel()})")
                    Text("Download sub-folder: ${task.subfolder}")
                    Text("State: ${task.state.humanName()}")
                    Text("Failure reason: ${task.lastFailureReason ?: "N/A"}")
                    Text("Part: ${task.partName ?: "N/A"}")
                    if (task.state == DownloadState.FAILED || task.state == DownloadState.RETRY_SCHEDULED || task.state == DownloadState.RETRYING) {
                        Text("Attempt: ${task.attemptCount} of ${task.maxAttempts}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Close")
                }
            }
        )
    }

    pendingPartialDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingPartialDelete = null },
            title = { Text("Delete partial file?") },
            text = {
                Text("Delete partial data for ${task.displayFilename}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePartial(task.id)
                        pendingPartialDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPartialDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusTag(state: DownloadState) {
    val (containerColor, contentColor) = when (state) {
        DownloadState.RUNNING,
        DownloadState.RETRYING,
        DownloadState.RESOLVING_LINK,
        DownloadState.QUEUED,
        DownloadState.RETRY_SCHEDULED,
        DownloadState.PAUSED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

        DownloadState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DownloadState.FAILED,
        DownloadState.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(text = state.humanName(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

private fun DownloadTaskEntity.progressPercentLabel(): String {
    val total = totalBytes?.takeIf { it > 0L } ?: return "--%"
    val percent = ((bytesDownloaded.coerceAtLeast(0L) * 100L) / total)
        .coerceIn(0L, 100L)
        .toInt()
    return "$percent%"
}

private fun DownloadTaskEntity.progressBytesLabel(): String {
    val downloaded = bytesDownloaded.formatBytes()
    val total = totalBytes?.takeIf { it > 0L }?.formatBytes() ?: "??"
    return "$downloaded/$total"
}

private fun DownloadTaskEntity.createdAtLabel(): String {
    return DateFormat.getDateTimeInstance().format(Date(createdAtEpochMs))
}

private fun DownloadTaskEntity.updatedAtLabel(): String {
    return DateFormat.getDateTimeInstance().format(Date(updatedAtEpochMs))
}

private fun DownloadState.humanName(): String {
    return when (this) {
        DownloadState.QUEUED -> "Queued"
        DownloadState.RESOLVING_LINK -> "Resolving"
        DownloadState.RUNNING -> "Running"
        DownloadState.PAUSED -> "Paused"
        DownloadState.RETRY_SCHEDULED -> "Retry scheduled"
        DownloadState.RETRYING -> "Retrying"
        DownloadState.COMPLETED -> "Completed"
        DownloadState.FAILED -> "Failed"
        DownloadState.CANCELLED -> "Cancelled"
    }
}

private fun Long.formatBytes(): String {
    val safe = coerceAtLeast(0L)
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.1f KB", safe / kb)
        else -> "$safe B"
    }
}
