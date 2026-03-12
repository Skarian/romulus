package com.romulus.mobile.source.torrentmeta

import kotlinx.serialization.Serializable

@Serializable
data class TorrentFileSelectionIntent(
    val sourceMagnetUri: String,
    val normalizedPath: String,
    val sizeBytes: Long?,
    val occurrenceIndex: Int
)

@Serializable
internal data class TorrentMetadataFileRecord(
    val originalName: String,
    val path: String,
    val sizeBytes: Long?,
    val partLabel: String?,
    val selectionIntent: TorrentFileSelectionIntent
)

@Serializable
internal data class TorrentMetadataInventory(val files: List<TorrentMetadataFileRecord>)
