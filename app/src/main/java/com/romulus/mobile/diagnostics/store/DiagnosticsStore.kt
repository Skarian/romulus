package com.romulus.mobile.diagnostics.store

import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.events.DiagnosticsManifest
import com.romulus.mobile.diagnostics.events.DiagnosticsSummary
import com.romulus.mobile.diagnostics.events.isFailureOutcome
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class DiagnosticsArtifactSnapshot(
    val manifest: File,
    val timeline: File,
    val failures: File,
    val summary: File,
    val totalBytes: Long
)

internal data class StagedInternalClear(val backupRoot: File?)

internal interface DiagnosticsFilesystem {
    val diagnosticsRoot: File

    val backupRootParent: File
}

@Suppress("TooManyFunctions")
internal class DiagnosticsStore(
    private val filesystem: DiagnosticsFilesystem,
    private val json: Json
) {
    fun appendTimeline(event: DiagnosticEvent): Result<Unit> = appendJsonLine(
        file = timelineFile(),
        event = event
    )

    fun appendFailure(event: DiagnosticEvent): Result<Unit> = appendJsonLine(
        file = failuresFile(),
        event = event
    )

    fun writeManifest(manifest: DiagnosticsManifest): Result<Unit> = runCatching {
        ensureFiles()
        manifestFile().writeText(json.encodeToString(manifest))
    }

    fun writeSummary(summary: DiagnosticsSummary): Result<Unit> = runCatching {
        ensureFiles()
        summaryFile().writeText(json.encodeToString(summary))
    }

    fun snapshot(): Result<DiagnosticsArtifactSnapshot> = runCatching {
        ensureFiles()
        DiagnosticsArtifactSnapshot(
            manifest = manifestFile(),
            timeline = timelineFile(),
            failures = failuresFile(),
            summary = summaryFile(),
            totalBytes = totalBytes()
        )
    }

    fun stageInternalClear(): Result<StagedInternalClear> = runCatching {
        val root = filesystem.diagnosticsRoot
        if (!root.exists()) {
            StagedInternalClear(backupRoot = null)
        } else {
            val backupParent = filesystem.backupRootParent
            if (!backupParent.exists()) {
                check(backupParent.mkdirs()) { "Diagnostics backup directory could not be created" }
            }
            val backupRoot = File(
                backupParent,
                "clear-${UUID.randomUUID()}"
            )
            move(root, backupRoot)
            StagedInternalClear(backupRoot = backupRoot)
        }
    }

    fun commitInternalClear(staged: StagedInternalClear): Result<Unit> = runCatching {
        staged.backupRoot?.let { backupRoot ->
            if (backupRoot.exists()) {
                check(backupRoot.deleteRecursively()) {
                    "Diagnostics backup could not be deleted"
                }
            }
        }
        Unit
    }

    fun rollbackInternalClear(staged: StagedInternalClear): Result<Unit> = runCatching {
        val backupRoot = staged.backupRoot ?: return@runCatching Unit
        val root = filesystem.diagnosticsRoot
        if (root.exists()) {
            check(root.deleteRecursively()) { "Diagnostics root could not be reset" }
        }
        if (backupRoot.exists()) {
            move(backupRoot, root)
        }
        Unit
    }

    internal fun readTimelineEvents(): Result<List<DiagnosticEvent>> = runCatching {
        ensureFiles()
        timelineFile()
            .readLines()
            .filter(String::isNotBlank)
            .map { line ->
                json.decodeFromString<DiagnosticEvent>(line)
            }
    }

    internal fun overwriteRetainedEvents(events: List<DiagnosticEvent>): Result<Unit> =
        runCatching {
            ensureFiles()
            timelineFile().writeText(jsonLines(events))
            failuresFile().writeText(
                jsonLines(events.filter { event -> event.isFailureOutcome() })
            )
            Unit
        }

    internal fun readSummary(): Result<DiagnosticsSummary?> = runCatching {
        ensureFiles()
        summaryFile().takeIf(File::exists)?.takeIf { it.length() > 0L }?.let { file ->
            json.decodeFromString<DiagnosticsSummary>(file.readText())
        }
    }

    private fun appendJsonLine(file: File, event: DiagnosticEvent): Result<Unit> = runCatching {
        ensureFiles()
        file.appendText("${json.encodeToString(event)}\n")
    }

    private fun ensureFiles() {
        val root = filesystem.diagnosticsRoot
        if (!root.exists()) {
            check(root.mkdirs()) { "Diagnostics directory could not be created" }
        }
        ensureFile(manifestFile())
        ensureFile(timelineFile())
        ensureFile(failuresFile())
        ensureFile(summaryFile())
    }

    private fun ensureFile(file: File) {
        if (!file.exists()) {
            check(file.createNewFile()) { "${file.name} could not be created" }
        }
    }

    private fun totalBytes(): Long = listOf(
        manifestFile(),
        timelineFile(),
        failuresFile(),
        summaryFile()
    ).sumOf(File::length)

    private fun jsonLines(events: List<DiagnosticEvent>): String = events
        .joinToString(separator = "\n") { event -> json.encodeToString(event) }
        .let { body ->
            if (body.isBlank()) {
                ""
            } else {
                "$body\n"
            }
        }

    private fun move(source: File, target: File) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun manifestFile(): File = File(filesystem.diagnosticsRoot, MANIFEST_FILE_NAME)

    private fun timelineFile(): File = File(filesystem.diagnosticsRoot, TIMELINE_FILE_NAME)

    private fun failuresFile(): File = File(filesystem.diagnosticsRoot, FAILURES_FILE_NAME)

    private fun summaryFile(): File = File(filesystem.diagnosticsRoot, SUMMARY_FILE_NAME)

    private companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val TIMELINE_FILE_NAME = "timeline.jsonl"
        const val FAILURES_FILE_NAME = "failures.jsonl"
        const val SUMMARY_FILE_NAME = "summary.json"
    }
}
