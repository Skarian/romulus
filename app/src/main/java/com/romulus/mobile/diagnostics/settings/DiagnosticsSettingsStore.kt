package com.romulus.mobile.diagnostics.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

interface DiagnosticsSettingsStore {
    suspend fun read(): DiagnosticsSettings?

    suspend fun write(settings: DiagnosticsSettings): Result<Unit>
}

internal class SharedPreferencesDiagnosticsSettingsStore(context: Context) :
    DiagnosticsSettingsStore {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun read(): DiagnosticsSettings? = readBlocking()

    override suspend fun write(settings: DiagnosticsSettings): Result<Unit> = runCatching {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_ENABLED, settings.enabled)
        check(editor.commit()) { "Diagnostics settings could not be saved" }
        Unit
    }

    fun readBlocking(): DiagnosticsSettings? = if (!sharedPreferences.contains(KEY_ENABLED)) {
        null
    } else {
        DiagnosticsSettings(enabled = sharedPreferences.getBoolean(KEY_ENABLED, false))
    }

    private companion object {
        const val PREFERENCES_NAME = "diagnostics_settings_store"
        const val KEY_ENABLED = "enabled"
    }
}

internal class DiagnosticsSettingsService(private val store: DiagnosticsSettingsStore) {
    private val state = MutableStateFlow(DiagnosticsSettings(enabled = false))

    init {
        runBlocking {
            state.value = store.read() ?: DiagnosticsSettings(enabled = false)
        }
    }

    fun observe(): StateFlow<DiagnosticsSettings> = state.asStateFlow()

    suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        val next = DiagnosticsSettings(enabled = enabled)
        return store.write(next).onSuccess {
            state.value = next
        }
    }
}
