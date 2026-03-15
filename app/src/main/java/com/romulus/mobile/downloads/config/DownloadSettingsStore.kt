package com.romulus.mobile.downloads.config

import android.content.Context

interface DownloadSettingsStore {
    suspend fun read(): DownloadSettingsState?

    suspend fun write(state: DownloadSettingsState): Result<Unit>
}

internal class SharedPreferencesDownloadSettingsStore(context: Context) : DownloadSettingsStore {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun read(): DownloadSettingsState? = readBlocking()

    override suspend fun write(state: DownloadSettingsState): Result<Unit> = runCatching {
        val editor = sharedPreferences.edit()
        if (state.outputDirectoryUri == null) {
            editor.remove(KEY_OUTPUT_DIRECTORY_URI)
        } else {
            editor.putString(KEY_OUTPUT_DIRECTORY_URI, state.outputDirectoryUri)
        }
        editor.putInt(KEY_MAX_CONCURRENCY, state.maxConcurrency)
        check(editor.commit()) { "Download settings could not be saved" }
        Unit
    }

    fun readBlocking(): DownloadSettingsState? {
        val hasOutputDirectory = sharedPreferences.contains(KEY_OUTPUT_DIRECTORY_URI)
        val hasConcurrency = sharedPreferences.contains(KEY_MAX_CONCURRENCY)
        if (!hasOutputDirectory && !hasConcurrency) {
            return null
        }

        return DownloadSettingsState(
            outputDirectoryUri = sharedPreferences.getString(KEY_OUTPUT_DIRECTORY_URI, null),
            maxConcurrency = sharedPreferences
                .getInt(KEY_MAX_CONCURRENCY, DEFAULT_MAX_CONCURRENCY)
                .coerceIn(MIN_MAX_CONCURRENCY, MAX_MAX_CONCURRENCY)
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "download_settings_store"
        const val KEY_OUTPUT_DIRECTORY_URI = "output_directory_uri"
        const val KEY_MAX_CONCURRENCY = "max_concurrency"
        const val DEFAULT_MAX_CONCURRENCY = DownloadLimits.DEFAULT_CONCURRENCY
        const val MIN_MAX_CONCURRENCY = DownloadLimits.MIN_CONCURRENCY
        const val MAX_MAX_CONCURRENCY = DownloadLimits.MAX_CONCURRENCY
    }
}
