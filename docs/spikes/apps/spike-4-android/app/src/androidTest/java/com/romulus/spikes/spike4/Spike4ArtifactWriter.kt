package com.romulus.spikes.spike4

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString

class Spike4ArtifactWriter(private val context: Context) {
    private val externalRoot = requireNotNull(context.getExternalFilesDir(null)) {
        "App-specific external storage is unavailable for Spike 4 artifacts."
    }

    val latestRoot: File = externalRoot.resolve("spike-4/run-artifacts/latest")

    fun resetLatestRoot() {
        latestRoot.deleteRecursively()
        latestRoot.mkdirs()
    }

    fun runRoot(runId: String): File = latestRoot.resolve(runId).apply { mkdirs() }

    fun passRoot(runRoot: File, passNumber: Int): File {
        val passRoot = runRoot.resolve("outputs/passes/pass-${passNumber.toString().padStart(2, '0')}")
        passRoot.mkdirs()
        return passRoot
    }

    fun writeSession(record: SessionRecord) {
        writeJson(latestRoot.resolve("session.json"), record)
        latestRoot.resolve("summary.md").writeText(
            buildString {
                append("# Spike 4 Session\n\n")
                append("- Session: `${record.sessionId}`\n")
                append("- Device: `${record.device.manufacturer} ${record.device.model}`\n")
                append("- Android: `${record.device.androidRelease}`\n")
                append("- Runs:\n")
                record.runs.forEach { run ->
                    append("  - `${run.runId}`: `${run.status}` - ${run.summary}\n")
                }
            }
        )
    }

    fun writeRunDefinition(runRoot: File, record: Spike4RunDefinition) {
        writeJson(runRoot.resolve("run.json"), record)
    }

    fun writeResolverArtifacts(
        runRoot: File,
        resolverRecord: ResolverRecord,
        providerInfo: TorrentInfoDto,
    ) {
        val resolverRoot = runRoot.resolve("resolver").apply { mkdirs() }
        writeJson(resolverRoot.resolve("selection.json"), resolverRecord)
        writeJson(resolverRoot.resolve("provider-info.json"), providerInfo)
        writeJson(resolverRoot.resolve("link-mapping.json"), resolverRecord)
    }

    fun writeArchiveSelectionArtifacts(
        runRoot: File,
        candidates: List<EnumeratedEntry>,
        ignored: List<EnumeratedEntry>,
        selected: List<EnumeratedEntry>,
    ) {
        val selectionRoot = runRoot.resolve("archive-selection").apply { mkdirs() }
        writeJson(selectionRoot.resolve("candidate-set.json"), candidates)
        writeJson(selectionRoot.resolve("ignored-set.json"), ignored)
        writeJson(selectionRoot.resolve("selected-entry-set.json"), selected)
    }

    fun writeBeforeUnarchive(runRoot: File, manifest: OutputManifest) {
        val outputRoot = runRoot.resolve("outputs").apply { mkdirs() }
        writeJson(outputRoot.resolve("before-unarchive.json"), manifest)
    }

    fun writePassItems(passRoot: File, items: List<ArchiveItemRecord>) {
        writeJson(passRoot.resolve("items.json"), items)
    }

    fun writePassOutputManifest(passRoot: File, manifest: OutputManifest) {
        writeJson(passRoot.resolve("output-manifest.json"), manifest)
    }

    fun writeFinalOutputManifest(runRoot: File, manifest: OutputManifest) {
        val outputRoot = runRoot.resolve("outputs").apply { mkdirs() }
        writeJson(outputRoot.resolve("final-output-manifest.json"), manifest)
    }

    fun writeCleanup(runRoot: File, cleanupRecord: CleanupRecord) {
        writeJson(runRoot.resolve("cleanup.json"), cleanupRecord)
    }

    fun writeIdentityTrace(runRoot: File, records: List<IdentityTraceRecord>) {
        writeJson(runRoot.resolve("identity-trace.json"), records)
    }

    fun writeStageTimeline(runRoot: File, events: List<DiagnosticsEvent>) {
        runRoot.resolve("stage-timeline.jsonl").writeText(
            events.joinToString(separator = "\n", postfix = if (events.isEmpty()) "" else "\n") {
                spike4Json.encodeToString(it)
            }
        )
    }

    fun writeFailure(runRoot: File, failureRecord: FailureRecord) {
        writeJson(runRoot.resolve("failure.json"), failureRecord)
    }

    private inline fun <reified T> writeJson(destination: File, value: T) {
        destination.parentFile?.mkdirs()
        destination.writeText(spike4Json.encodeToString(value))
    }
}
