package com.romulus.mobile.feature.downloads.spike

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ketch.DownloadModel
import com.ketch.Ketch
import com.ketch.Status
import com.romulus.mobile.data.files.SafFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KetchSpikeScreen(
    ketch: Ketch,
    safFileStore: SafFileStore
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://proof.ovh.net/files/100Mb.dat") }
    var downloadId by remember { mutableIntStateOf(-1) }
    var statusText by remember { mutableStateOf("Idle") }
    var safTreeUri by remember { mutableStateOf<String?>(null) }
    var safStatusText by remember { mutableStateOf("No SAF destination selected") }

    val pickDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        safTreeUri = uri.toString()
        safStatusText = "SAF destination selected"
    }

    LaunchedEffect(downloadId) {
        if (downloadId < 0) return@LaunchedEffect
        ketch.observeDownloadById(downloadId).collect { model: DownloadModel? ->
            if (model == null) return@collect
            val failureText = if (model.status == Status.FAILED && !model.failureReason.isNullOrBlank()) {
                " - ${model.failureReason}"
            } else {
                ""
            }
            statusText = "${model.status} ${model.progress}%$failureText"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ketch + SAF compatibility spike", style = MaterialTheme.typography.headlineSmall)
        Text("Use this screen to validate Ketch controls and SAF write access before full hardening.")

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Direct download URL") }
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                scope.launch {
                    val id = ketch.download(
                        url = url,
                        path = context.cacheDir.absolutePath,
                        fileName = "ketch-spike.bin",
                        tag = "ketch-spike",
                        metaData = "spike",
                        headers = hashMapOf(),
                        supportPauseResume = true
                    )
                    downloadId = id
                }
            }) {
                Text("Start")
            }

            Button(onClick = { if (downloadId >= 0) ketch.pause(downloadId) }) { Text("Pause") }
            Button(onClick = { if (downloadId >= 0) ketch.resume(downloadId) }) { Text("Resume") }
            Button(onClick = { if (downloadId >= 0) ketch.cancel(downloadId) }) { Text("Cancel") }
            Button(onClick = { if (downloadId >= 0) ketch.clearDb(downloadId, false) }) { Text("Delete partial") }
        }

        Text("Ketch status: $statusText")
        Text("Ketch output path: ${context.cacheDir.absolutePath}")

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { pickDirectoryLauncher.launch(null) }) {
                Text("Pick SAF folder")
            }
            Button(
                enabled = !safTreeUri.isNullOrBlank(),
                onClick = {
                    val treeUri = safTreeUri ?: return@Button
                    scope.launch {
                        val created = withContext(Dispatchers.IO) {
                            val uri = safFileStore.createOutputFile(
                                treeUri = treeUri,
                                subfolder = "spike",
                                preferredFileName = "saf-probe.txt"
                            )
                            if (uri == null) return@withContext false
                            val stream = safFileStore.openOutput(uri.toString()) ?: return@withContext false
                            stream.use {
                                it.write("romulus-saf-probe".encodeToByteArray())
                                it.flush()
                            }
                            true
                        }
                        safStatusText = if (created) {
                            "SAF write probe succeeded"
                        } else {
                            "SAF write probe failed"
                        }
                    }
                }
            ) {
                Text("Probe SAF write")
            }
        }

        Text("SAF folder: ${safTreeUri ?: "Not selected"}")
        Text("SAF status: $safStatusText")
    }
}
