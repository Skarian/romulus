@file:Suppress("LongMethod", "CyclomaticComplexMethod")

package com.romulus.mobile.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import com.romulus.mobile.downloads.config.DownloadLimits
import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.ui.components.RomulusAlertDialog
import com.romulus.mobile.ui.components.RomulusButtonText
import com.romulus.mobile.ui.components.RomulusDialogActionText
import com.romulus.mobile.ui.components.RomulusDialogBodyText
import com.romulus.mobile.ui.components.RomulusDialogTitle
import com.romulus.mobile.ui.components.RomulusFieldLabel
import com.romulus.mobile.ui.components.RomulusSectionCard
import com.romulus.mobile.ui.components.romulusButtonColors
import com.romulus.mobile.ui.components.romulusSwitchColors
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import java.time.Instant
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
    var pendingDiagnosticsExportLabel by rememberSaveable { mutableStateOf("") }
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
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            pendingDiagnosticsExportLabel = ""
            return@rememberLauncherForActivityResult
        }
        val targetLabel = pendingDiagnosticsExportLabel.ifBlank { "diagnostics.zip" }
        pendingDiagnosticsExportLabel = ""
        scope.launch {
            viewModel.exportDiagnostics(uri, targetLabel)
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
                style = MaterialTheme.typography.headlineMedium
            )

            if (allSettingsLocked) {
                Text(
                    text = "Settings are locked while downloads are active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (repairMode) {
                Text(
                    text = "Only the broken saved setting can be edited while downloads stay active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            RomulusSectionCard(
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
                    label = { RomulusFieldLabel("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = state.lockState.tokenEditable,
                    singleLine = true
                )
                Button(
                    colors = romulusButtonColors(),
                    enabled = state.lockState.tokenEditable && apiKeyDraft.isNotBlank(),
                    onClick = { viewModel.saveApiKey(apiKeyDraft) }
                ) {
                    RomulusButtonText("Save API key")
                }
            }

            RomulusSectionCard(
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
                        label = { RomulusFieldLabel("Source URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.lockState.sourceEditable,
                        singleLine = true
                    )
                    Button(
                        colors = romulusButtonColors(),
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
                        RomulusButtonText("Save source URL")
                    }
                } else {
                    Button(
                        colors = romulusButtonColors(),
                        enabled = state.lockState.sourceEditable,
                        onClick = {
                            sourceDocumentLauncher.launch(
                                arrayOf("application/json", "text/json", "*/*")
                            )
                        }
                    ) {
                        RomulusButtonText("Pick local source file")
                    }
                }
            }

            RomulusSectionCard(
                title = "Download directory",
                description = "Storage Access Framework directory used for final outputs."
            ) {
                Text(
                    text = state.downloadSettings.outputDirectoryUri ?: "No directory selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    colors = romulusButtonColors(),
                    enabled = state.lockState.downloadDirectoryEditable,
                    onClick = { outputDirectoryLauncher.launch(null) }
                ) {
                    RomulusButtonText("Select download directory")
                }
            }

            RomulusSectionCard(
                title = "Simultaneous downloads",
                description = "Choose how many downloads run at the same time."
            ) {
                val minConcurrency = DownloadLimits.MIN_CONCURRENCY.toFloat()
                val maxConcurrency = DownloadLimits.MAX_CONCURRENCY.toFloat()
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
                    valueRange = minConcurrency..maxConcurrency,
                    steps = DownloadLimits.MAX_CONCURRENCY -
                        DownloadLimits.MIN_CONCURRENCY - 1,
                    enabled = state.lockState.concurrencyEditable
                )
                Button(
                    colors = romulusButtonColors(),
                    enabled = state.lockState.concurrencyEditable,
                    onClick = {
                        val concurrency = concurrencyDraft.roundToInt()
                        val outputDirectory = state.downloadSettings.outputDirectoryUri
                        when {
                            concurrency !in
                                DownloadLimits.MIN_CONCURRENCY..DownloadLimits.MAX_CONCURRENCY -> {
                                viewModel.showFeedback(
                                    "Concurrency must be between " +
                                        "${DownloadLimits.MIN_CONCURRENCY} and " +
                                        "${DownloadLimits.MAX_CONCURRENCY}."
                                )
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
                    RomulusButtonText("Save concurrency")
                }
            }

            RomulusSectionCard(
                title = "Diagnostics",
                description = "Manage local diagnostics capture and exported support bundles."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable diagnostics",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = state.diagnosticsSettings.enabled,
                        onCheckedChange = viewModel::setDiagnosticsEnabled,
                        colors = romulusSwitchColors()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { clearDiagnosticsDialogOpen = true },
                        colors = romulusButtonColors()
                    ) {
                        RomulusButtonText("Clear diagnostics")
                    }
                    Button(
                        colors = romulusButtonColors(),
                        onClick = {
                            val targetLabel = buildDiagnosticsExportFileName()
                            pendingDiagnosticsExportLabel = targetLabel
                            diagnosticsExportLauncher.launch(targetLabel)
                        }
                    ) {
                        RomulusButtonText("Export")
                    }
                }
            }

            state.feedbackMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (clearDiagnosticsDialogOpen) {
        RomulusAlertDialog(
            onDismissRequest = { clearDiagnosticsDialogOpen = false },
            title = { RomulusDialogTitle("Clear diagnostics") },
            text = { RomulusDialogBodyText("Delete stored diagnostics data?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearDiagnosticsDialogOpen = false
                        viewModel.clearDiagnostics()
                    }
                ) {
                    RomulusDialogActionText("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDiagnosticsDialogOpen = false }) {
                    RomulusDialogActionText("Cancel")
                }
            }
        )
    }
}

private fun buildDiagnosticsExportFileName(): String = "romulus-diagnostics-${
    Instant.now().toString()
        .replace(":", "")
        .replace("-", "")
}.zip"

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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
