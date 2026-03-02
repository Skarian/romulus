package com.romulus.mobile.core.text

import com.romulus.mobile.domain.home.HomeRow
import com.romulus.mobile.domain.source.SourceEntry

object HomeDisambiguator {
    fun toRows(entries: List<SourceEntry>): List<HomeRow> {
        if (entries.isEmpty()) return emptyList()
        val duplicateNameSet = entries
            .groupBy { it.displayName.lowercase() }
            .filterValues { it.size > 1 }
            .keys

        return entries
            .sortedWith(
                compareBy<SourceEntry>(
                    { it.displayName.lowercase() },
                    { it.subfolder.lowercase() },
                    { it.index }
                )
            )
            .map { entry ->
                val subtitle = if (entry.displayName.lowercase() in duplicateNameSet) {
                    entry.subfolder.ifBlank { "Entry #${entry.index + 1}" }
                } else {
                    entry.subfolder
                }
                HomeRow(
                    entryIndex = entry.index,
                    title = entry.displayName,
                    subtitle = subtitle,
                    sourceOrdinal = entry.index
                )
            }
    }
}
