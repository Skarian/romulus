@file:Suppress("ClassSignature")

package com.romulus.mobile.realdebrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RealDebridUserDto(
    val id: Int? = null,
    val username: String? = null
)

@Serializable
internal data class AddedMagnetDto(
    val id: String,
    val uri: String? = null
)

@Serializable
internal data class TorrentInfoDto(
    val id: String,
    val hash: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val progress: Double? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
    val ended: String? = null,
    val files: List<TorrentFileDto> = emptyList(),
    val links: List<String> = emptyList()
)

@Serializable
internal data class TorrentFileDto(
    val id: Int,
    val path: String,
    val bytes: Long? = null,
    val selected: Int = 0,
    @SerialName("unrestricted")
    val unrestrictedLink: String? = null,
    val link: String? = null
)

@Serializable
internal data class UnrestrictedLinkDto(
    val id: String? = null,
    val filename: String? = null,
    @SerialName("download")
    val downloadUrl: String,
    @SerialName("filesize")
    val fileSize: Long? = null
)
