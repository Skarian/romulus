package com.romulus.spikes.spike3.artifacts

import com.romulus.spikes.spike3.errors.FailureCodes
import com.romulus.spikes.spike3.errors.Spike3FailureException
import com.romulus.spikes.spike3.model.CaseFailure
import com.romulus.spikes.spike3.model.CaseResult
import com.romulus.spikes.spike3.model.EnumeratedEntry
import com.romulus.spikes.spike3.model.ExpectedOutcome
import com.romulus.spikes.spike3.model.FilteredEntries
import com.romulus.spikes.spike3.model.FailureStage
import com.romulus.spikes.spike3.model.HttpTraceEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class ArtifactWriter(private val artifactRoot: Path) {
    fun openRun(runId: String): Path {
        return try {
            Files.createDirectories(artifactRoot)
            Files.createDirectories(artifactRoot.resolve(runId))
        } catch (failure: IOException) {
            throw Spike3FailureException(
                stage = FailureStage.ARTIFACT_WRITE,
                errorCode = FailureCodes.ARTIFACT_WRITE_FAILED,
                message = "Failed to create run directory under $artifactRoot",
                cause = failure,
            )
        }
    }

    fun writeCandidateSet(
        runDirectory: Path,
        caseId: String,
        allEntries: List<EnumeratedEntry>,
        filteredEntries: FilteredEntries,
        ignoreGlobs: List<String>,
    ) {
        val file = caseDirectory(runDirectory, caseId).resolve("candidate-set.md")
        val directories = allEntries.count { it.isDirectory }
        val files = allEntries.count { !it.isDirectory }
        val visibleSample = renderSample(filteredEntries.visibleEntries)
        val rawSample = renderSample(allEntries.filterNot { it.isDirectory })
        val ignoredSample = renderSample(filteredEntries.ignoredEntries)
        writeText(
            file,
            buildString {
                appendLine("# Candidate Set")
                appendLine()
                appendLine("- Raw archive entries: ${allEntries.size}")
                appendLine("- Raw file entries: $files")
                appendLine("- Raw directory entries: $directories")
                appendLine("- Ignored file entries: ${filteredEntries.ignoredEntries.size}")
                appendLine("- Visible file entries: ${filteredEntries.visibleEntries.size}")
                appendLine("- Selected file entries: ${filteredEntries.selectedEntries.size}")
                appendLine("- Ignore globs: ${ignoreGlobs.ifEmpty { listOf("(none)") }.joinToString()}")
                appendLine()
                appendLine("## Visible Sample")
                append(visibleSample)
                appendLine()
                appendLine("## Ignored Sample")
                append(ignoredSample)
                appendLine()
                appendLine("## Raw File Sample")
                append(rawSample)
            },
        )
    }

    fun writeDownloadManifest(
        runDirectory: Path,
        caseId: String,
        filteredEntries: FilteredEntries,
        downloadedFiles: List<Path>,
    ) {
        val file = caseDirectory(runDirectory, caseId).resolve("download-manifest.md")
        val selectedLines = filteredEntries.selectedEntries.zip(downloadedFiles).joinToString("\n") { (entry, output) ->
            "- ${entry.identity} -> ${output.fileName}"
        }
        val unselectedLines = filteredEntries.visibleEntries
            .filterNot { visible -> filteredEntries.selectedEntries.any { it.identity == visible.identity } }
            .joinToString("\n") { "- ${it.identity}" }
            .ifBlank { "- (none)" }
        writeText(
            file,
            buildString {
                appendLine("# Download Manifest")
                appendLine()
                appendLine("## Downloaded")
                appendLine(selectedLines)
                appendLine()
                appendLine("## Visible But Not Downloaded")
                appendLine(unselectedLines)
            },
        )
    }

    fun writeFailure(
        runDirectory: Path,
        caseId: String,
        fixtureName: String,
        archiveUrl: String,
        failure: CaseFailure,
    ) {
        val file = caseDirectory(runDirectory, caseId).resolve("failure.md")
        writeText(
            file,
            buildString {
                appendLine("# Failure")
                appendLine()
                appendLine("- Fixture: $fixtureName")
                appendLine("- URL: $archiveUrl")
                appendLine("- Stage: ${failure.stage}")
                appendLine("- Error code: ${failure.errorCode}")
                appendLine("- Cause class: ${failure.causeClass ?: "(none)"}")
                appendLine()
                appendLine(failure.message)
                appendLine()
                appendLine("No fallback full download path was attempted.")
            },
        )
    }

    fun writeHttpTrace(runDirectory: Path, events: List<HttpTraceEvent>) {
        val file = runDirectory.resolve("http-trace.ndjson")
        val lines = events.joinToString("\n") { event ->
            buildString {
                append("{\"timestamp\":\"").append(escape(event.timestamp.toString())).append('\"')
                append(",\"caseId\":\"").append(escape(event.caseId)).append('\"')
                append(",\"stage\":\"").append(event.stage.name).append('\"')
                append(",\"method\":\"").append(event.method).append('\"')
                append(",\"path\":\"").append(escape(event.path)).append('\"')
                append(",\"requestRange\":").append(event.requestRange?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"responseCode\":").append(event.responseCode ?: "null")
                append(",\"contentLength\":").append(event.contentLength ?: "null")
                append(",\"contentRange\":").append(event.contentRange?.let { "\"${escape(it)}\"" } ?: "null")
                append(",\"elapsedMillis\":").append(event.elapsedMillis)
                append(",\"failureMessage\":").append(event.failureMessage?.let { "\"${escape(it)}\"" } ?: "null")
                append('}')
            }
        }
        writeText(file, if (lines.isBlank()) "" else "$lines\n")
    }

    fun writeSummary(runDirectory: Path, results: List<CaseResult>, events: List<HttpTraceEvent>) {
        val file = runDirectory.resolve("summary.md")
        val successPathRangedGetsDowngradedTo200 = events.count { event ->
            event.method == "GET" && event.requestRange != null && event.responseCode == 200 && results.any {
                it.case.id == event.caseId &&
                    it.status == com.romulus.spikes.spike3.model.CaseStatus.PASSED &&
                    it.case.expectedOutcome !is ExpectedOutcome.Failure
            }
        }
        val lines = results.joinToString("\n") { result ->
            val expectation = if (result.matchedExpectation) "matched" else "mismatched"
            val detail = result.failure?.let { "${it.stage}/${it.errorCode}" } ?: "success"
            "- ${result.case.id}: ${result.status} ($expectation, $detail)"
        }
        writeText(
            file,
            buildString {
                appendLine("# Summary")
                appendLine()
                appendLine(lines)
                appendLine()
                appendLine("- Success-path ranged GETs downgraded to 200 responses: $successPathRangedGetsDowngradedTo200")
            },
        )
    }

    private fun caseDirectory(runDirectory: Path, caseId: String): Path {
        return try {
            Files.createDirectories(runDirectory.resolve("cases").resolve(caseId))
        } catch (failure: IOException) {
            throw Spike3FailureException(
                stage = FailureStage.ARTIFACT_WRITE,
                errorCode = FailureCodes.ARTIFACT_WRITE_FAILED,
                message = "Failed to create case artifact directory for $caseId",
                cause = failure,
            )
        }
    }

    private fun renderSample(entries: List<EnumeratedEntry>, edgeCount: Int = 5): String {
        if (entries.isEmpty()) {
            return "- (none)\n"
        }
        val sample = if (entries.size <= edgeCount * 2) {
            entries
        } else {
            entries.take(edgeCount) + entries.takeLast(edgeCount)
        }
        val lines = sample.joinToString("\n") { "- ${it.identity}" }
        val suffix = if (sample.size < entries.size) "\n- ... (${entries.size - sample.size} more)" else ""
        return "$lines$suffix\n"
    }

    private fun writeText(file: Path, content: String) {
        try {
            Files.writeString(file, content)
        } catch (failure: IOException) {
            throw Spike3FailureException(
                stage = FailureStage.ARTIFACT_WRITE,
                errorCode = FailureCodes.ARTIFACT_WRITE_FAILED,
                message = "Failed to write artifact $file",
                cause = failure,
            )
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}
