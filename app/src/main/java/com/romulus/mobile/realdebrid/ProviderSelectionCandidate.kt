package com.romulus.mobile.realdebrid

import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class ProviderSelectionCandidate(val file: TorrentFileDto, val selectionId: String)

internal fun List<TorrentFileDto>.withSelectionIds(): List<ProviderSelectionCandidate> {
    val occurrences = mutableMapOf<Pair<String, Long?>, Int>()
    return map { file ->
        val normalizedPath = normalizeProviderPath(file.path)
        val occurrenceKey = normalizedPath to file.bytes
        val occurrenceIndex = occurrences.getOrDefault(occurrenceKey, 0) + 1
        occurrences[occurrenceKey] = occurrenceIndex
        ProviderSelectionCandidate(
            file = file,
            selectionId = providerSelectionId(
                normalizedPath = normalizedPath,
                sizeBytes = file.bytes,
                occurrenceIndex = occurrenceIndex
            )
        )
    }
}

internal fun providerSelectionId(
    normalizedPath: String,
    sizeBytes: Long?,
    occurrenceIndex: Int
): String {
    val seed = listOf(
        normalizedPath,
        sizeBytes?.toString() ?: "unknown",
        occurrenceIndex.toString()
    ).joinToString("|")
    return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
}

internal fun normalizeProviderPath(path: String): String = path
    .replace('\\', '/')
    .trimStart('/')
