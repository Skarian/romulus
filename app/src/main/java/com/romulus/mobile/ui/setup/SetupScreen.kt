@file:Suppress("LongMethod")

package com.romulus.mobile.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Suppress("ParameterNaming")
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onPersistSourceGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onPersistOutputGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val uriHandler = LocalUriHandler.current
    val submitEnabled = state.canSubmit()

    val sourceDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            onPersistSourceGrant(uri).fold(
                onSuccess = { grant -> viewModel.acceptSourceGrant(grant.uri) },
                onFailure = { throwable ->
                    viewModel.showError(
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
                onSuccess = { grant -> viewModel.acceptOutputGrant(grant.uri) },
                onFailure = { throwable ->
                    viewModel.showError(
                        throwable.message ?: "Output directory permission could not be saved."
                    )
                }
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is SetupEffect.Completed) {
                currentOnCompleted()
            }
        }
    }

    ResponsiveScreenContainer(
        modifier = modifier.fillMaxSize(),
        scrollable = true,
        respectStatusBar = true
    ) { metrics ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Setup",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Enter your Real-Debrid API key, choose a source, " +
                        "and select an output directory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SetupSectionCard(
                title = "API key",
                description = "Credential used for Real-Debrid requests."
            ) {
                OutlinedTextField(
                    value = state.apiKeyDraft,
                    onValueChange = viewModel::updateApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Real-Debrid API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.isSaving,
                    singleLine = true
                )
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { uriHandler.openUri("https://real-debrid.com/apitoken") }
                ) {
                    Text("Get API token")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)) {
                SetupSectionCard(
                    title = "Source",
                    description = "Choose a URL source or local JSON file."
                ) {
                    SourceModeOption(
                        label = "URL source",
                        selected = state.sourceMode == SourceMode.URL,
                        enabled = !state.isSaving,
                        onSelect = { viewModel.updateSourceMode(SourceMode.URL) }
                    )
                    SourceModeOption(
                        label = "Local JSON file",
                        selected = state.sourceMode == SourceMode.FILE,
                        enabled = !state.isSaving,
                        onSelect = { viewModel.updateSourceMode(SourceMode.FILE) }
                    )
                    if (state.sourceMode == SourceMode.URL) {
                        OutlinedTextField(
                            value = state.sourceValueDraft,
                            onValueChange = viewModel::updateSourceValue,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Source URL") },
                            enabled = !state.isSaving,
                            singleLine = true
                        )
                    } else {
                        Button(
                            enabled = !state.isSaving,
                            onClick = {
                                sourceDocumentLauncher.launch(
                                    arrayOf("application/json", "text/json", "*/*")
                                )
                            }
                        ) {
                            Text("Pick local JSON file")
                        }
                        Text(
                            text = state.sourceDocumentUri?.toString()
                                ?: "No source document selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SetupSectionCard(
                    title = "Download directory",
                    description = "Select where final files should be written."
                ) {
                    Button(
                        enabled = !state.isSaving,
                        onClick = { outputDirectoryLauncher.launch(null) }
                    ) {
                        Text("Pick download directory")
                    }
                    Text(
                        text = state.outputDirectoryUri?.toString()
                            ?: "No output directory selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    enabled = submitEnabled,
                    onClick = viewModel::submit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (state.isSaving) "Saving..." else "Complete setup")
                }

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled
        )
        Text(text = label)
    }
}

@Composable
private fun SetupSectionCard(
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

private fun SetupUiState.canSubmit(): Boolean = !isSaving &&
    apiKeyDraft.isNotBlank() &&
    when (sourceMode) {
        SourceMode.URL -> sourceValueDraft.isNotBlank()
        SourceMode.FILE -> sourceDocumentUri != null
    } &&
    outputDirectoryUri != null
