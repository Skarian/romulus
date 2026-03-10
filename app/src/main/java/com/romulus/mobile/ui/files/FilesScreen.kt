package com.romulus.mobile.ui.files

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

@Suppress("UnusedParameter")
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Files scaffold is not wired into the running UI yet.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = "Browse mode: ${state.mode}")
        Text(text = "Visible rows: ${state.rows.size}")
        Text(text = "Selected rows: ${state.selectedIds.size}")
        state.resolverError?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}
