package com.romulus.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.romulus.mobile.data.security.ApiKeyCipher
import com.romulus.mobile.domain.source.SourceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class PreferencesSettingsRepository(
    appContext: Context,
    private val apiKeyCipher: ApiKeyCipher
) : SettingsRepository {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { appContext.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            AppSettings(
                setupComplete = prefs[Keys.SETUP_COMPLETE] ?: false,
                encryptedApiKey = prefs[Keys.ENCRYPTED_API_KEY],
                sourceMode = prefs[Keys.SOURCE_MODE]?.let { SourceMode.valueOf(it) },
                sourceValue = prefs[Keys.SOURCE_VALUE],
                sourceSnapshotId = prefs[Keys.SOURCE_SNAPSHOT_ID],
                sourceIsStale = prefs[Keys.SOURCE_IS_STALE] ?: false,
                sourceLastRefreshEpochMs = prefs[Keys.SOURCE_LAST_REFRESH_EPOCH_MS],
                downloadDirectoryUri = prefs[Keys.DOWNLOAD_DIRECTORY_URI],
                maxConcurrency = (prefs[Keys.MAX_CONCURRENCY] ?: DEFAULT_MAX_CONCURRENCY)
                    .coerceIn(MIN_MAX_CONCURRENCY, MAX_MAX_CONCURRENCY)
            )
        }

    override suspend fun saveApiKey(rawApiKey: String) {
        val encrypted = apiKeyCipher.encrypt(rawApiKey.trim())
        dataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_API_KEY] = encrypted
        }
    }

    override suspend fun readApiKey(): String? {
        val encrypted = settings.first().encryptedApiKey ?: return null
        return runCatching { apiKeyCipher.decrypt(encrypted) }
            .getOrNull()
    }

    override suspend fun setSource(mode: SourceMode, value: String, snapshotId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SOURCE_MODE] = mode.name
            prefs[Keys.SOURCE_VALUE] = value
            prefs[Keys.SOURCE_SNAPSHOT_ID] = snapshotId
        }
    }

    override suspend fun setSourceFreshness(isStale: Boolean, lastRefreshEpochMs: Long?) {
        dataStore.edit { prefs ->
            prefs[Keys.SOURCE_IS_STALE] = isStale
            if (lastRefreshEpochMs == null) {
                prefs.remove(Keys.SOURCE_LAST_REFRESH_EPOCH_MS)
            } else {
                prefs[Keys.SOURCE_LAST_REFRESH_EPOCH_MS] = lastRefreshEpochMs
            }
        }
    }

    override suspend fun setDownloadDirectoryUri(uri: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_DIRECTORY_URI] = uri
        }
    }

    override suspend fun setSetupComplete(isComplete: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SETUP_COMPLETE] = isComplete
        }
    }

    override suspend fun setMaxConcurrency(value: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.MAX_CONCURRENCY] = value.coerceIn(MIN_MAX_CONCURRENCY, MAX_MAX_CONCURRENCY)
        }
    }

    private object Keys {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val SOURCE_MODE = stringPreferencesKey("source_mode")
        val SOURCE_VALUE = stringPreferencesKey("source_value")
        val SOURCE_SNAPSHOT_ID = stringPreferencesKey("source_snapshot_id")
        val SOURCE_IS_STALE = booleanPreferencesKey("source_is_stale")
        val SOURCE_LAST_REFRESH_EPOCH_MS = longPreferencesKey("source_last_refresh_epoch_ms")
        val DOWNLOAD_DIRECTORY_URI = stringPreferencesKey("download_directory_uri")
        val MAX_CONCURRENCY = intPreferencesKey("max_concurrency")
    }

    companion object {
        private const val DATASTORE_NAME = "romulus_settings.preferences_pb"
        private const val DEFAULT_MAX_CONCURRENCY = 10
        private const val MIN_MAX_CONCURRENCY = 1
        private const val MAX_MAX_CONCURRENCY = 100
    }
}
