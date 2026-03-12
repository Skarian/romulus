package com.romulus.mobile.realdebrid

import kotlinx.serialization.Serializable

@Serializable
data class ProviderSourceRef(val magnetUri: String, val partLabel: String?)

@Serializable
data class ProviderInventoryRequest(val sources: List<ProviderSourceRef>)

@Serializable
data class ProviderFileRecord(
    val providerFileId: String,
    val originalName: String,
    val path: String,
    val sizeBytes: Long?,
    val partLabel: String?,
    val locator: ProviderLocator
)

@Serializable
data class ProviderLocator(
    val sourceMagnetUri: String,
    val torrentId: String,
    val providerFileIds: List<String>,
    val selectedProviderFileId: String,
    val path: String,
    val partLabel: String?
)

@Serializable
data class ProviderInventory(val files: List<ProviderFileRecord>)

@Serializable
data class ProviderResumeMarker(
    val torrentId: String,
    val sourceMagnetUri: String,
    val selectedProviderFileIds: List<String>
)

@Serializable
data class ProviderReadyLink(val restrictedUrl: String)

@Serializable
sealed interface AcquisitionStatus {
    @Serializable
    data class Waiting(
        val statusLabel: String,
        val progressPercent: Double?,
        val resumeMarker: ProviderResumeMarker
    ) : AcquisitionStatus

    @Serializable
    data class LinksReady(
        val resumeMarker: ProviderResumeMarker,
        val readyLinks: List<ProviderReadyLink>
    ) : AcquisitionStatus
}

@Serializable
data class ResolvedDownloadUnit(
    val downloadUrl: String,
    val originalName: String,
    val sizeBytes: Long?
)

@Serializable
data class ExactZipRequest(val sources: List<ProviderSourceRef>, val exactPath: String)

@Serializable
data class ArchiveContainerLocator(
    val archiveUrl: String,
    val originalName: String,
    val providerLocator: ProviderLocator
)
