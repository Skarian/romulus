package com.romulus.mobile.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romulus.mobile.domain.home.HomeRow

@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    sourceStatus: String,
    validationSummary: String?,
    onRefresh: () -> Unit,
    onEntryClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = sourceStatus)
            Button(onClick = onRefresh) {
                Text("Refresh")
            }
        }

        if (!validationSummary.isNullOrBlank()) {
            Text(
                text = validationSummary,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (rows.isEmpty()) {
            Text(
                text = "No valid files",
                style = MaterialTheme.typography.bodyLarge
            )
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { row ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(row.entryIndex) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = row.title, style = MaterialTheme.typography.titleMedium)
                        if (row.subtitle.isNotBlank()) {
                            Text(text = row.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
