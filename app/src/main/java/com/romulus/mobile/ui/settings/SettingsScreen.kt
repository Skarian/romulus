package com.romulus.mobile.ui.settings

import android.net.Uri
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
import com.romulus.mobile.app.platform.PersistedUriGrant

@Suppress("UnusedParameter")
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onPersistSourceGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onPersistOutputGrant: suspend (Uri) -> Result<PersistedUriGrant>,
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
            text = "Settings scaffold is not wired into the running UI yet.",
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = "Masked token present: ${state.maskedToken.isNotBlank()}")
        Text(
            text = state.sourceSummary?.rawValue ?: "No accepted source summary is available yet."
        )
        Text(
            text = "Download directory editable: ${state.lockState.downloadDirectoryEditable}"
        )
        state.feedbackMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}
