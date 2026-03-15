@file:Suppress("LongMethod")

package com.romulus.mobile.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romulus.mobile.ui.components.RomulusAlertDialog
import com.romulus.mobile.ui.components.RomulusButtonText
import com.romulus.mobile.ui.components.RomulusDialogActionText
import com.romulus.mobile.ui.components.RomulusDialogBodyText
import com.romulus.mobile.ui.components.RomulusDialogTitle
import com.romulus.mobile.ui.components.RomulusFieldLabel
import com.romulus.mobile.ui.components.RomulusIconButton
import com.romulus.mobile.ui.components.TableDataRow
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
    var detailEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDetail = remember(state.mode, detailEntryId) {
        val mode = state.mode as? HomeMode.Content ?: return@remember null
        mode.rows.firstOrNull { row -> row.routeArgs.entryId.value == detailEntryId }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is HomeEffect.RefreshFeedback) {
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
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RomulusIconButton(onClick = viewModel::openSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search source"
                        )
                    }
                    if (state.refreshVisible) {
                        RomulusIconButton(onClick = viewModel::refresh) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh source"
                            )
                        }
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
                            RomulusButtonText("Retry")
                        }
                    }
                }

                is HomeMode.Content -> HomeContentSection(
                    mode = mode,
                    onOpenFiles = onOpenFiles,
                    onOpenDetails = { detailEntryId = it }
                )
            }
        }
    }

    selectedDetail?.let { row ->
        HomeSourceDetailsDialog(row = row, onDismiss = { detailEntryId = null })
    }

    if (state.searchDialogOpen) {
        HomeSearchDialog(
            searchQuery = state.searchQuery,
            onUpdateSearchQuery = viewModel::updateSearchQuery,
            onClose = viewModel::closeSearch,
            onClear = {
                viewModel.clearSearch()
                viewModel.closeSearch()
            }
        )
    }
}

@Composable
private fun ColumnScope.HomeContentSection(
    mode: HomeMode.Content,
    onOpenFiles: (FilesRouteArgs) -> Unit,
    onOpenDetails: (String) -> Unit
) {
    mode.warning?.let { warning ->
        Text(
            text = when (warning) {
                is HomeContentWarning.LatestRefreshFailed -> warning.message
                is HomeContentWarning.MissingSnapshotFallback -> warning.message
            },
            color = MaterialTheme.colorScheme.error
        )
    }

    if (mode.rows.isEmpty()) {
        Text(
            text = "No sources available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mode.rows, key = { it.routeArgs.entryId.value }) { row ->
                TableDataRow(onClick = { onOpenFiles(row.routeArgs) }) {
                    Text(
                        text = row.displayName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    RomulusIconButton(
                        modifier = Modifier.size(28.dp),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        outlined = false,
                        onClick = { onOpenDetails(row.routeArgs.entryId.value) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Show source details"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSourceDetailsDialog(row: HomeRowModel, onDismiss: () -> Unit) {
    RomulusAlertDialog(
        onDismissRequest = onDismiss,
        title = { RomulusDialogTitle(row.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RomulusDialogBodyText("Output folder: ${row.details.outputFolder}")
                RomulusDialogBodyText("Scope path: ${row.details.scopePath}")
                RomulusDialogBodyText(
                    text = "Include nested files: ${
                        if (row.details.includeNestedFiles) "Yes" else "No"
                    }"
                )
                RomulusDialogBodyText("Torrent parts: ${row.details.torrentPartCount}")
                if (row.details.partLabels.isNotEmpty()) {
                    RomulusDialogBodyText("Part labels: ${row.details.partLabels.joinToString()}")
                }
                RomulusDialogBodyText(
                    text = "Ignore rules: ${
                        row.details.ignoreRuleCount.takeIf { it > 0 } ?: "None"
                    }"
                )
                RomulusDialogBodyText(
                    text = "Rename rule: ${
                        if (row.details.renameAvailable) "On" else "Off"
                    }"
                )
                RomulusDialogBodyText(
                    text = "Unarchive: ${
                        if (row.details.unarchiveAvailable) "On" else "Off"
                    }"
                )
                row.details.recursiveUnarchiveDefault?.let { recursive ->
                    RomulusDialogBodyText(
                        text = "Recursive unarchive default: ${
                            if (recursive) "On" else "Off"
                        }"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                RomulusDialogActionText("Close")
            }
        }
    )
}

@Composable
private fun HomeSearchDialog(
    searchQuery: String,
    onUpdateSearchQuery: (String) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    RomulusAlertDialog(
        onDismissRequest = onClose,
        title = { RomulusDialogTitle("Search") },
        text = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onUpdateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { RomulusFieldLabel("Find source entry") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                RomulusDialogActionText("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                RomulusDialogActionText("Clear")
            }
        }
    )
}
