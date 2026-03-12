@file:Suppress("LongMethod", "CyclomaticComplexMethod")

package com.romulus.mobile.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Suppress("ParameterNaming")
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onPersistSourceGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onPersistOutputGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKeyDraft by rememberSaveable {
        mutableStateOf("")
    }
    var sourceModeDraft by rememberSaveable {
        mutableStateOf(state.sourceSummary?.mode ?: SourceMode.URL)
    }
    var sourceValueDraft by rememberSaveable {
        mutableStateOf(state.sourceSummary?.rawValue.orEmpty())
    }
    var concurrencyDraft by rememberSaveable {
        mutableStateOf(state.downloadSettings.maxConcurrency.toFloat())
    }
    var clearDiagnosticsDialogOpen by rememberSaveable { mutableStateOf(false) }
    val allSettingsLocked = !state.lockState.tokenEditable &&
        !state.lockState.sourceEditable &&
        !state.lockState.downloadDirectoryEditable &&
        !state.lockState.concurrencyEditable
    val hasRepairableField = state.lockState.tokenEditable ||
        state.lockState.sourceEditable ||
        state.lockState.downloadDirectoryEditable
    val repairMode = !state.lockState.concurrencyEditable && hasRepairableField

    LaunchedEffect(state.sourceSummary?.mode, state.sourceSummary?.rawValue) {
        if (sourceValueDraft.isBlank() || !state.lockState.sourceEditable) {
            sourceModeDraft = state.sourceSummary?.mode ?: SourceMode.URL
            sourceValueDraft = state.sourceSummary?.rawValue.orEmpty()
        }
    }
    LaunchedEffect(state.downloadSettings.maxConcurrency) {
        if (!state.lockState.concurrencyEditable) {
            concurrencyDraft = state.downloadSettings.maxConcurrency.toFloat()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is SettingsEffect.Message) {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sourceDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            onPersistSourceGrant(uri).fold(
                onSuccess = { grant ->
                    sourceModeDraft = SourceMode.FILE
                    sourceValueDraft = grant.uri.toString()
                    viewModel.saveSource(
                        AcceptSourceCommand(
                            mode = SourceMode.FILE,
                            rawValue = grant.uri.toString(),
                            persistedUri = grant.uri.toString()
                        )
                    )
                },
                onFailure = { throwable ->
                    viewModel.showFeedback(
                        throwable.message ?: "Source document permission could not be saved."
                    )
                }
            )
        }
    }
    val outputDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            onPersistOutputGrant(uri).fold(
                onSuccess = { grant ->
                    viewModel.saveDownloadSettings(
                        DownloadSettingsDraft(
                            outputDirectoryUri = grant.uri.toString(),
                            maxConcurrency = state.downloadSettings.maxConcurrency
                        )
                    )
                },
                onFailure = { throwable ->
                    viewModel.showFeedback(
                        throwable.message ?: "Download directory permission could not be saved."
                    )
                }
            )
        }
    }

    ResponsiveScreenContainer(
        modifier = modifier.fillMaxSize(),
        scrollable = true,
        compactVerticalPadding = true
    ) { metrics ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall
            )

            if (allSettingsLocked) {
                Text(
                    text = "Settings are locked while downloads are active.",
                    color = MaterialTheme.colorScheme.error
                )
            } else if (repairMode) {
                Text(
                    text = "Only the broken saved setting can be edited while downloads stay active.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            SectionCard(
                title = "API key",
                description = "Credential used for Real-Debrid requests."
            ) {
                Text(
                    text = if (state.maskedToken.isBlank()) {
                        "Saved key: Not set"
                    } else {
                        "Saved key: ${state.maskedToken}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = {
                        apiKeyDraft = it
                        viewModel.clearFeedback()
                    },
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = state.lockState.tokenEditable,
                    singleLine = true
                )
                Button(
                    enabled = state.lockState.tokenEditable && apiKeyDraft.isNotBlank(),
                    onClick = { viewModel.saveApiKey(apiKeyDraft) }
                ) {
                    Text("Save API key")
                }
            }

            SectionCard(
                title = "Source",
                description = "Choose URL or local file for the standard runtime."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceModeOption(
                        label = "URL",
                        selected = sourceModeDraft == SourceMode.URL,
                        enabled = state.lockState.sourceEditable,
                        onClick = {
                            sourceModeDraft = SourceMode.URL
                            viewModel.clearFeedback()
                        }
                    )
                    SourceModeOption(
                        label = "File",
                        selected = sourceModeDraft == SourceMode.FILE,
                        enabled = state.lockState.sourceEditable,
                        onClick = {
                            sourceModeDraft = SourceMode.FILE
                            viewModel.clearFeedback()
                        }
                    )
                }
                Text(
                    text = state.sourceSummary?.rawValue ?: "No source saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sourceModeDraft == SourceMode.URL) {
                    OutlinedTextField(
                        value = sourceValueDraft,
                        onValueChange = {
                            sourceValueDraft = it
                            viewModel.clearFeedback()
                        },
                        label = { Text("Source URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.lockState.sourceEditable,
                        singleLine = true
                    )
                    Button(
                        enabled = state.lockState.sourceEditable && sourceValueDraft.isNotBlank(),
                        onClick = {
                            viewModel.saveSource(
                                AcceptSourceCommand(
                                    mode = SourceMode.URL,
                                    rawValue = sourceValueDraft.trim(),
                                    persistedUri = null
                                )
                            )
                        }
                    ) {
                        Text("Save source URL")
                    }
                } else {
                    Button(
                        enabled = state.lockState.sourceEditable,
                        onClick = {
                            sourceDocumentLauncher.launch(
                                arrayOf("application/json", "text/json", "*/*")
                            )
                        }
                    ) {
                        Text("Pick local source file")
                    }
                }
            }

            SectionCard(
                title = "Download directory",
                description = "Storage Access Framework directory used for final outputs."
            ) {
                Text(
                    text = state.downloadSettings.outputDirectoryUri ?: "No directory selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    enabled = state.lockState.downloadDirectoryEditable,
                    onClick = { outputDirectoryLauncher.launch(null) }
                ) {
                    Text("Select download directory")
                }
            }

            SectionCard(
                title = "Download behavior",
                description = "Queue concurrency for the standard download flow."
            ) {
                Text(
                    text = "Max concurrency: ${concurrencyDraft.roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = concurrencyDraft,
                    onValueChange = {
                        concurrencyDraft = it.roundToInt().toFloat()
                        viewModel.clearFeedback()
                    },
                    valueRange = 1f..5f,
                    steps = 3,
                    enabled = state.lockState.concurrencyEditable
                )
                Button(
                    enabled = state.lockState.concurrencyEditable,
                    onClick = {
                        val concurrency = concurrencyDraft.roundToInt()
                        val outputDirectory = state.downloadSettings.outputDirectoryUri
                        when {
                            concurrency !in 1..5 -> {
                                viewModel.showFeedback("Concurrency must be between 1 and 5.")
                            }

                            outputDirectory.isNullOrBlank() -> {
                                viewModel.showFeedback("Download directory must be selected first.")
                            }

                            else -> {
                                viewModel.saveDownloadSettings(
                                    DownloadSettingsDraft(
                                        outputDirectoryUri = outputDirectory,
                                        maxConcurrency = concurrency
                                    )
                                )
                            }
                        }
                    }
                ) {
                    Text("Save concurrency")
                }
            }

            SectionCard(
                title = "Diagnostics",
                description = "Manage local diagnostics capture and exported support bundles."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable diagnostics")
                    Switch(
                        checked = state.diagnosticsSettings.enabled,
                        onCheckedChange = viewModel::setDiagnosticsEnabled
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clearDiagnosticsDialogOpen = true }) {
                        Text("Clear diagnostics")
                    }
                    Button(onClick = viewModel::exportDiagnostics) {
                        Text("Export")
                    }
                }
            }

            state.feedbackMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (clearDiagnosticsDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearDiagnosticsDialogOpen = false },
            title = { Text("Clear diagnostics") },
            text = { Text("Delete stored diagnostics data?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearDiagnosticsDialogOpen = false
                        viewModel.clearDiagnostics()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDiagnosticsDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SourceModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = {
                if (enabled) {
                    onClick()
                }
            },
            enabled = enabled
        )
        Text(label)
    }
}

@Composable
private fun SectionCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}
