package com.romulus.mobile.source

import com.romulus.mobile.source.ingest.AcceptedSourceConfigRecord
import com.romulus.mobile.source.ingest.SourceConfigStore
import com.romulus.mobile.source.ingest.SourceLoader
import com.romulus.mobile.source.ingest.SourceSchemaValidator
import com.romulus.mobile.source.ingest.StagedSourceConfig
import com.romulus.mobile.source.snapshot.SourceActivationId
import com.romulus.mobile.source.snapshot.SourceActivationRecord
import com.romulus.mobile.source.snapshot.SourceActivationStore
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SnapshotStore
import com.romulus.mobile.source.snapshot.SourceSnapshot
import com.romulus.mobile.source.snapshot.StagedSnapshot
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant

internal class FakeSourceLoader : SourceLoader {
    private val urlBodies = mutableMapOf<String, String>()
    private val uriBodies = mutableMapOf<String, String>()
    private val unreadableUris = mutableSetOf<String>()
    private val failures = mutableMapOf<String, Throwable>()

    fun setUrlBody(url: String, body: String) {
        urlBodies[url] = body
    }

    fun setUriBody(uri: String, body: String) {
        uriBodies[uri] = body
    }

    fun setUnreadable(uri: String) {
        unreadableUris += uri
    }

    fun setFailure(key: String, error: Throwable) {
        failures[key] = error
    }

    override suspend fun loadFromUrl(url: String): Result<ByteArray> =
        failures[url]?.let(Result.Companion::failure)
            ?: urlBodies[url]?.toByteArray(StandardCharsets.UTF_8)?.let(Result.Companion::success)
            ?: Result.failure(IllegalStateException("No source body registered for $url"))

    override suspend fun loadFromPersistedUri(uri: String): Result<ByteArray> =
        failures[uri]?.let(Result.Companion::failure)
            ?: if (uri in unreadableUris) {
                Result.failure(IllegalStateException("Source document could not be opened"))
            } else {
                uriBodies[uri]
                    ?.toByteArray(StandardCharsets.UTF_8)
                    ?.let(Result.Companion::success)
                    ?: Result.failure(IllegalStateException("No source body registered for $uri"))
            }

    override suspend fun canReadPersistedUri(uri: String): Result<Unit> =
        if (uri in unreadableUris) {
            Result.failure(IllegalStateException("Source document could not be opened"))
        } else {
            Result.success(Unit)
        }
}

internal class InMemorySourceConfigStore : SourceConfigStore {
    private val records = linkedMapOf<String, AcceptedSourceConfigRecord>()
    private var nextStage = 0

    override suspend fun readStaged(stageId: String): AcceptedSourceConfigRecord? = records[stageId]

    override suspend fun stageCandidate(next: AcceptedSourceConfigRecord): Result<StagedSourceConfig> {
        val stageId = "config-${nextStage++}"
        records[stageId] = next
        return Result.success(
            StagedSourceConfig(
                stageId = stageId,
                acceptedAt = next.acceptedAt
            )
        )
    }

    override suspend fun discardCandidate(staged: StagedSourceConfig): Result<Unit> {
        records.remove(staged.stageId)
        return Result.success(Unit)
    }
}

internal class InMemorySourceActivationStore : SourceActivationStore {
    var active: SourceActivationRecord? = null
    var failActivation = false
    private var nextActivation = 0

    override suspend fun readActive(): SourceActivationRecord? = active

    override suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord> {
        if (failActivation) {
            return Result.failure(IllegalStateException("Activation failed"))
        }
        return SourceActivationRecord(
            activationId = SourceActivationId("activation-${nextActivation++}"),
            configStageId = stagedConfig.stageId,
            snapshotId = stagedSnapshot.snapshotId,
            activatedAt = activatedAt
        ).also { record ->
            active = record
        }.let(Result.Companion::success)
    }
}

internal class InMemorySnapshotStore : SnapshotStore {
    private val snapshots = linkedMapOf<SnapshotId, SourceSnapshot>()

    override suspend fun read(snapshotId: SnapshotId): SourceSnapshot? = snapshots[snapshotId]

    override suspend fun writeStaged(snapshot: SourceSnapshot): Result<StagedSnapshot> {
        snapshots[snapshot.snapshotId] = snapshot
        return Result.success(StagedSnapshot(snapshot.snapshotId))
    }

    override suspend fun discard(staged: StagedSnapshot): Result<Unit> {
        snapshots.remove(staged.snapshotId)
        return Result.success(Unit)
    }
}

internal fun sourceSchemaValidator(): SourceSchemaValidator =
    SourceSchemaValidator.fromSchemaData(findSchemaFile().readText())

private fun findSchemaFile(): File {
    var current = File(System.getProperty("user.dir") ?: ".")
    repeat(5) {
        val candidate = current.resolve("docs/schema.json")
        if (candidate.exists()) {
            return candidate
        }
        current = current.parentFile ?: return@repeat
    }
    error("docs/schema.json could not be located from ${System.getProperty("user.dir")}")
}
