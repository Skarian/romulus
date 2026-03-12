package com.romulus.mobile.source.snapshot

import android.content.Context
import com.romulus.mobile.source.InstantAsEpochMilliSerializer
import com.romulus.mobile.source.ingest.StagedSourceConfig
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@JvmInline
value class SourceActivationId(val value: String)

@Serializable
data class SourceActivationRecord(
    val activationId: SourceActivationId,
    val configStageId: String,
    val snapshotId: SnapshotId,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val activatedAt: Instant
)

interface SourceActivationStore {
    suspend fun readActive(): SourceActivationRecord?

    suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord>
}

internal class SharedPreferencesSourceActivationStore(context: Context, private val json: Json) :
    SourceActivationStore {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun readActive(): SourceActivationRecord? {
        val encoded = sharedPreferences.getString(KEY_ACTIVE_ACTIVATION, null) ?: return null
        return runCatching {
            json.decodeFromString<SourceActivationRecord>(encoded)
        }.getOrNull()
    }

    override suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord> = runCatching {
        val record = SourceActivationRecord(
            activationId = SourceActivationId(UUID.randomUUID().toString()),
            configStageId = stagedConfig.stageId,
            snapshotId = stagedSnapshot.snapshotId,
            activatedAt = activatedAt
        )
        val editor = sharedPreferences.edit()
        editor.putString(KEY_ACTIVE_ACTIVATION, json.encodeToString(record))
        check(editor.commit()) { "Source activation could not be persisted" }
        record
    }

    private companion object {
        const val PREFERENCES_NAME = "source_activation_store"
        const val KEY_ACTIVE_ACTIVATION = "active_activation"
    }
}
