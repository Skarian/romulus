package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.config.Spike1Config
import com.romulus.spikes.spike1.model.AddedMagnetDto
import com.romulus.spikes.spike1.model.AvailableHostDto
import com.romulus.spikes.spike1.model.DeterministicFailureRecord
import com.romulus.spikes.spike1.model.DownloadManifestEntry
import com.romulus.spikes.spike1.model.KnownUncachedCheckpoint
import com.romulus.spikes.spike1.model.KnownUncachedSummary
import com.romulus.spikes.spike1.model.LifecycleMarker
import com.romulus.spikes.spike1.model.LinkMappingEntry
import com.romulus.spikes.spike1.model.LinkMappingStatus
import com.romulus.spikes.spike1.model.MatrixPlan
import com.romulus.spikes.spike1.model.MatrixSummary
import com.romulus.spikes.spike1.model.PollingSummary
import com.romulus.spikes.spike1.model.ProviderAcquisitionSample
import com.romulus.spikes.spike1.model.RunCase
import com.romulus.spikes.spike1.model.SelectionResolution
import com.romulus.spikes.spike1.model.TorrentFileDto
import com.romulus.spikes.spike1.model.TorrentInfoDto
import com.romulus.spikes.spike1.model.TraceEvent
import com.romulus.spikes.spike1.model.UnrestrictCallRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ArtifactWriter(
    private val artifactRoot: Path,
    private val json: Json = spikeJson(),
) {
    fun createInvocation(plan: MatrixPlan, startedAt: Instant = Instant.now()): InvocationContext {
        val invocationDir = artifactRoot.resolve(timestampFormatter.format(startedAt))
        invocationDir.createDirectories()
        val runDirs = plan.runs.associate { runCase ->
            val runDir = invocationDir.resolve(runCase.id)
            runDir.createDirectories()
            runCase.id to runDir
        }
        val latestPointer = artifactRoot.resolve("latest-run.txt")
        artifactRoot.createDirectories()
        latestPointer.writeText(invocationDir.toString() + "\n")
        return InvocationContext(invocationDir = invocationDir, runDirs = runDirs)
    }

    fun writeSanitizedInputs(
        context: InvocationContext,
        envFile: Path,
        config: Spike1Config,
        infoHash: String,
    ) {
        val text = buildString {
            appendLine("# Sanitized Inputs")
            appendLine()
            appendLine("- env_file: $envFile")
            appendLine("- api_token: PRESENT")
            appendLine("- magnet_info_hash: $infoHash")
            appendLine("- root_selected_paths: ${config.rootSelectedPaths.joinToString()}")
            appendLine("- directory_scope: ${config.directoryScope}")
            appendLine("- directory_selected_paths: ${config.directorySelectedPaths.joinToString()}")
            appendLine("- exact_zip_path: ${config.exactZipPath}")
            config.uncachedMagnet?.let { uncachedMagnet ->
                appendLine("- uncached_magnet_info_hash: ${RealDebridClient.extractInfoHash(uncachedMagnet) ?: "UNCONFIRMED"}")
            }
            config.uncachedSelectedPath?.let { appendLine("- uncached_selected_path: $it") }
        }
        context.invocationDir.resolve("sanitized-inputs.md").writeText(text)
    }

    fun writeRunArtifacts(
        context: InvocationContext,
        runCase: RunCase,
        payload: RunArtifactPayload,
    ) {
        val runDir = context.runDirs.getValue(runCase.id)
        writeJsonLines(runDir.resolve("trace.jsonl"), payload.traceEvents)
        writeJson(runDir.resolve("provider-files-before-selection.json"), payload.providerFilesBeforeSelection)
        writeJson(runDir.resolve("candidate-files.json"), payload.selectionResolution?.candidateFiles ?: emptyList<TorrentFileDto>())
        writeJson(
            runDir.resolve("selection-payload.json"),
            payload.selectionResolution?.let { resolution ->
                SelectionPayloadArtifact(
                    desiredPaths = resolution.desiredPaths,
                    resolvedFileIds = resolution.resolvedFiles.map { it.id },
                    resolvedPaths = resolution.resolvedFiles.map { it.path },
                    formPayload = resolution.payload,
                    usesAllLiteral = resolution.usesAllLiteral,
                )
            } ?: SelectionPayloadArtifact(),
        )
        writeJson(runDir.resolve("provider-files-after-selection.json"), payload.providerFilesAfterSelection)
        payload.lastPolledTorrentInfo?.let { writeJson(runDir.resolve("last-polled-torrent-info.json"), it) }
        payload.pollingSummary?.let { writeJson(runDir.resolve("polling-summary.json"), it) }
        writeJson(
            runDir.resolve("link-mapping.json"),
            LinkMappingArtifact(
                requestedSelectedFileIds = payload.selectionResolution?.resolvedFiles?.map { it.id }.orEmpty(),
                requestedSelectedPaths = payload.selectionResolution?.resolvedFiles?.map { it.path }.orEmpty(),
                providerSelectedFileIdsAfterSelection = payload.providerSelectedFilesAfterSelection.map { it.id },
                providerSelectedPathsAfterSelection = payload.providerSelectedFilesAfterSelection.map { it.path },
                returnedRestrictedLinks = payload.returnedRestrictedLinks,
                mappingStatus = payload.linkMappingStatus,
                mappingNote = payload.linkMappingNote,
                mappings = payload.linkMappings,
                unrestrictCalls = payload.unrestrictCalls,
            ),
        )
        runDir.resolve("result.md").writeText(payload.resultMarkdown.trimEnd() + "\n")
        if (payload.providerAcquisitionTimeline.isNotEmpty()) {
            writeJson(runDir.resolve("provider-acquisition-timeline.json"), payload.providerAcquisitionTimeline)
        }
        if (payload.lifecycleMarkers.isNotEmpty()) {
            writeJson(runDir.resolve("lifecycle-markers.json"), payload.lifecycleMarkers)
        }
        payload.knownUncachedCheckpoint?.let { writeJson(runDir.resolve("checkpoint.json"), it) }

        payload.availableHosts?.let { writeJson(runDir.resolve("available-hosts.json"), it) }
        payload.addMagnet?.let { writeJson(runDir.resolve("add-magnet.json"), it) }
        payload.deterministicFailureRecord?.let { writeJson(runDir.resolve("error.json"), it) }
        payload.downloadManifest?.let { writeJson(runDir.resolve("download-manifest.json"), it) }
    }

    fun writeMatrixSummary(context: InvocationContext, summary: MatrixSummary) {
        writeJson(context.invocationDir.resolve("matrix-summary.json"), summary)
        context.invocationDir.resolve("matrix-summary.md").writeText(toMarkdown(summary))
    }

    fun writeKnownUncachedSummary(context: InvocationContext, summary: KnownUncachedSummary) {
        writeJson(context.invocationDir.resolve("known-uncached-summary.json"), summary)
        context.invocationDir.resolve("known-uncached-summary.md").writeText(toMarkdown(summary))
    }

    fun writeKnownUncachedProgress(
        context: InvocationContext,
        runCase: RunCase,
        selectedFileIds: List<Int>,
        selectedPaths: List<String>,
        payload: RunArtifactPayload,
    ) {
        val runDir = context.runDirs.getValue(runCase.id)
        writeJsonLines(runDir.resolve("trace.jsonl"), payload.traceEvents)
        payload.lastPolledTorrentInfo?.let { writeJson(runDir.resolve("last-polled-torrent-info.json"), it) }
        payload.pollingSummary?.let { writeJson(runDir.resolve("polling-summary.json"), it) }
        writeJson(
            runDir.resolve("link-mapping.json"),
            LinkMappingArtifact(
                requestedSelectedFileIds = selectedFileIds,
                requestedSelectedPaths = selectedPaths,
                providerSelectedFileIdsAfterSelection = payload.providerSelectedFilesAfterSelection.map { it.id },
                providerSelectedPathsAfterSelection = payload.providerSelectedFilesAfterSelection.map { it.path },
                returnedRestrictedLinks = payload.returnedRestrictedLinks,
                mappingStatus = payload.linkMappingStatus,
                mappingNote = payload.linkMappingNote,
                mappings = payload.linkMappings,
                unrestrictCalls = payload.unrestrictCalls,
            ),
        )
        if (payload.providerAcquisitionTimeline.isNotEmpty()) {
            writeJson(runDir.resolve("provider-acquisition-timeline.json"), payload.providerAcquisitionTimeline)
        }
        if (payload.lifecycleMarkers.isNotEmpty()) {
            writeJson(runDir.resolve("lifecycle-markers.json"), payload.lifecycleMarkers)
        }
        payload.knownUncachedCheckpoint?.let { writeJson(runDir.resolve("checkpoint.json"), it) }
        runDir.resolve("result.md").writeText(payload.resultMarkdown.trimEnd() + "\n")
    }

    private fun toMarkdown(summary: MatrixSummary): String {
        return buildString {
            appendLine("# Spike 1 Matrix Summary")
            appendLine()
            appendLine("- gate_status: ${summary.gateStatus}")
            summary.blockerCode?.let { appendLine("- blocker_code: $it") }
            summary.blockerMessage?.let { appendLine("- blocker_message: $it") }
            appendLine("- artifact_root: ${summary.artifactRoot}")
            appendLine("- env_file: ${summary.envFile}")
            appendLine("- started_at: ${summary.startedAt}")
            appendLine("- finished_at: ${summary.finishedAt}")
            appendLine()
            appendLine("## Runs")
            appendLine()
            summary.runOutcomes.forEach { outcome ->
                appendLine("- ${outcome.runId}: ${outcome.status} - ${outcome.message}")
            }
            appendLine()
            appendLine("## Evidence Questions")
            appendLine()
            summary.evidenceQuestions.forEach { answer ->
                appendLine("- ${answer.id} [${answer.status}]: ${answer.question}")
                appendLine("  ${answer.answer}")
            }
        }
    }

    private fun toMarkdown(summary: KnownUncachedSummary): String {
        return buildString {
            appendLine("# Spike 1 Known-Uncached Summary")
            appendLine()
            appendLine("- status: ${summary.status}")
            appendLine("- artifact_root: ${summary.artifactRoot}")
            appendLine("- env_file: ${summary.envFile}")
            appendLine("- selected_path: ${summary.selectedPath}")
            summary.torrentId?.let { appendLine("- torrent_id: $it") }
            summary.lastObservedStatus?.let { appendLine("- last_observed_status: $it") }
            summary.lastProgress?.let { appendLine("- last_progress: $it") }
            summary.lastSpeed?.let { appendLine("- last_speed: $it") }
            summary.lastSeeders?.let { appendLine("- last_seeders: $it") }
            summary.firstReturnedLinksAt?.let { appendLine("- first_returned_links_at: $it") }
            appendLine("- total_samples: ${summary.totalSamples}")
            appendLine("- session_poll_attempts: ${summary.sessionPollAttempts}")
            appendLine("- started_at: ${summary.startedAt}")
            appendLine("- finished_at: ${summary.finishedAt}")
            appendLine()
            appendLine(summary.message)
        }
    }

    private inline fun <reified T> writeJson(path: Path, value: T) {
        path.parent?.createDirectories()
        path.writeText(json.encodeToString(value))
    }

    private fun writeJsonLines(path: Path, events: List<TraceEvent>) {
        path.parent?.createDirectories()
        Files.newBufferedWriter(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { writer ->
            events.forEach { event ->
                writer.appendLine(json.encodeToString(event))
            }
        }
    }

    companion object {
        private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
    }
}

data class InvocationContext(
    val invocationDir: Path,
    val runDirs: Map<String, Path>,
)

data class RunArtifactPayload(
    val traceEvents: List<TraceEvent>,
    val providerFilesBeforeSelection: List<TorrentFileDto> = emptyList(),
    val selectionResolution: SelectionResolution? = null,
    val providerFilesAfterSelection: List<TorrentFileDto> = emptyList(),
    val lastPolledTorrentInfo: TorrentInfoDto? = null,
    val pollingSummary: PollingSummary? = null,
    val providerSelectedFilesAfterSelection: List<TorrentFileDto> = emptyList(),
    val returnedRestrictedLinks: List<String> = emptyList(),
    val linkMappingStatus: LinkMappingStatus? = null,
    val linkMappingNote: String? = null,
    val linkMappings: List<LinkMappingEntry> = emptyList(),
    val unrestrictCalls: List<UnrestrictCallRecord> = emptyList(),
    val availableHosts: List<AvailableHostDto>? = null,
    val addMagnet: AddedMagnetDto? = null,
    val deterministicFailureRecord: DeterministicFailureRecord? = null,
    val downloadManifest: List<DownloadManifestEntry>? = null,
    val providerAcquisitionTimeline: List<ProviderAcquisitionSample> = emptyList(),
    val lifecycleMarkers: List<LifecycleMarker> = emptyList(),
    val knownUncachedCheckpoint: KnownUncachedCheckpoint? = null,
    val resultMarkdown: String,
)

@Serializable
private data class SelectionPayloadArtifact(
    val desiredPaths: List<String> = emptyList(),
    val resolvedFileIds: List<Int> = emptyList(),
    val resolvedPaths: List<String> = emptyList(),
    val formPayload: String = "",
    val usesAllLiteral: Boolean = false,
)

@Serializable
private data class LinkMappingArtifact(
    val requestedSelectedFileIds: List<Int>,
    val requestedSelectedPaths: List<String>,
    val providerSelectedFileIdsAfterSelection: List<Int>,
    val providerSelectedPathsAfterSelection: List<String>,
    val returnedRestrictedLinks: List<String>,
    val mappingStatus: LinkMappingStatus? = null,
    val mappingNote: String? = null,
    val mappings: List<LinkMappingEntry>,
    val unrestrictCalls: List<UnrestrictCallRecord>,
)
