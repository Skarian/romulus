package com.romulus.mobile.source.snapshot

import android.content.Context
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class StagedSnapshot(val snapshotId: SnapshotId)

interface SnapshotStore {
    suspend fun read(snapshotId: SnapshotId): SourceSnapshot?

    suspend fun writeStaged(snapshot: SourceSnapshot): Result<StagedSnapshot>

    suspend fun discard(staged: StagedSnapshot): Result<Unit>
}

internal class FileSnapshotStore(context: Context, private val json: Json) : SnapshotStore {
    private val snapshotDirectory = File(context.filesDir, SNAPSHOT_DIRECTORY)

    override suspend fun read(snapshotId: SnapshotId): SourceSnapshot? {
        val snapshotFile = fileFor(snapshotId)
        if (!snapshotFile.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<SourceSnapshot>(snapshotFile.readText())
        }.getOrNull()
    }

    override suspend fun writeStaged(snapshot: SourceSnapshot): Result<StagedSnapshot> =
        runCatching {
            ensureDirectory()
            fileFor(snapshot.snapshotId).writeText(json.encodeToString(snapshot))
            StagedSnapshot(snapshot.snapshotId)
        }

    override suspend fun discard(staged: StagedSnapshot): Result<Unit> = runCatching {
        val snapshotFile = fileFor(staged.snapshotId)
        if (snapshotFile.exists()) {
            check(snapshotFile.delete()) {
                "Snapshot ${staged.snapshotId.value} could not be discarded"
            }
        }
        Unit
    }

    private fun fileFor(snapshotId: SnapshotId): File =
        File(snapshotDirectory, "${snapshotId.value}.json")

    private fun ensureDirectory() {
        if (snapshotDirectory.exists()) {
            return
        }
        check(snapshotDirectory.mkdirs()) { "Snapshot directory could not be created" }
    }

    private companion object {
        const val SNAPSHOT_DIRECTORY = "source_snapshots"
    }
}
