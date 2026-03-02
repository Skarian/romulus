package com.romulus.mobile.feature.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
fun SetupFlow(
    settings: AppSettings,
    onValidateApiKey: suspend (String) -> ValidationResult,
    onSetUrlSource: suspend (String) -> ValidationResult,
    onSetLocalSource: suspend (Uri) -> ValidationResult,
    onSetDownloadDirectory: suspend (Uri) -> Unit,
    onComplete: suspend () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(settings.sourceValue.orEmpty()) }
    var sourceMode by remember { mutableStateOf(settings.sourceMode ?: SourceMode.URL) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val pickJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = onSetLocalSource(uri)) {
                ValidationResult.Valid -> {
                    error = null
                    step = 2
                }

                is ValidationResult.Invalid -> error = result.message
            }
        }
    }

    val pickDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            onSetDownloadDirectory(uri)
            error = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Setup",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (step) {
            0 -> {
                Text(text = "Step 1 of 3: API key")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Real-Debrid API key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = apiKey.isNotBlank(),
                    onClick = {
                        scope.launch {
                            when (val result = onValidateApiKey(apiKey)) {
                                ValidationResult.Valid -> {
                                    error = null
                                    step = 1
                                }

                                is ValidationResult.Invalid -> error = result.message
                            }
                        }
                    }
                ) {
                    Text("Validate API key")
                }
            }

            1 -> {
                Text(text = "Step 2 of 3: JSON source")
                Spacer(modifier = Modifier.height(8.dp))
                RowRadio(
                    label = "URL source",
                    selected = sourceMode == SourceMode.URL,
                    onSelect = { sourceMode = SourceMode.URL }
                )
                RowRadio(
                    label = "Local JSON file",
                    selected = sourceMode == SourceMode.FILE,
                    onSelect = { sourceMode = SourceMode.FILE }
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (sourceMode == SourceMode.URL) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Public JSON URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        enabled = url.isNotBlank(),
                        onClick = {
                            scope.launch {
                                when (val result = onSetUrlSource(url)) {
                                    ValidationResult.Valid -> {
                                        error = null
                                        step = 2
                                    }

                                    is ValidationResult.Invalid -> error = result.message
                                }
                            }
                        }
                    ) {
                        Text("Validate URL source")
                    }
                } else {
                    Button(onClick = { pickJsonLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                        Text("Pick local JSON file")
                    }
                }
            }

            else -> {
                Text(text = "Step 3 of 3: Download directory")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = settings.downloadDirectoryUri ?: "No directory selected")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { pickDirectoryLauncher.launch(null) }) {
                    Text("Pick download directory")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    enabled = settings.isConfigurationValid,
                    onClick = {
                        scope.launch {
                            onComplete()
                        }
                    }
                ) {
                    Text("Finish setup")
                }
                TextButton(onClick = { step = 1 }) {
                    Text("Back")
                }
            }
        }
        if (step > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { step -= 1 }) {
                Text("Previous step")
            }
        }
        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RowRadio(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}
