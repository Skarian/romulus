package com.romulus.mobile.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Downloads scaffold is not wired into the running UI yet.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = "Visible rows: ${state.projection.rows.size}")
        Text(text = "Active downloads: ${state.projection.activeDownloads}")
        state.detailTaskId?.let { taskId ->
            Text(text = "Detail dialog would open for ${taskId.value}.")
        }
        state.clearHistoryDialog?.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}
