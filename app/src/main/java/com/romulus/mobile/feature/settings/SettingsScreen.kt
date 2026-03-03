package com.romulus.mobile.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.romulus.mobile.core.validation.ValidationResult
import com.romulus.mobile.data.settings.AppSettings
import com.romulus.mobile.domain.source.SourceMode
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    activeCount: Int,
    onSaveApiKey: suspend (String) -> ValidationResult,
    onSetSourceUrl: suspend (String) -> ValidationResult,
    onSetLocalSource: suspend (Uri) -> ValidationResult,
    onSetDirectory: suspend (Uri) -> Unit,
    onSetConcurrency: suspend (Int) -> Unit,
    onManualRefresh: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var sourceMode by remember { mutableStateOf(settings.sourceMode ?: SourceMode.URL) }
    var sourceUrl by remember { mutableStateOf(settings.sourceValue.orEmpty()) }
    var concurrencyText by remember { mutableStateOf(settings.maxConcurrency.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.sourceMode, settings.sourceValue, settings.maxConcurrency) {
        sourceMode = settings.sourceMode ?: SourceMode.URL
        sourceUrl = settings.sourceValue.orEmpty()
        concurrencyText = settings.maxConcurrency.toString()
    }

    val remediationMode = activeCount > 0 && !settings.isConfigurationValid
    val allLocked = activeCount > 0 && settings.isConfigurationValid
    val remediationFieldsEnabled = activeCount == 0 || remediationMode
    val concurrencyEnabled = activeCount == 0

    val pickJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = onSetLocalSource(uri)) {
                ValidationResult.Valid -> message = "Source updated"
                is ValidationResult.Invalid -> message = result.message
            }
        }
    }

    val pickDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            onSetDirectory(uri)
            message = "Directory updated"
        }
    }

    ResponsiveScreenContainer(scrollable = true) { metrics ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            if (allLocked) {
                Text(
                    text = "Settings are locked while downloads are active. Stop downloads in Downloads first.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (remediationMode) {
                Text(
                    text = "Configuration is invalid. API key, source, and directory remain editable while downloads are active.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            SectionCard(
                title = "API key",
                description = "Credential used for Real-Debrid requests."
            ) {
                Text(
                    text = "Saved key: ${obfuscatedApiKey(settings.encryptedApiKey)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = remediationFieldsEnabled
                )
                Button(
                    enabled = remediationFieldsEnabled && apiKey.isNotBlank(),
                    onClick = {
                        scope.launch {
                            when (val result = onSaveApiKey(apiKey)) {
                                ValidationResult.Valid -> message = "API key updated"
                                is ValidationResult.Invalid -> message = result.message
                            }
                        }
                    }
                ) {
                    Text("Save API key")
                }
            }

            SectionCard(
                title = "Source",
                description = "Choose URL or local file and refresh source data when needed."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceModeOption(
                        label = "URL",
                        selected = sourceMode == SourceMode.URL,
                        enabled = remediationFieldsEnabled,
                        onClick = { sourceMode = SourceMode.URL }
                    )
                    SourceModeOption(
                        label = "File",
                        selected = sourceMode == SourceMode.FILE,
                        enabled = remediationFieldsEnabled,
                        onClick = { sourceMode = SourceMode.FILE }
                    )
                }

                if (sourceMode == SourceMode.URL) {
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        label = { Text("Source URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = remediationFieldsEnabled
                    )
                    Button(
                        enabled = remediationFieldsEnabled && sourceUrl.isNotBlank(),
                        onClick = {
                            scope.launch {
                                when (val result = onSetSourceUrl(sourceUrl)) {
                                    ValidationResult.Valid -> message = "Source updated"
                                    is ValidationResult.Invalid -> message = result.message
                                }
                            }
                        }
                    ) {
                        Text("Save source URL")
                    }
                } else {
                    Button(
                        enabled = remediationFieldsEnabled,
                        onClick = {
                            pickJsonLauncher.launch(arrayOf("application/json", "text/json", "*/*"))
                        }
                    ) {
                        Text("Pick local source file")
                    }
                }

                TextButton(onClick = { scope.launch { onManualRefresh() } }) {
                    Text("Manual JSON refresh")
                }
            }

            SectionCard(
                title = "Download directory",
                description = "Storage Access Framework directory used for output files."
            ) {
                Text(text = settings.downloadDirectoryUri ?: "No directory selected")
                Button(
                    enabled = remediationFieldsEnabled,
                    onClick = { pickDirectoryLauncher.launch(null) }
                ) {
                    Text("Select download directory")
                }
            }

            SectionCard(
                title = "Download behavior",
                description = "Queue-level tuning for max parallel downloads."
            ) {
                OutlinedTextField(
                    value = concurrencyText,
                    onValueChange = { concurrencyText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Max concurrency (1..100)") },
                    enabled = concurrencyEnabled
                )
                Button(
                    enabled = concurrencyEnabled,
                    onClick = {
                        val value = concurrencyText.toIntOrNull()
                        if (value == null || value !in 1..100) {
                            message = "Concurrency must be between 1 and 100"
                        } else {
                            scope.launch {
                                onSetConcurrency(value)
                                message = "Concurrency updated"
                            }
                        }
                    }
                ) {
                    Text("Save concurrency")
                }
            }

            if (!message.isNullOrBlank()) {
                Text(text = message.orEmpty(), color = MaterialTheme.colorScheme.primary)
            }
        }
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

private fun obfuscatedApiKey(encryptedApiKey: String?): String {
    if (encryptedApiKey.isNullOrBlank()) return "Not set"
    val suffix = encryptedApiKey.takeLast(4)
        .filter { char -> char.isLetterOrDigit() }
    return if (suffix.isBlank()) "••••••••" else "••••••••$suffix"
}
