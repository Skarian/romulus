package com.romulus.mobile.feature.files

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.romulus.mobile.data.downloads.QueuedDownload
import com.romulus.mobile.domain.files.FileOption
import com.romulus.mobile.domain.source.SourceEntry
import com.romulus.mobile.ui.components.TableDataRow
import com.romulus.mobile.ui.components.TableHeaderRow
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import com.romulus.mobile.ui.layout.rememberResponsiveMetrics
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FilesScreen(
    snapshotId: String,
    entry: SourceEntry,
    applyRenameByDefault: Boolean,
    onApplyRenameByDefaultChanged: (Boolean) -> Unit,
    onResolveFiles: suspend (SourceEntry) -> Result<List<FileOption>>,
    onEnqueue: suspend (List<QueuedDownload>) -> Unit,
    onDownloadsStarted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val filesViewModel: FilesScreenViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel()
    }

    val scope = rememberCoroutineScope()
    val entryKey = remember(snapshotId, entry.index) { "$snapshotId:${entry.index}" }

    LaunchedEffect(snapshotId) {
        filesViewModel.updateActiveSnapshot(snapshotId)
    }
    LaunchedEffect(entryKey) {
        filesViewModel.ensureLoaded(entryKey, entry, onResolveFiles)
    }

    val resolution = filesViewModel.stateFor(entryKey)

    var query by rememberSaveable(entryKey) { mutableStateOf("") }
    var applyRename by rememberSaveable(entryKey) { mutableStateOf(applyRenameByDefault) }
    var selectedIds by rememberSaveable(entryKey) { mutableStateOf(setOf<String>()) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var selectedDetails by remember { mutableStateOf<FileOption?>(null) }

    val allCurrentIds = resolution.files.map { option -> option.uniqueId() }.toSet()
    LaunchedEffect(allCurrentIds) {
        selectedIds = selectedIds.filterTo(linkedSetOf()) { id -> id in allCurrentIds }
    }

    val normalizedQuery = query.trim().lowercase()
    val renderedFiles = resolution.files
        .map { option ->
            option to if (applyRename) option.defaultDisplayName else option.originalName
        }
        .filter { (option, displayName) ->
            normalizedQuery.isBlank() ||
                displayName.lowercase().contains(normalizedQuery) ||
                option.originalName.lowercase().contains(normalizedQuery)
        }
        .sortedBy { (_, displayName) -> displayName.lowercase(Locale.US) }

    val visibleIds = renderedFiles.map { it.first.uniqueId() }.toSet()

    val selectedRendered = resolution.files
        .map { option -> option to if (applyRename) option.defaultDisplayName else option.originalName }
        .filter { (option, _) -> selectedIds.contains(option.uniqueId()) }

    val duplicateSelectedNames = selectedRendered
        .map { (_, displayName) -> displayName }
        .groupBy { it.lowercase(Locale.US) }
        .filterValues { it.size > 1 }
        .keys
    val hasResolutionError = !resolution.error.isNullOrBlank()

    val downloadSelected: () -> Unit = {
        scope.launch {
            val selectedOptions = resolution.files
                .filter { option -> selectedIds.contains(option.uniqueId()) }
                .sortedBy { option ->
                    if (applyRename) {
                        option.defaultDisplayName.lowercase(Locale.US)
                    } else {
                        option.originalName.lowercase(Locale.US)
                    }
                }
                .map { option ->
                    val displayName = if (applyRename) option.defaultDisplayName else option.originalName
                    QueuedDownload(
                        snapshotId = snapshotId,
                        entryIndex = entry.index,
                        sourceDisplayName = entry.displayName,
                        partIndex = option.partIndex,
                        originalFilename = option.originalName,
                        sizeBytes = option.sizeBytes,
                        displayFilename = displayName,
                        subfolder = entry.subfolder,
                        magnetUrl = option.magnetUrl,
                        torrentFileId = option.torrentFileId,
                        partName = option.partName
                    )
                }

            if (selectedOptions.isNotEmpty()) {
                onEnqueue(selectedOptions)
                onDownloadsStarted()
            }
        }
        Unit
    }

    if (resolution.isLoading) {
        ResponsiveScreenContainer(verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator()
                    Text(text = "Resolving files for ${entry.displayName}")
                }
            }
        }
        return
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasResolutionError) {
                    IconButton(
                        modifier = Modifier.size(30.dp),
                        onClick = { showErrorDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Show file warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(
                    modifier = Modifier.size(30.dp),
                    onClick = { showSearchDialog = true }
                ) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search files")
                }
                IconButton(
                    modifier = Modifier.size(30.dp),
                    onClick = { showSettingsDialog = true }
                ) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "File list settings")
                }
                IconButton(
                    modifier = Modifier.size(30.dp),
                    enabled = selectedIds.isNotEmpty(),
                    onClick = downloadSelected
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Start downloads",
                        tint = if (selectedIds.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            TableHeaderRow {
                Text(
                    text = "File",
                    modifier = Modifier.weight(0.82f),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.weight(0.18f),
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
                            val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { id -> selectedIds.contains(id) }
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
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

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(renderedFiles, key = { it.first.uniqueId() }) { (option, displayName) ->
                    val uniqueId = option.uniqueId()
                    TableDataRow {
                        Column(
                            modifier = Modifier.weight(0.82f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = displayName,
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
                                    onClick = { selectedDetails = option }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Show file details"
                                    )
                                }
                                Text(
                                    text = option.sizeBytes.formatBytes(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(0.18f)
                                .align(Alignment.CenterVertically),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(uniqueId),
                                onCheckedChange = { isChecked ->
                                    selectedIds = if (isChecked) {
                                        selectedIds + uniqueId
                                    } else {
                                        selectedIds - uniqueId
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (duplicateSelectedNames.isNotEmpty()) {
                Text(
                    text = "Duplicate names selected; auto-suffixing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search files") },
            text = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find file") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    query = ""
                    showSearchDialog = false
                }) {
                    Text("Clear")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("File preferences") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Apply rename")
                    Switch(
                        checked = applyRename,
                        onCheckedChange = { next ->
                            applyRename = next
                            onApplyRenameByDefaultChanged(next)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showErrorDialog && hasResolutionError) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Resolution warning") },
            text = { Text(resolution.error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    selectedDetails?.let { option ->
        val displayName = if (applyRename) option.defaultDisplayName else option.originalName
        AlertDialog(
            onDismissRequest = { selectedDetails = null },
            title = { Text(displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Original name: ${option.originalName}")
                    Text("Part: ${option.partName}")
                    Text("Size: ${option.sizeBytes.formatBytes()}")
                    Text("Torrent file id: ${option.torrentFileId}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun FileOption.uniqueId(): String {
    return "$magnetUrl#$torrentFileId#$originalName#$sizeBytes"
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
