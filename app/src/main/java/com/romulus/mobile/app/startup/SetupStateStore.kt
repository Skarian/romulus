package com.romulus.mobile.app.startup

import java.time.Instant

data class SetupCompletionRecord(val isCompleted: Boolean, val completedAt: Instant?)

interface SetupStateStore {
    suspend fun read(): SetupCompletionRecord

    suspend fun markCompleted(at: Instant): Result<Unit>
}
