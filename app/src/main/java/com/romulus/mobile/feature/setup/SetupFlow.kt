package com.romulus.mobile.feature.setup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.romulus.mobile.core.validation.ValidationResult
import com.romulus.mobile.data.settings.AppSettings
import com.romulus.mobile.domain.source.SourceMode
import com.romulus.mobile.ui.layout.ResponsiveScreenContainer
import kotlinx.coroutines.launch

@Composable
fun SetupFlow(
    settings: AppSettings,
    onValidateApiKey: suspend (String) -> ValidationResult,
    onSetUrlSource: suspend (String) -> ValidationResult,
    onSetLocalSource: suspend (Uri) -> ValidationResult,
    onSetDownloadDirectory: suspend (Uri) -> Unit,
    onComplete: suspend () -> Boolean
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf(settings.sourceValue.orEmpty()) }
    var sourceMode by rememberSaveable { mutableStateOf(settings.sourceMode ?: SourceMode.URL) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    BackHandler(enabled = step > 0) {
        step -= 1
    }

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

                is ValidationResult.Invalid -> {
                    error = result.message
                }
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
            val completed = onComplete()
            if (completed) {
                Toast.makeText(context, "Setup complete", Toast.LENGTH_SHORT).show()
            } else {
                error = "Setup is still incomplete. Verify API key and source settings."
            }
        }
    }

    ResponsiveScreenContainer(scrollable = true) { metrics ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            Text(
                text = "Setup",
                style = MaterialTheme.typography.headlineMedium
            )

            when (step) {
                0 -> {
                    Text(text = "Step 1 of 3: API key")
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Real-Debrid API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { uriHandler.openUri("https://real-debrid.com/apitoken") }) {
                        Text("Open API token help")
                    }
                    Button(
                        enabled = apiKey.isNotBlank(),
                        onClick = {
                            scope.launch {
                                when (val result = onValidateApiKey(apiKey)) {
                                    ValidationResult.Valid -> {
                                        error = null
                                        step = 1
                                    }

                                    is ValidationResult.Invalid -> {
                                        error = result.message
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Validate API key")
                    }
                }

                1 -> {
                    Text(text = "Step 2 of 3: JSON source")
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

                    if (sourceMode == SourceMode.URL) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Public JSON URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = url.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    when (val result = onSetUrlSource(url)) {
                                        ValidationResult.Valid -> {
                                            error = null
                                            step = 2
                                        }

                                        is ValidationResult.Invalid -> {
                                            error = result.message
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Load")
                        }
                    } else {
                        Button(onClick = { pickJsonLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                            Text("Pick local JSON file")
                        }
                    }
                }

                else -> {
                    Text(text = "Step 3 of 3: Download directory")
                    Text(text = settings.downloadDirectoryUri ?: "No directory selected")
                    Button(onClick = { pickDirectoryLauncher.launch(null) }) {
                        Text("Pick download directory")
                    }
                    Text(
                        text = "Directory selection completes setup when configuration is valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (step == 0) "Use system back to exit setup" else "Use system back to return to previous step",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RowRadio(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}
