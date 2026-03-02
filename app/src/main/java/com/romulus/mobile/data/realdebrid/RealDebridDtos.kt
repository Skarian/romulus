package com.romulus.mobile.data.realdebrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RealDebridUserDto(
    val id: Int? = null,
    val username: String? = null,
    val email: String? = null
)

@Serializable
data class AddedMagnetDto(
    val id: String,
    val uri: String? = null
)

@Serializable
data class TorrentInfoDto(
    val id: String,
    val hash: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val files: List<TorrentFileDto> = emptyList(),
    val links: List<String> = emptyList()
)

@Serializable
data class TorrentSummaryDto(
    val id: String,
    val hash: String? = null,
    val status: String? = null
)

@Serializable
data class TorrentFileDto(
    val id: Int,
    val path: String,
    val bytes: Long = 0,
    val selected: Int = 0,
    @SerialName("unrestricted")
    val unrestricted: String? = null,
    @SerialName("link")
    val link: String? = null
)

@Serializable
data class UnrestrictedLinkDto(
    val id: String? = null,
    val filename: String? = null,
    @SerialName("download")
    val downloadUrl: String,
    @SerialName("filesize")
    val fileSize: Long? = null
)
