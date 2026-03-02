package com.romulus.mobile.data.settings

import com.romulus.mobile.domain.source.SourceMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun saveApiKey(rawApiKey: String)
    suspend fun clearApiKey()
    suspend fun readApiKey(): String?

    suspend fun setSource(mode: SourceMode, value: String, snapshotId: String)
    suspend fun setSourceFreshness(isStale: Boolean, lastRefreshEpochMs: Long?)
    suspend fun clearSource()

    suspend fun setDownloadDirectoryUri(uri: String)

    suspend fun setSetupComplete(isComplete: Boolean)

    suspend fun setMaxConcurrency(value: Int)
}
