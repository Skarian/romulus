package com.romulus.mobile.data.settings

import com.romulus.mobile.domain.source.SourceMode

data class AppSettings(
    val setupComplete: Boolean = false,
    val encryptedApiKey: String? = null,
    val sourceMode: SourceMode? = null,
    val sourceValue: String? = null,
    val sourceSnapshotId: String? = null,
    val sourceIsStale: Boolean = false,
    val sourceLastRefreshEpochMs: Long? = null,
    val downloadDirectoryUri: String? = null,
    val maxConcurrency: Int = 25
) {
    val hasApiKey: Boolean
        get() = !encryptedApiKey.isNullOrBlank()

    val hasSource: Boolean
        get() = sourceMode != null && !sourceValue.isNullOrBlank() && !sourceSnapshotId.isNullOrBlank()

    val hasDownloadDirectory: Boolean
        get() = !downloadDirectoryUri.isNullOrBlank()

    val isConfigurationValid: Boolean
        get() = hasApiKey && hasSource && hasDownloadDirectory
}
