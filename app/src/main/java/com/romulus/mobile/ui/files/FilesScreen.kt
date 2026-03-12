@file:Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")

package com.romulus.mobile.ui.files

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.ui.components.TableDataRow
import com.romulus.mobile.ui.components.TableHeaderRow
import com.romulus.mobile.ui.formatByteCountOrUnknown
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlinx.coroutines.launch

@Suppress("UnusedParameter")
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searchDialogOpen by rememberSaveable { mutableStateOf(false) }
    var preferencesDialogOpen by rememberSaveable { mutableStateOf(false) }
    var warningDialogOpen by rememberSaveable { mutableStateOf(false) }
    var detailItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDetail = remember(state.rows, detailItemId) {
        state.rows.firstOrNull { row -> row.itemId.value == detailItemId }
    }

    ResponsiveScreenContainer(modifier = modifier.fillMaxSize()) { metrics ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.mode == FilesMode.ARCHIVE_SELECTION) {
                        "Archive files"
                    } else {
                        "Files"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.resolverError != null) {
                        IconButton(
                            modifier = Modifier.size(30.dp),
                            onClick = { warningDialogOpen = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Show resolution warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(30.dp),
                        onClick = { searchDialogOpen = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search files"
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(30.dp),
                        onClick = { preferencesDialogOpen = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "File preferences"
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(30.dp),
                        enabled = state.selectedIds.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                when (val result = viewModel.queueSelected()) {
                                    is EnqueueResult.Enqueued -> {
                                        Toast.makeText(
                                            context,
                                            queuedMessage(result.taskIds.size),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onNavigateToDownloads()
                                    }

                                    is EnqueueResult.EnqueuedPendingDispatch -> {
                                        Toast.makeText(
                                            context,
                                            result.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onNavigateToDownloads()
                                    }

                                    is EnqueueResult.Rejected -> {
                                        Toast.makeText(
                                            context,
                                            result.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    is EnqueueResult.Failed -> {
                                        Toast.makeText(
                                            context,
                                            result.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Start downloads"
                        )
                    }
                }
            }

            state.resolverError?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::retryResolve) {
                        Text("Retry")
                    }
                }
            }

            TableHeaderRow {
                Text(
                    text = "File",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.weight(0.2f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select",
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(
                        modifier = Modifier.size(26.dp),
                        onClick = {
                            val visibleIds = state.rows.map { row -> row.itemId }
                            val allVisibleSelected = visibleIds.isNotEmpty() &&
                                visibleIds.all { itemId -> itemId in state.selectedIds }
                            if (allVisibleSelected) {
                                viewModel.deselectVisible()
                            } else {
                                viewModel.selectAllVisible()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SelectAll,
                            contentDescription = "Toggle all visible rows"
                        )
                    }
                }
            }

            if (state.rows.isEmpty() && state.resolverError == null) {
                Text("No files available")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.rows, key = { row -> row.itemId.value }) { row ->
                        TableDataRow {
                            Column(
                                modifier = Modifier.weight(0.8f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = row.originalDisplayName,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        modifier = Modifier.size(24.dp),
                                        onClick = { detailItemId = row.itemId.value }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = "Show file details"
                                        )
                                    }
                                    Text(
                                        text = row.sizeBytes.formatByteCountOrUnknown(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .weight(0.2f)
                                    .align(Alignment.CenterVertically),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = row.itemId in state.selectedIds,
                                    onCheckedChange = { checked ->
                                        if (checked != (row.itemId in state.selectedIds)) {
                                            viewModel.toggleSelection(row.itemId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (searchDialogOpen) {
        AlertDialog(
            onDismissRequest = { searchDialogOpen = false },
            title = { Text("Search files") },
            text = {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Find file") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { searchDialogOpen = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSearchQuery("")
                        searchDialogOpen = false
                    }
                ) {
                    Text("Clear")
                }
            }
        )
    }

    if (preferencesDialogOpen) {
        AlertDialog(
            onDismissRequest = { preferencesDialogOpen = false },
            title = { Text("File preferences") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.preferences.renameAvailable) {
                        PreferenceToggleRow(
                            label = "Apply rename",
                            checked = state.preferences.applyRename,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences(
                                    state.preferences.copy(applyRename = enabled)
                                )
                            }
                        )
                    }
                    if (state.preferences.unarchiveAvailable) {
                        PreferenceToggleRow(
                            label = "Unarchive",
                            checked = state.preferences.unarchiveEnabled,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences(
                                    state.preferences.copy(unarchiveEnabled = enabled)
                                )
                            }
                        )
                    }
                    if (state.preferences.recursiveUnarchiveAvailable) {
                        PreferenceToggleRow(
                            label = "Recursive unarchive",
                            checked = state.preferences.unarchiveEnabled &&
                                state.preferences.recursiveUnarchiveEnabled,
                            enabled = state.preferences.unarchiveEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePreferences(
                                    state.preferences.copy(
                                        recursiveUnarchiveEnabled = enabled
                                    )
                                )
                            }
                        )
                    }
                    if (!state.preferences.renameAvailable &&
                        !state.preferences.unarchiveAvailable &&
                        !state.preferences.recursiveUnarchiveAvailable
                    ) {
                        Text("No configurable file preferences are available for this source.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { preferencesDialogOpen = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (warningDialogOpen && state.resolverError != null) {
        AlertDialog(
            onDismissRequest = { warningDialogOpen = false },
            title = { Text("Resolution warning") },
            text = { Text(state.resolverError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { warningDialogOpen = false }) {
                    Text("Close")
                }
            }
        )
    }

    selectedDetail?.let { row ->
        AlertDialog(
            onDismissRequest = { detailItemId = null },
            title = { Text(row.originalDisplayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Original name: ${row.originalDisplayName}")
                    Text("Size: ${row.sizeBytes.formatByteCountOrUnknown()}")
                    Text("Part: ${row.partLabel ?: "N/A"}")
                    Text("Torrent file id: ${row.providerFileId ?: "N/A"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { detailItemId = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private fun queuedMessage(count: Int): String = if (count == 1) {
    "Started 1 download."
} else {
    "Started $count downloads."
}
