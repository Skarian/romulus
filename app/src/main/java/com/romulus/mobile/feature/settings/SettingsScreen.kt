package com.romulus.mobile.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.romulus.mobile.core.validation.ValidationResult
import com.romulus.mobile.data.settings.AppSettings
import com.romulus.mobile.domain.source.SourceMode
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
    onManualRefresh: suspend () -> Unit,
    onOpenSpike: (() -> Unit)? = null
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(
                selected = sourceMode == SourceMode.URL,
                onClick = { if (remediationFieldsEnabled) sourceMode = SourceMode.URL },
                enabled = remediationFieldsEnabled
            )
            Text("URL")
            RadioButton(
                selected = sourceMode == SourceMode.FILE,
                onClick = { if (remediationFieldsEnabled) sourceMode = SourceMode.FILE },
                enabled = remediationFieldsEnabled
            )
            Text("File")
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

        Text(text = settings.downloadDirectoryUri ?: "No directory selected")
        Button(
            enabled = remediationFieldsEnabled,
            onClick = { pickDirectoryLauncher.launch(null) }
        ) {
            Text("Select download directory")
        }

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

        TextButton(onClick = { scope.launch { onManualRefresh() } }) {
            Text("Manual JSON refresh")
        }

        if (onOpenSpike != null) {
            TextButton(onClick = onOpenSpike) {
                Text("Open Ketch spike")
            }
        }

        if (!message.isNullOrBlank()) {
            Text(text = message.orEmpty(), color = MaterialTheme.colorScheme.primary)
        }
    }
}
