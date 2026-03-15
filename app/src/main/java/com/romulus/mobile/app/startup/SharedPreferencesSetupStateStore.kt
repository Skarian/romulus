package com.romulus.mobile.app.startup

import android.content.Context
import java.time.Instant

internal class SharedPreferencesSetupStateStore(context: Context) : SetupStateStore {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun read(): SetupCompletionRecord {
        val completedAtEpochMillis = sharedPreferences.getLong(
            KEY_COMPLETED_AT_EPOCH_MILLIS,
            0L
        )
        val completedAt = if (completedAtEpochMillis > 0L) {
            Instant.ofEpochMilli(completedAtEpochMillis)
        } else {
            null
        }
        return SetupCompletionRecord(
            isCompleted = sharedPreferences.getBoolean(KEY_IS_COMPLETED, false),
            completedAt = completedAt
        )
    }

    override suspend fun markCompleted(at: Instant): Result<Unit> = runCatching {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_COMPLETED, true)
        editor.putLong(KEY_COMPLETED_AT_EPOCH_MILLIS, at.toEpochMilli())
        check(editor.commit()) { "Setup completion could not be saved" }
        Unit
    }

    private companion object {
        const val PREFERENCES_NAME = "app_setup_state_store"
        const val KEY_IS_COMPLETED = "is_completed"
        const val KEY_COMPLETED_AT_EPOCH_MILLIS = "completed_at_epoch_millis"
    }
}
