package com.romulus.mobile.ui.home

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
import com.romulus.mobile.ui.files.FilesRouteArgs

@Suppress("UnusedParameter")
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenFiles: (FilesRouteArgs) -> Unit,
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
            text = "Home scaffold is not wired into the running UI yet.",
            style = MaterialTheme.typography.titleMedium
        )
        when (val mode = state.mode) {
            is HomeMode.InvalidSettings -> {
                Text(text = "Broken settings: ${mode.broken.joinToString()}")
            }

            is HomeMode.SourceLoadError -> {
                Text(text = mode.message, color = MaterialTheme.colorScheme.error)
            }

            is HomeMode.Content -> {
                Text(text = "Visible rows: ${mode.rows.size}")
                mode.warning?.let { warning ->
                    Text(text = warning.toString())
                }
            }
        }
        if (state.refreshVisible) {
            Text(text = "Manual refresh would be available in the wired UI.")
        }
    }
}
