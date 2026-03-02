package com.romulus.mobile.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.domain.downloads.DownloadState
import java.text.DateFormat
import java.util.Date

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTaskEntity>,
    onRetry: (String) -> Unit,
    onRestart: (String) -> Unit,
    onCancel: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDeletePartial: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    val active = tasks.filter { it.state.isActive }
    val terminal = tasks.filter { it.state.isTerminal }
    var selected by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var pendingPartialDelete by remember { mutableStateOf<DownloadTaskEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Active downloads", style = MaterialTheme.typography.titleLarge)
        if (active.isEmpty()) {
            Text("No active downloads")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(active, key = { it.id }) { task ->
                DownloadRow(
                    task = task,
                    onClick = { selected = task },
                    onRetry = onRetry,
                    onRestart = onRestart,
                    onCancel = onCancel,
                    onPause = onPause,
                    onResume = onResume,
                    onDeletePartial = { pendingPartialDelete = task }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Completed", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onClearCompleted, enabled = terminal.isNotEmpty()) {
                Text("Clear completed")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(terminal, key = { it.id }) { task ->
                DownloadRow(
                    task = task,
                    onClick = { selected = task },
                    onRetry = onRetry,
                    onRestart = onRestart,
                    onCancel = onCancel,
                    onPause = onPause,
                    onResume = onResume,
                    onDeletePartial = { pendingPartialDelete = task }
                )
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
                    Text("Date completed: ${task.completedAtLabel()}")
                    Text("File size: ${task.sizeBytes} bytes")
                    Text("Download sub-folder: ${task.subfolder}")
                    Text("State: ${task.state.humanName()}")
                    Text("Failure reason: ${task.lastFailureReason ?: "N/A"}")
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
private fun DownloadRow(
    task: DownloadTaskEntity,
    onClick: () -> Unit,
    onRetry: (String) -> Unit,
    onRestart: (String) -> Unit,
    onCancel: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDeletePartial: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(task.displayFilename, style = MaterialTheme.typography.titleMedium)
        Text(task.sourceDisplayName, style = MaterialTheme.typography.bodySmall)
        Text("${task.bytesDownloaded}/${task.totalBytes ?: 0} bytes")
        Text(task.state.humanName())

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (task.state) {
                DownloadState.QUEUED,
                DownloadState.RESOLVING_LINK,
                DownloadState.RETRY_SCHEDULED,
                DownloadState.RETRYING -> Button(onClick = { onCancel(task.id) }) { Text("Cancel") }

                DownloadState.RUNNING -> {
                    Button(onClick = { onPause(task.id) }) { Text("Pause") }
                    Button(onClick = { onCancel(task.id) }) { Text("Cancel") }
                }

                DownloadState.PAUSED -> {
                    Button(onClick = { onResume(task.id) }) { Text("Resume") }
                    Button(onClick = { onCancel(task.id) }) { Text("Cancel") }
                    if (task.partialExists) {
                        Button(onClick = { onDeletePartial(task.id) }) { Text("Delete partial") }
                    }
                }

                DownloadState.FAILED -> {
                    Button(onClick = { onRetry(task.id) }) { Text("Retry") }
                    Button(onClick = { onRestart(task.id) }) { Text("Restart") }
                    if (task.partialExists) {
                        Button(onClick = { onDeletePartial(task.id) }) { Text("Delete partial") }
                    }
                }

                DownloadState.CANCELLED -> {
                    Button(onClick = { onRestart(task.id) }) { Text("Restart") }
                    if (task.partialExists) {
                        Button(onClick = { onDeletePartial(task.id) }) { Text("Delete partial") }
                    }
                }

                DownloadState.COMPLETED -> {
                    Button(onClick = { onRestart(task.id) }) { Text("Restart") }
                }
            }
        }
    }
}

private fun DownloadTaskEntity.completedAtLabel(): String {
    if (state != DownloadState.COMPLETED) return "N/A"
    return DateFormat.getDateTimeInstance().format(Date(updatedAtEpochMs))
}

private fun DownloadState.humanName(): String {
    return when (this) {
        DownloadState.QUEUED -> "Queued"
        DownloadState.RESOLVING_LINK -> "Resolving link"
        DownloadState.RUNNING -> "Running"
        DownloadState.PAUSED -> "Paused"
        DownloadState.RETRY_SCHEDULED -> "Retry scheduled"
        DownloadState.RETRYING -> "Retrying"
        DownloadState.COMPLETED -> "Completed"
        DownloadState.FAILED -> "Failed"
        DownloadState.CANCELLED -> "Cancelled"
    }
}
