@file:Suppress("ClassSignature")

package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.remotezip.EnumeratedRemoteZip
import com.romulus.mobile.source.InstantAsEpochMilliSerializer
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ArchivePreparationKey(
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId
)

@Serializable
internal sealed interface ArchivePreparationCachedState {
    @Serializable
    data class Preparing(
        val exactMatch: ProviderFileRecord,
        @Serializable(with = InstantAsEpochMilliSerializer::class)
        val enteredAt: Instant,
        @Serializable(with = InstantAsEpochMilliSerializer::class)
        val timeoutAt: Instant,
        val lastProviderStatus: String?,
        val lastProviderProgress: Double?,
        val resumeMarker: ProviderResumeMarker
    ) : ArchivePreparationCachedState

    @Serializable
    data class Ready(
        val exactMatch: ProviderFileRecord,
        @Serializable(with = InstantAsEpochMilliSerializer::class)
        val enteredAt: Instant,
        @Serializable(with = InstantAsEpochMilliSerializer::class)
        val timeoutAt: Instant,
        val resumeMarker: ProviderResumeMarker,
        val outerZip: ArchiveContainerLocator,
        val entries: EnumeratedRemoteZip
    ) : ArchivePreparationCachedState
}

internal interface ArchivePreparationStateStore {
    suspend fun read(key: ArchivePreparationKey): ArchivePreparationCachedState?

    suspend fun write(
        key: ArchivePreparationKey,
        state: ArchivePreparationCachedState
    ): Result<Unit>

    suspend fun clear(key: ArchivePreparationKey): Result<Unit>
}

internal class FileArchivePreparationStateStore(
    private val cacheDirectory: File,
    private val json: Json
) :
    ArchivePreparationStateStore {
    override suspend fun read(key: ArchivePreparationKey): ArchivePreparationCachedState? {
        val cacheFile = fileFor(key)
        if (!cacheFile.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<ArchivePreparationCachedState>(cacheFile.readText())
        }.getOrNull()
    }

    override suspend fun write(
        key: ArchivePreparationKey,
        state: ArchivePreparationCachedState
    ): Result<Unit> = runCatching {
        ensureDirectory()
        fileFor(key).writeText(json.encodeToString(state))
        Unit
    }

    override suspend fun clear(key: ArchivePreparationKey): Result<Unit> = runCatching {
        val cacheFile = fileFor(key)
        if (cacheFile.exists()) {
            check(cacheFile.delete()) {
                "Archive browse cache ${cacheFile.name} could not be deleted"
            }
        }
        Unit
    }

    private fun fileFor(key: ArchivePreparationKey): File =
        File(cacheDirectory, "${key.snapshotId.value}__${key.entryId.value}.json")

    private fun ensureDirectory() {
        if (cacheDirectory.exists()) {
            return
        }
        check(cacheDirectory.mkdirs()) { "Archive browse cache directory could not be created" }
    }
}
