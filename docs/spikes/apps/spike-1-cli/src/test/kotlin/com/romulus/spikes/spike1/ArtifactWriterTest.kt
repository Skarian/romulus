package com.romulus.spikes.spike1

import com.romulus.spikes.spike1.config.Spike1Config
import com.romulus.spikes.spike1.model.MatrixGateStatus
import com.romulus.spikes.spike1.model.MatrixPlan
import com.romulus.spikes.spike1.model.MatrixSummary
import com.romulus.spikes.spike1.model.KnownUncachedStatus
import com.romulus.spikes.spike1.model.KnownUncachedSummary
import com.romulus.spikes.spike1.model.PollingSummary
import com.romulus.spikes.spike1.model.ProviderAcquisitionSample
import com.romulus.spikes.spike1.model.RunCase
import com.romulus.spikes.spike1.model.ScopeKind
import com.romulus.spikes.spike1.model.TorrentInfoDto
import com.romulus.spikes.spike1.model.TraceEvent
import com.romulus.spikes.spike1.service.ArtifactWriter
import com.romulus.spikes.spike1.service.RunArtifactPayload
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class ArtifactWriterTest {
    @Test
    fun writesInvocationTreeAndLatestPointer() {
        val root = createTempDirectory(prefix = "spike1-artifacts-")
        val writer = ArtifactWriter(root)
        val runCase = RunCase(
            id = "run-b-add-directory",
            specLabel = "Run B",
            executionOrder = 1,
            scopeKind = ScopeKind.DIRECTORY,
            scopePath = "/Folder",
            desiredPaths = listOf("/Folder/file.zip"),
        )
        val context = writer.createInvocation(MatrixPlan(listOf(runCase)), Instant.parse("2026-03-06T12:00:00Z"))

        writer.writeSanitizedInputs(
            context = context,
            envFile = root.resolve(".env.local"),
            config = Spike1Config(
                apiToken = "token",
                magnet = "magnet:?xt=urn:btih:1234567890123456789012345678901234567890",
                rootSelectedPaths = listOf("/Root/file.mkv"),
                directoryScope = "/Folder",
                directorySelectedPaths = listOf("/Folder/file.zip"),
                exactZipPath = "/Folder/file.zip",
            ),
            infoHash = "1234567890123456789012345678901234567890",
        )
        writer.writeRunArtifacts(
            context = context,
            runCase = runCase,
            payload = RunArtifactPayload(
                traceEvents = listOf(
                    TraceEvent(
                        timestamp = "2026-03-06T12:00:01Z",
                        stage = "http-success",
                        method = "GET",
                        endpoint = "/torrents",
                        statusCode = 200,
                        summary = "ok",
                    )
                ),
                lastPolledTorrentInfo = TorrentInfoDto(
                    id = "torrent-1",
                    status = "downloading",
                ),
                pollingSummary = PollingSummary(
                    attempts = 2,
                    elapsedMillis = 4000,
                    lastObservedStatus = "downloading",
                    firstNonPreselectionStatus = "queued",
                    firstNonPreselectionElapsedMillis = 500,
                ),
                resultMarkdown = "# Run B\n\nPASS",
            ),
        )
        writer.writeMatrixSummary(
            context = context,
            summary = MatrixSummary(
                startedAt = "2026-03-06T12:00:00Z",
                finishedAt = "2026-03-06T12:00:01Z",
                gateStatus = MatrixGateStatus.FAIL,
                artifactRoot = context.invocationDir.toString(),
                envFile = root.resolve(".env.local").toString(),
                runOrder = listOf(runCase.id),
                runOutcomes = emptyList(),
                evidenceQuestions = emptyList(),
            ),
        )

        assertTrue(context.invocationDir.resolve("sanitized-inputs.md").exists())
        assertTrue(context.invocationDir.resolve("matrix-summary.json").exists())
        assertTrue(context.invocationDir.resolve("matrix-summary.md").exists())
        assertTrue(context.runDirs.getValue(runCase.id).resolve("trace.jsonl").exists())
        assertTrue(context.runDirs.getValue(runCase.id).resolve("last-polled-torrent-info.json").exists())
        assertTrue(context.runDirs.getValue(runCase.id).resolve("polling-summary.json").exists())
        assertTrue(root.resolve("latest-run.txt").exists())
    }

    @Test
    fun writesKnownUncachedSummaryAndTimelineArtifacts() {
        val root = createTempDirectory(prefix = "spike1-uncached-artifacts-")
        val writer = ArtifactWriter(root)
        val runCase = RunCase(
            id = "uncached-provider-acquisition",
            specLabel = "Known-Uncached Profile",
            executionOrder = 1,
            scopeKind = ScopeKind.EXACT_PATH,
            scopePath = "/Folder/file.iso",
            desiredPaths = listOf("/Folder/file.iso"),
        )
        val context = writer.createInvocation(MatrixPlan(listOf(runCase)), Instant.parse("2026-03-06T13:00:00Z"))

        writer.writeKnownUncachedProgress(
            context = context,
            runCase = runCase,
            selectedFileIds = listOf(7),
            selectedPaths = listOf("/Folder/file.iso"),
            payload = RunArtifactPayload(
                traceEvents = listOf(
                    TraceEvent(
                        timestamp = "2026-03-06T13:00:01Z",
                        stage = "torrent-status",
                        summary = "status=downloading",
                    )
                ),
                lastPolledTorrentInfo = TorrentInfoDto(
                    id = "torrent-uncached",
                    status = "downloading",
                    progress = 27.0,
                    speed = 4096,
                    seeders = 12,
                ),
                pollingSummary = PollingSummary(
                    attempts = 3,
                    elapsedMillis = 30000,
                    lastObservedStatus = "downloading",
                ),
                providerAcquisitionTimeline = listOf(
                    ProviderAcquisitionSample(
                        timestamp = "2026-03-06T13:00:01Z",
                        status = "downloading",
                        progress = 27.0,
                        speed = 4096,
                        seeders = 12,
                    )
                ),
                resultMarkdown = "# Known-Uncached Profile\n\nIN_PROGRESS: still downloading.",
            ),
        )
        writer.writeKnownUncachedSummary(
            context = context,
            summary = KnownUncachedSummary(
                startedAt = "2026-03-06T13:00:00Z",
                finishedAt = "2026-03-06T13:00:31Z",
                status = KnownUncachedStatus.IN_PROGRESS,
                artifactRoot = context.invocationDir.toString(),
                envFile = root.resolve(".env.local").toString(),
                torrentId = "torrent-uncached",
                selectedPath = "/Folder/file.iso",
                selectedFileIds = listOf(7),
                observedStatuses = listOf("downloading"),
                lastObservedStatus = "downloading",
                lastProgress = 27.0,
                lastSpeed = 4096,
                lastSeeders = 12,
                totalSamples = 1,
                sessionPollAttempts = 3,
                message = "Known-uncached provider acquisition is still active.",
            ),
        )

        assertTrue(context.invocationDir.resolve("known-uncached-summary.json").exists())
        assertTrue(context.invocationDir.resolve("known-uncached-summary.md").exists())
        assertTrue(context.runDirs.getValue(runCase.id).resolve("provider-acquisition-timeline.json").exists())
    }
}
