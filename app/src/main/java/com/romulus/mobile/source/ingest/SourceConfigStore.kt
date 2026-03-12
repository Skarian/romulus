package com.romulus.mobile.source.ingest

import android.content.Context
import com.romulus.mobile.source.InstantAsEpochMilliSerializer
import com.romulus.mobile.source.snapshot.SourceRefreshOutcome
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AcceptedSourceConfigRecord(
    val mode: SourceMode,
    val rawValue: String,
    val persistedUri: String?,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val acceptedAt: Instant,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val lastRefreshAt: Instant?,
    val lastRefreshOutcome: SourceRefreshOutcome?
)

data class StagedSourceConfig(val stageId: String, val acceptedAt: Instant)

interface SourceConfigStore {
    suspend fun readStaged(stageId: String): AcceptedSourceConfigRecord?

    suspend fun stageCandidate(next: AcceptedSourceConfigRecord): Result<StagedSourceConfig>

    suspend fun discardCandidate(staged: StagedSourceConfig): Result<Unit>
}

internal class SharedPreferencesSourceConfigStore(context: Context, private val json: Json) :
    SourceConfigStore {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun readStaged(stageId: String): AcceptedSourceConfigRecord? {
        val encoded = sharedPreferences.getString(keyFor(stageId), null) ?: return null
        return runCatching {
            json.decodeFromString<AcceptedSourceConfigRecord>(encoded)
        }.getOrNull()
    }

    override suspend fun stageCandidate(
        next: AcceptedSourceConfigRecord
    ): Result<StagedSourceConfig> = runCatching {
        val stageId = UUID.randomUUID().toString()
        val encoded = json.encodeToString(next)
        val editor = sharedPreferences.edit()
        editor.putString(keyFor(stageId), encoded)
        check(editor.commit()) { "Accepted source record could not be staged" }
        StagedSourceConfig(
            stageId = stageId,
            acceptedAt = next.acceptedAt
        )
    }

    override suspend fun discardCandidate(staged: StagedSourceConfig): Result<Unit> = runCatching {
        val editor = sharedPreferences.edit()
        editor.remove(keyFor(staged.stageId))
        check(editor.commit()) { "Accepted source record could not be discarded" }
        Unit
    }

    private fun keyFor(stageId: String): String = "$STAGE_KEY_PREFIX$stageId"

    private companion object {
        const val PREFERENCES_NAME = "source_config_store"
        const val STAGE_KEY_PREFIX = "staged_config_"
    }
}
