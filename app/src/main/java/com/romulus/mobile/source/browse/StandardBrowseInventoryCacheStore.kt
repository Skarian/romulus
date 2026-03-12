package com.romulus.mobile.source.browse

import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.torrentmeta.TorrentMetadataInventory
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface StandardBrowseInventoryCacheStore {
    suspend fun read(snapshotId: SnapshotId, entryId: SourceEntryId): TorrentMetadataInventory?

    suspend fun write(
        snapshotId: SnapshotId,
        entryId: SourceEntryId,
        inventory: TorrentMetadataInventory
    ): Result<Unit>
}

internal class FileStandardBrowseInventoryCacheStore(
    private val cacheDirectory: File,
    private val json: Json
) : StandardBrowseInventoryCacheStore {
    override suspend fun read(
        snapshotId: SnapshotId,
        entryId: SourceEntryId
    ): TorrentMetadataInventory? {
        val cacheFile = fileFor(snapshotId, entryId)
        if (!cacheFile.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<TorrentMetadataInventory>(cacheFile.readText())
        }.getOrNull()
    }

    override suspend fun write(
        snapshotId: SnapshotId,
        entryId: SourceEntryId,
        inventory: TorrentMetadataInventory
    ): Result<Unit> = runCatching {
        ensureDirectory()
        fileFor(snapshotId, entryId).writeText(json.encodeToString(inventory))
        Unit
    }

    private fun fileFor(snapshotId: SnapshotId, entryId: SourceEntryId): File =
        File(cacheDirectory, "${snapshotId.value}__${entryId.value}.json")

    private fun ensureDirectory() {
        if (cacheDirectory.exists()) {
            return
        }
        check(cacheDirectory.mkdirs()) { "Standard browse cache directory could not be created" }
    }
}
