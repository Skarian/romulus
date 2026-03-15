package com.romulus.spikes.spike5.service

import com.romulus.spikes.spike5.config.Spike5Config
import com.romulus.spikes.spike5.model.DeletedTorrentRecord
import com.romulus.spikes.spike5.model.FolderLinkDto
import com.romulus.spikes.spike5.model.ProviderStatusSample
import com.romulus.spikes.spike5.model.SelectedFileProbe
import com.romulus.spikes.spike5.model.Spike5Summary
import com.romulus.spikes.spike5.model.TorrentInfoDto
import com.romulus.spikes.spike5.model.TraceEvent
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.nio.file.Path
import java.time.Instant

class Spike5Runner(
    private val client: RealDebridClient,
    private val artifactWriter: ArtifactWriter,
    private val downloadVerifier: DownloadVerifier = DownloadVerifier(OkHttpClient()),
) {
    suspend fun run(envFile: Path, config: Spike5Config): Spike5RunReport {
        val startedAt = Instant.now()
        val invocationDir = artifactWriter.createInvocation(startedAt)
        val traceEvents = mutableListOf<TraceEvent>()
        val magnetInfoHash = MagnetInfoHash.extract(config.magnet)
            ?: error("Could not extract info hash from SPIKE5_MAGNET")
        artifactWriter.writeSanitizedInputs(
            invocationDir = invocationDir,
            envFileLabel = ".env.local",
            magnetInfoHash = magnetInfoHash,
            selectedFilePath = config.selectedFilePath,
        )

        val matches = client.listAccountTorrentsByHash(magnetInfoHash, traceEvents)
        artifactWriter.writeMatchingTorrents(invocationDir, matches)

        val deletedRecords = matches.map { match ->
            client.deleteTorrent(match.id, traceEvents)
            DeletedTorrentRecord(
                torrentId = match.id,
                status = match.status,
                filename = match.filename,
            )
        }
        artifactWriter.writeDeletedTorrents(invocationDir, deletedRecords)

        val hosts = client.getAvailableHosts(traceEvents)
        require(hosts.isNotEmpty()) { "No available hosts were returned by Real-Debrid" }
        artifactWriter.writeAvailableHosts(invocationDir, hosts)

        val addedMagnet = client.addMagnet(config.magnet, hosts.first().host, traceEvents)
        artifactWriter.writeAddMagnet(invocationDir, addedMagnet)

        val initialInfo = waitForFilesReady(addedMagnet.id, traceEvents)
        artifactWriter.writeInitialInfo(invocationDir, initialInfo)

        client.selectFiles(addedMagnet.id, "all", traceEvents)

        val immediatePostSelectInfo = client.getTorrentInfo(addedMagnet.id, traceEvents)
        artifactWriter.writePostSelectInfo(invocationDir, immediatePostSelectInfo)

        val pollResult = pollForCachedVerdict(addedMagnet.id, traceEvents, immediatePostSelectInfo)
        artifactWriter.writeStatusTimeline(invocationDir, pollResult.timeline)
        artifactWriter.writeFinalInfo(invocationDir, pollResult.finalInfo)

        val cachedWholeTorrent = pollResult.finalInfo.status == downloadedStatus &&
            (pollResult.finalInfo.progress ?: 0.0) >= requiredProgress
        val selectedFileProbe = if (cachedWholeTorrent) {
            probeSelectedFileDownload(
                invocationDir = invocationDir,
                selectedFilePath = config.selectedFilePath,
                cachedInfo = pollResult.finalInfo,
                traceEvents = traceEvents,
            )
        } else {
            SelectedFileProbe(
                requestedPath = config.selectedFilePath,
                resolvedFileFound = false,
            )
        }
        artifactWriter.writeSelectedFileProbe(invocationDir, selectedFileProbe)
        artifactWriter.writeTrace(invocationDir, traceEvents)

        val selectedFileDownloaded = selectedFileProbe.downloaded
        val overallPass = cachedWholeTorrent && selectedFileDownloaded
        val summary = Spike5Summary(
            startedAt = startedAt.toString(),
            finishedAt = Instant.now().toString(),
            artifactRoot = invocationDir.fileName.toString(),
            envFile = ".env.local",
            magnetInfoHash = magnetInfoHash,
            deletedMatchCount = deletedRecords.size,
            addedTorrentId = addedMagnet.id,
            selectedFilePath = config.selectedFilePath,
            observedStatuses = pollResult.timeline.mapNotNull { it.status }.distinct(),
            finalStatus = pollResult.finalInfo.status,
            finalProgress = pollResult.finalInfo.progress,
            finalLinkCount = pollResult.finalInfo.links.size,
            cachedWholeTorrent = cachedWholeTorrent,
            selectedFileFound = selectedFileProbe.resolvedFileFound,
            selectedFileId = selectedFileProbe.resolvedFileId,
            folderLinkCount = selectedFileProbe.folderLinkCount,
            folderCandidateCount = selectedFileProbe.folderCandidateCount,
            selectedFileDownloaded = selectedFileDownloaded,
            status = if (overallPass) "PASS" else "FAIL",
            message = buildSummaryMessage(
                cachedWholeTorrent = cachedWholeTorrent,
                cachedMessage = pollResult.message,
                selectedFileProbe = selectedFileProbe,
            ),
        )
        artifactWriter.writeSummary(invocationDir, summary)
        return Spike5RunReport(summary = summary, invocationDir = invocationDir)
    }

    private suspend fun waitForFilesReady(
        torrentId: String,
        traceEvents: MutableList<TraceEvent>,
    ): TorrentInfoDto {
        repeat(postAddInfoAttempts) { attempt ->
            val info = client.getTorrentInfo(torrentId, traceEvents)
            if (info.files.isNotEmpty()) {
                return info
            }
            if (attempt < postAddInfoAttempts - 1) {
                delay(postAddDelayMs)
            }
        }
        error("Torrent info never exposed files after addMagnet")
    }

    private suspend fun pollForCachedVerdict(
        torrentId: String,
        traceEvents: MutableList<TraceEvent>,
        initialInfo: TorrentInfoDto,
    ): PollResult {
        val timeline = mutableListOf(sample(initialInfo))
        if (isCached(initialInfo)) {
            return PollResult(
                finalInfo = initialInfo,
                timeline = timeline,
                message = "Whole-torrent selection reached downloaded/100 immediately after selectFiles(all).",
            )
        }
        if (initialInfo.status in terminalStatuses) {
            return PollResult(
                finalInfo = initialInfo,
                timeline = timeline,
                message = "Provider reached terminal state ${initialInfo.status} immediately after selectFiles(all).",
            )
        }

        var lastInfo = initialInfo
        repeat(selectionPollAttempts) { attempt ->
            delay(selectionPollDelayMs)
            val info = client.getTorrentInfo(torrentId, traceEvents)
            timeline += sample(info)
            lastInfo = info
            if (isCached(info)) {
                return PollResult(
                    finalInfo = info,
                    timeline = timeline,
                    message = "Whole-torrent selection reached downloaded/100 after ${attempt + 1} poll(s).",
                )
            }
            if (info.status in terminalStatuses) {
                return PollResult(
                    finalInfo = info,
                    timeline = timeline,
                    message = "Provider reached terminal state ${info.status} after selectFiles(all).",
                )
            }
        }

        return PollResult(
            finalInfo = lastInfo,
            timeline = timeline,
            message = "Whole-torrent selection did not reach downloaded/100 within the bounded polling budget.",
        )
    }

    private fun sample(info: TorrentInfoDto): ProviderStatusSample {
        return ProviderStatusSample(
            timestamp = Instant.now().toString(),
            status = info.status,
            progress = info.progress,
            speed = info.speed,
            seeders = info.seeders,
            linkCount = info.links.size,
        )
    }

    private fun isCached(info: TorrentInfoDto): Boolean {
        return info.status == downloadedStatus && (info.progress ?: 0.0) >= requiredProgress
    }

    private suspend fun probeSelectedFileDownload(
        invocationDir: Path,
        selectedFilePath: String,
        cachedInfo: TorrentInfoDto,
        traceEvents: MutableList<TraceEvent>,
    ): SelectedFileProbe {
        val selectedFile = cachedInfo.files.firstOrNull { it.path == selectedFilePath }
            ?: return SelectedFileProbe(
                requestedPath = selectedFilePath,
                resolvedFileFound = false,
            )

        val wholeTorrentLink = cachedInfo.links.singleOrNull()
        if (wholeTorrentLink == null) {
            return SelectedFileProbe(
                requestedPath = selectedFilePath,
                resolvedFileId = selectedFile.id,
                resolvedFileBytes = selectedFile.bytes,
                resolvedFileFound = true,
            )
        }

        val folderLinksResult = runCatching { client.unrestrictFolder(wholeTorrentLink, traceEvents) }
        val folderLinks = folderLinksResult.getOrNull().orEmpty()
        if (folderLinksResult.isSuccess) {
            artifactWriter.writeFolderLinks(invocationDir, folderLinks)
        }

        val candidates = findFolderCandidates(selectedFilePath, selectedFile.bytes, folderLinks)
        val matchedLink = candidates.singleOrNull()?.takeIf { !it.downloadUrl.isNullOrBlank() }
        val downloadManifest = matchedLink?.downloadUrl?.let { downloadUrl ->
            downloadVerifier.downloadSingle(
                downloadUrl = downloadUrl,
                responseFilename = matchedLink.filename,
                outputDirectory = invocationDir.resolve("downloads"),
            )
        }
        return SelectedFileProbe(
            requestedPath = selectedFilePath,
            resolvedFileId = selectedFile.id,
            resolvedFileBytes = selectedFile.bytes,
            resolvedFileFound = true,
            wholeTorrentLinkPresent = true,
            folderProbeAttempted = true,
            folderProbeError = folderLinksResult.exceptionOrNull()?.message,
            folderLinkCount = folderLinks.size,
            folderCandidateCount = candidates.size,
            folderMatchedFilename = matchedLink?.filename,
            folderMatchedFileSize = matchedLink?.fileSize,
            folderMatchedDownloadPresent = matchedLink?.downloadUrl != null,
            downloaded = downloadManifest != null,
            downloadManifest = downloadManifest,
        )
    }

    private fun findFolderCandidates(
        selectedFilePath: String,
        selectedFileBytes: Long,
        folderLinks: List<FolderLinkDto>,
    ): List<FolderLinkDto> {
        val targetName = selectedFilePath.substringAfterLast('/')
        val exactNameMatches = folderLinks.filter { it.filename == targetName }
        val exactNameAndSizeMatches = exactNameMatches.filter { it.fileSize == selectedFileBytes }
        return when {
            exactNameAndSizeMatches.isNotEmpty() -> exactNameAndSizeMatches
            exactNameMatches.isNotEmpty() -> exactNameMatches
            else -> emptyList()
        }
    }

    private fun buildSummaryMessage(
        cachedWholeTorrent: Boolean,
        cachedMessage: String,
        selectedFileProbe: SelectedFileProbe,
    ): String {
        if (!cachedWholeTorrent) {
            return cachedMessage
        }
        if (!selectedFileProbe.resolvedFileFound) {
            return "$cachedMessage Selected file ${selectedFileProbe.requestedPath} was not found in the cached torrent."
        }
        if (!selectedFileProbe.wholeTorrentLinkPresent) {
            return "$cachedMessage The cached whole torrent did not expose exactly one whole-torrent link to probe."
        }
        if (selectedFileProbe.folderProbeError != null) {
            return "$cachedMessage Folder-unrestrict probe failed for ${selectedFileProbe.requestedPath}: ${selectedFileProbe.folderProbeError}."
        }
        if (selectedFileProbe.folderCandidateCount != 1) {
            return "$cachedMessage Folder-unrestrict probe produced ${selectedFileProbe.folderCandidateCount} candidate link(s) for ${selectedFileProbe.requestedPath}."
        }
        if (!selectedFileProbe.folderMatchedDownloadPresent || !selectedFileProbe.downloaded) {
            return "$cachedMessage Folder-unrestrict probe found a candidate for ${selectedFileProbe.requestedPath} but did not produce a downloadable direct link."
        }
        return "$cachedMessage Folder-unrestrict probe mapped ${selectedFileProbe.requestedPath} to one child link and downloaded it successfully."
    }

    companion object {
        private const val postAddInfoAttempts = 10
        private const val postAddDelayMs = 2000L
        private const val selectionPollAttempts = 20
        private const val selectionPollDelayMs = 2000L
        private const val downloadedStatus = "downloaded"
        private const val requiredProgress = 100.0
        private val terminalStatuses = setOf("magnet_error", "error", "virus", "dead")
    }
}

data class Spike5RunReport(
    val summary: Spike5Summary,
    val invocationDir: Path,
)

private data class PollResult(
    val finalInfo: TorrentInfoDto,
    val timeline: List<ProviderStatusSample>,
    val message: String,
)
