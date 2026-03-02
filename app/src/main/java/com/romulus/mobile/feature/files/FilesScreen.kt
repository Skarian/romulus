package com.romulus.mobile.feature.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romulus.mobile.data.downloads.QueuedDownload
import com.romulus.mobile.domain.files.FileOption
import com.romulus.mobile.domain.source.SourceEntry
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    snapshotId: String,
    entry: SourceEntry,
    onResolveFiles: suspend (SourceEntry) -> Result<List<FileOption>>,
    onEnqueue: suspend (List<QueuedDownload>) -> Unit,
    onBackToHome: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var applyRename by remember { mutableStateOf(true) }
    val files = remember { mutableStateListOf<FileOption>() }
    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(entry.index) {
        isLoading = true
        error = null
        val result = onResolveFiles(entry)
        result.onSuccess {
            files.clear()
            files.addAll(it)
            selectedIds.clear()
            isLoading = false
        }.onFailure {
            error = it.message ?: "Unable to resolve file list"
            isLoading = false
        }
    }

    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(text = "Resolving files for ${entry.displayName}")
        }
        return
    }

    val filtered = files
        .map { option ->
            val displayName = if (applyRename) option.defaultDisplayName else option.originalName
            option to displayName
        }
        .filter { (option, displayName) ->
            if (query.isBlank()) true else {
                val needle = query.trim().lowercase()
                displayName.lowercase().contains(needle) || option.originalName.lowercase().contains(needle)
            }
        }
        .sortedBy { (_, displayName) -> displayName.lowercase() }

    val selectedOutputNames = filtered
        .filter { (option, _) -> selectedIds.contains(option.uniqueId()) }
        .map { (_, displayName) -> displayName }
    val duplicateSelectedNames = selectedOutputNames
        .groupBy { it.lowercase() }
        .filterValues { it.size > 1 }
        .keys

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = entry.displayName, style = MaterialTheme.typography.headlineSmall)
        if (!error.isNullOrBlank()) {
            Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search files") }
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Apply rename")
            Switch(checked = applyRename, onCheckedChange = { applyRename = it })
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                selectedIds.clear()
                selectedIds.addAll(filtered.map { it.first.uniqueId() })
            }) {
                Text("Select all")
            }
            Button(onClick = { selectedIds.clear() }) {
                Text("Select none")
            }
        }

        if (duplicateSelectedNames.isNotEmpty()) {
            Text(
                text = "Selected files include duplicate output names and will be auto-suffixed.",
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered) { (option, displayName) ->
                val uniqueId = option.uniqueId()
                val checked = selectedIds.contains(uniqueId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = displayName)
                        Text(text = option.originalName, style = MaterialTheme.typography.bodySmall)
                        Text(text = option.partName, style = MaterialTheme.typography.labelSmall)
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (!selectedIds.contains(uniqueId)) selectedIds.add(uniqueId)
                            } else {
                                selectedIds.remove(uniqueId)
                            }
                        }
                    )
                }
            }
        }

        Button(
            enabled = selectedIds.isNotEmpty(),
            onClick = {
                scope.launch {
                    val selectedOptions = filtered
                        .filter { (option, _) -> selectedIds.contains(option.uniqueId()) }
                        .map { (option, displayName) ->
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
                    onEnqueue(selectedOptions)
                    onBackToHome()
                }
            }
        ) {
            Text("Download")
        }
    }
}

private fun FileOption.uniqueId(): String {
    return "$magnetUrl#$torrentFileId#$originalName#$sizeBytes"
}
