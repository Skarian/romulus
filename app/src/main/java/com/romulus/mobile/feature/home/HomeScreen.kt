package com.romulus.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romulus.mobile.domain.home.HomeRow
import com.romulus.mobile.ui.components.TableDataRow
import com.romulus.mobile.ui.components.TableHeaderRow
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer

@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    validationSummary: String?,
    showRefresh: Boolean,
    applyRenameByDefault: Boolean,
    onApplyRenameByDefaultChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEntryClick: (Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val normalizedQuery = query.trim().lowercase()
    val filteredRows = rows.filter { row ->
        normalizedQuery.isBlank() ||
            row.title.lowercase().contains(normalizedQuery) ||
            row.subtitle.lowercase().contains(normalizedQuery)
    }

    ResponsiveScreenContainer(modifier = Modifier.fillMaxSize()) { metrics ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showSearchDialog = true }) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search entries")
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "List settings")
                }
                if (showRefresh) {
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh source")
                    }
                }
            }

            if (!validationSummary.isNullOrBlank()) {
                Text(
                    text = validationSummary,
                    color = MaterialTheme.colorScheme.error
                )
            }

            TableHeaderRow {
                Text(
                    text = "Source",
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Folder",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (filteredRows.isEmpty()) {
                Text(
                    text = "No valid files",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredRows, key = { it.entryIndex }) { row ->
                        TableDataRow(onClick = { onEntryClick(row.entryIndex) }) {
                            Text(
                                text = row.title,
                                modifier = Modifier.weight(0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = row.subtitle.ifBlank { "-" },
                                modifier = Modifier.weight(0.3f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search") },
            text = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find source entry") },
                    modifier = Modifier.fillMaxWidth(),
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
            title = { Text("Selection preferences") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Apply rename by default")
                    Switch(
                        checked = applyRenameByDefault,
                        onCheckedChange = onApplyRenameByDefaultChanged
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
}
