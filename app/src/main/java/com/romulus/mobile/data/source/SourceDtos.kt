package com.romulus.mobile.data.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SourceDocumentDto(
    val version: Int,
    val entries: List<SourceEntryDto>
)

@Serializable
data class SourceEntryDto(
    val displayName: String? = null,
    val subfolder: String? = null,
    val path: String? = null,
    val torrents: List<SourceTorrentDto> = emptyList(),
    val rename: RenameDto? = null,
    val ignore: IgnoreDto? = null
)

@Serializable
data class SourceTorrentDto(
    val url: String,
    val partName: String? = null
)

@Serializable
data class RenameDto(
    val pattern: String? = null,
    val replacement: String? = null
)

@Serializable
data class IgnoreDto(
    @SerialName("glob")
    val globPatterns: List<String> = emptyList()
)
