package com.romulus.mobile.realdebrid

data class ProviderSourceRef(val magnetUri: String, val partLabel: String?)

data class ProviderInventoryRequest(val sources: List<ProviderSourceRef>)

data class ProviderFileRecord(
    val providerFileId: String,
    val originalName: String,
    val path: String,
    val sizeBytes: Long?,
    val partLabel: String?,
    val locator: ProviderLocator
)

data class ProviderLocator(
    val sourceMagnetUri: String,
    val torrentId: String,
    val providerFileIds: List<String>,
    val selectedProviderFileId: String,
    val path: String,
    val partLabel: String?
)

data class ProviderInventory(val files: List<ProviderFileRecord>)

data class ProviderResumeMarker(
    val torrentId: String,
    val sourceMagnetUri: String,
    val selectedProviderFileIds: List<String>
)

data class ProviderReadyLink(val providerFileId: String, val restrictedUrl: String)

sealed interface AcquisitionStatus {
    data class Waiting(
        val statusLabel: String,
        val progressPercent: Double?,
        val resumeMarker: ProviderResumeMarker
    ) : AcquisitionStatus

    data class LinksReady(
        val resumeMarker: ProviderResumeMarker,
        val readyLinks: List<ProviderReadyLink>
    ) : AcquisitionStatus
}

data class ResolvedDownloadUnit(
    val downloadUrl: String,
    val originalName: String,
    val sizeBytes: Long?
)

data class ExactZipRequest(val sources: List<ProviderSourceRef>, val exactPath: String)

data class ArchiveContainerLocator(
    val archiveUrl: String,
    val originalName: String,
    val providerLocator: ProviderLocator
)
