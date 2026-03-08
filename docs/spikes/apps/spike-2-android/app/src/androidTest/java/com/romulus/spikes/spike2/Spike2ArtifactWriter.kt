package com.romulus.spikes.spike2

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString

class Spike2ArtifactWriter(private val context: Context) {
    private val externalRoot = requireNotNull(context.getExternalFilesDir(null)) {
        "App-specific external storage is unavailable for Spike 2 artifacts."
    }

    val latestRoot: File = externalRoot.resolve("spike-2/run-artifacts/latest")

    fun resetLatestRoot() {
        latestRoot.deleteRecursively()
        latestRoot.mkdirs()
    }

    fun runRoot(runId: String): File = latestRoot.resolve(runId).apply { mkdirs() }

    fun passRoot(runRoot: File, passNumber: Int): File {
        val passRoot = runRoot.resolve("passes/pass-${passNumber.toString().padStart(2, '0')}")
        passRoot.mkdirs()
        return passRoot
    }

    fun writeSession(record: SessionRecord) {
        writeJson(latestRoot.resolve("session.json"), record)
    }

    fun writeRunDefinition(runRoot: File, record: RunDefinition) {
        writeJson(runRoot.resolve("run.json"), record)
    }

    fun writeRuntime(runRoot: File, record: RuntimeInitRecord) {
        writeJson(runRoot.resolve("runtime-init.json"), record)
    }

    fun writeInputManifest(passRoot: File, manifest: PassInputManifest) {
        writeJson(passRoot.resolve("input-manifest.json"), manifest)
    }

    fun writeItems(passRoot: File, records: List<ArchiveItemRecord>) {
        writeJson(passRoot.resolve("items.json"), records)
    }

    fun writeOutputManifest(passRoot: File, manifest: OutputManifest) {
        writeJson(passRoot.resolve("output-manifest.json"), manifest)
    }

    fun writeCleanup(runRoot: File, record: CleanupRecord) {
        writeJson(runRoot.resolve("cleanup.json"), record)
    }

    fun writeFailure(runRoot: File, record: FailureRecord) {
        writeJson(runRoot.resolve("failure.json"), record)
    }

    fun writeVolumeResolution(runRoot: File, records: List<VolumeResolutionRecord>) {
        writeJson(runRoot.resolve("volume-resolution.json"), VolumeResolutionEnvelope(records))
    }

    private inline fun <reified T> writeJson(destination: File, value: T) {
        destination.parentFile?.mkdirs()
        destination.writeText(spike2Json.encodeToString(value))
    }
}
