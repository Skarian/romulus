package com.romulus.spikes.spike5.service

import com.romulus.spikes.spike5.model.AddedMagnetDto
import com.romulus.spikes.spike5.model.AvailableHostDto
import com.romulus.spikes.spike5.model.DeletedTorrentRecord
import com.romulus.spikes.spike5.model.FolderLinkDto
import com.romulus.spikes.spike5.model.ProviderStatusSample
import com.romulus.spikes.spike5.model.SelectedFileProbe
import com.romulus.spikes.spike5.model.Spike5Summary
import com.romulus.spikes.spike5.model.TorrentInfoDto
import com.romulus.spikes.spike5.model.TorrentSummaryDto
import com.romulus.spikes.spike5.model.TraceEvent
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
    fun createInvocation(startedAt: Instant = Instant.now()): Path {
        val invocationDir = artifactRoot.resolve(timestampFormatter.format(startedAt))
        invocationDir.createDirectories()
        artifactRoot.createDirectories()
        artifactRoot.resolve("latest-run.txt").writeText(invocationDir.toString() + "\n")
        return invocationDir
    }

    fun writeSanitizedInputs(
        invocationDir: Path,
        envFileLabel: String,
        magnetInfoHash: String,
        selectedFilePath: String,
    ) {
        invocationDir.resolve("sanitized-inputs.md").writeText(
            buildString {
                appendLine("# Sanitized Inputs")
                appendLine()
                appendLine("- env_file: $envFileLabel")
                appendLine("- api_token: PRESENT")
                appendLine("- magnet_info_hash: $magnetInfoHash")
                appendLine("- selected_file_path: $selectedFilePath")
            }
        )
    }

    fun writeTrace(invocationDir: Path, events: List<TraceEvent>) {
        writeJsonLines(invocationDir.resolve("trace.jsonl"), events)
    }

    fun writeMatchingTorrents(invocationDir: Path, torrents: List<TorrentSummaryDto>) {
        writeJson(invocationDir.resolve("matching-account-torrents.json"), torrents)
    }

    fun writeDeletedTorrents(invocationDir: Path, records: List<DeletedTorrentRecord>) {
        writeJson(invocationDir.resolve("deleted-torrents.json"), records)
    }

    fun writeAvailableHosts(invocationDir: Path, hosts: List<AvailableHostDto>) {
        writeJson(invocationDir.resolve("available-hosts.json"), hosts)
    }

    fun writeAddMagnet(invocationDir: Path, addMagnet: AddedMagnetDto) {
        writeJson(invocationDir.resolve("add-magnet.json"), addMagnet)
    }

    fun writeInitialInfo(invocationDir: Path, info: TorrentInfoDto) {
        writeJson(invocationDir.resolve("initial-info.json"), info)
    }

    fun writePostSelectInfo(invocationDir: Path, info: TorrentInfoDto) {
        writeJson(invocationDir.resolve("post-select-info.json"), info)
    }

    fun writeStatusTimeline(invocationDir: Path, timeline: List<ProviderStatusSample>) {
        writeJson(invocationDir.resolve("status-timeline.json"), timeline)
    }

    fun writeFinalInfo(invocationDir: Path, info: TorrentInfoDto) {
        writeJson(invocationDir.resolve("final-info.json"), info)
    }

    fun writeSelectedFileProbe(invocationDir: Path, probe: SelectedFileProbe) {
        writeJson(invocationDir.resolve("selected-file-probe.json"), probe)
    }

    fun writeFolderLinks(invocationDir: Path, links: List<FolderLinkDto>) {
        writeJson(invocationDir.resolve("folder-links.json"), links)
    }

    fun writeSummary(invocationDir: Path, summary: Spike5Summary) {
        writeJson(invocationDir.resolve("summary.json"), summary)
        invocationDir.resolve("summary.md").writeText(
            buildString {
                appendLine("# Spike 5 Summary")
                appendLine()
                appendLine("- status: ${summary.status}")
                appendLine("- cached_whole_torrent: ${summary.cachedWholeTorrent}")
                appendLine("- artifact_root: ${summary.artifactRoot}")
                appendLine("- env_file: ${summary.envFile}")
                appendLine("- magnet_info_hash: ${summary.magnetInfoHash}")
                appendLine("- deleted_match_count: ${summary.deletedMatchCount}")
                summary.addedTorrentId?.let { appendLine("- added_torrent_id: $it") }
                appendLine("- selected_file_path: ${summary.selectedFilePath}")
                appendLine("- selected_file_found: ${summary.selectedFileFound}")
                summary.selectedFileId?.let { appendLine("- selected_file_id: $it") }
                appendLine("- folder_link_count: ${summary.folderLinkCount}")
                appendLine("- folder_candidate_count: ${summary.folderCandidateCount}")
                appendLine("- selected_file_downloaded: ${summary.selectedFileDownloaded}")
                summary.finalStatus?.let { appendLine("- final_status: $it") }
                summary.finalProgress?.let { appendLine("- final_progress: $it") }
                appendLine("- final_link_count: ${summary.finalLinkCount}")
                appendLine("- observed_statuses: ${summary.observedStatuses.joinToString()}")
                appendLine("- started_at: ${summary.startedAt}")
                appendLine("- finished_at: ${summary.finishedAt}")
                appendLine()
                appendLine(summary.message)
            }
        )
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
