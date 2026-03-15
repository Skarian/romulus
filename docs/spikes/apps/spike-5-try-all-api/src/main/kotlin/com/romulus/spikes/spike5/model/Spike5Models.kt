package com.romulus.spikes.spike5.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TorrentSummaryDto(
    val id: String,
    val hash: String? = null,
    val status: String? = null,
    val filename: String? = null,
)

@Serializable
data class AvailableHostDto(
    val host: String,
    @SerialName("max_file_size")
    val maxFileSize: Long? = null,
)

@Serializable
data class AddedMagnetDto(
    val id: String,
    val uri: String? = null,
)

@Serializable
data class TorrentInfoDto(
    val id: String,
    val hash: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val progress: Double? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
    val links: List<String> = emptyList(),
    val files: List<TorrentFileDto> = emptyList(),
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
    val link: String? = null,
)

@Serializable
data class UnrestrictedLinkDto(
    val id: String? = null,
    val filename: String,
    @SerialName("download")
    val downloadUrl: String,
    @SerialName("filesize")
    val fileSize: Long? = null,
)

@Serializable
data class FolderLinkDto(
    val id: String? = null,
    val filename: String,
    @SerialName("mimeType")
    val mimeType: String? = null,
    @SerialName("filesize")
    val fileSize: Long? = null,
    val link: String? = null,
    val host: String? = null,
    val chunks: Int? = null,
    @SerialName("download")
    val downloadUrl: String? = null,
    val generated: String? = null,
)

@Serializable
data class ApiErrorPayload(
    @SerialName("error_code")
    val errorCode: Int? = null,
    @SerialName("error")
    val error: String? = null,
)

@Serializable
data class TraceEvent(
    val timestamp: String,
    val stage: String,
    val method: String? = null,
    val endpoint: String? = null,
    val statusCode: Int? = null,
    val providerCode: Int? = null,
    val summary: String,
)

@Serializable
data class DeletedTorrentRecord(
    val torrentId: String,
    val status: String? = null,
    val filename: String? = null,
)

@Serializable
data class ProviderStatusSample(
    val timestamp: String,
    val status: String? = null,
    val progress: Double? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
    val linkCount: Int = 0,
)

@Serializable
data class DownloadManifestEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class SelectedFileProbe(
    val requestedPath: String,
    val resolvedFileId: Int? = null,
    val resolvedFileBytes: Long? = null,
    val resolvedFileFound: Boolean,
    val wholeTorrentLinkPresent: Boolean = false,
    val folderProbeAttempted: Boolean = false,
    val folderProbeError: String? = null,
    val folderLinkCount: Int = 0,
    val folderCandidateCount: Int = 0,
    val folderMatchedFilename: String? = null,
    val folderMatchedFileSize: Long? = null,
    val folderMatchedDownloadPresent: Boolean = false,
    val downloaded: Boolean = false,
    val downloadManifest: DownloadManifestEntry? = null,
)

@Serializable
data class Spike5Summary(
    val startedAt: String,
    val finishedAt: String,
    val artifactRoot: String,
    val envFile: String,
    val magnetInfoHash: String,
    val deletedMatchCount: Int,
    val addedTorrentId: String? = null,
    val selectedFilePath: String,
    val observedStatuses: List<String> = emptyList(),
    val finalStatus: String? = null,
    val finalProgress: Double? = null,
    val finalLinkCount: Int = 0,
    val cachedWholeTorrent: Boolean,
    val selectedFileFound: Boolean,
    val selectedFileId: Int? = null,
    val folderLinkCount: Int = 0,
    val folderCandidateCount: Int = 0,
    val selectedFileDownloaded: Boolean,
    val status: String,
    val message: String,
)
