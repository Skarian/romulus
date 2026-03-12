@file:Suppress("LongMethod")

package com.romulus.mobile.ui.home

import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romulus.mobile.ui.components.TableDataRow
import com.romulus.mobile.ui.components.TableHeaderRow
import com.romulus.mobile.ui.files.FilesRouteArgs
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlinx.coroutines.flow.collect

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenFiles: (FilesRouteArgs) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is HomeEffect.RefreshFeedback) {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ResponsiveScreenContainer(modifier = modifier.fillMaxSize()) { metrics ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = viewModel::openSearch) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search source")
                }
                if (state.refreshVisible) {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh source"
                        )
                    }
                }
            }

            when (val mode = state.mode) {
                is HomeMode.InvalidSettings -> {
                    Text(
                        text = "Configuration requires attention in Settings: " +
                            mode.broken.joinToString(),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is HomeMode.SourceLoadError -> {
                    Text(
                        text = mode.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (mode.retryVisible) {
                        TextButton(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                is HomeMode.Content -> {
                    mode.warning?.let { warning ->
                        Text(
                            text = when (warning) {
                                is HomeContentWarning.LatestRefreshFailed -> warning.message
                                is HomeContentWarning.MissingSnapshotFallback -> warning.message
                            },
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

                    if (mode.rows.isEmpty()) {
                        Text("No sources available")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mode.rows, key = { it.routeArgs.entryId.value }) { row ->
                                TableDataRow(onClick = { onOpenFiles(row.routeArgs) }) {
                                    Text(
                                        text = row.displayName,
                                        modifier = Modifier.weight(0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = row.folderContext.ifBlank { "-" },
                                        modifier = Modifier.weight(0.3f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.searchDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeSearch,
            title = { Text("Search") },
            text = {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Find source entry") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeSearch) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSearch()
                        viewModel.closeSearch()
                    }
                ) {
                    Text("Clear")
                }
            }
        )
    }
}
