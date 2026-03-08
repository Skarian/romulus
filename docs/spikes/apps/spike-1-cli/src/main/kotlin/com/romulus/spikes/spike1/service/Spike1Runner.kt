package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.config.Spike1Config
import com.romulus.spikes.spike1.config.WorkspacePaths
import com.romulus.spikes.spike1.model.DeterministicFailureRecord
import com.romulus.spikes.spike1.model.KnownUncachedCheckpoint
import com.romulus.spikes.spike1.model.KnownUncachedStatus
import com.romulus.spikes.spike1.model.KnownUncachedSummary
import com.romulus.spikes.spike1.model.LifecycleMarker
import com.romulus.spikes.spike1.model.LinkMappingEntry
import com.romulus.spikes.spike1.model.LinkMappingStatus
import com.romulus.spikes.spike1.model.MatrixPlan
import com.romulus.spikes.spike1.model.MatrixSummary
import com.romulus.spikes.spike1.model.PollingSummary
import com.romulus.spikes.spike1.model.ProviderAcquisitionSample
import com.romulus.spikes.spike1.model.RunCase
import com.romulus.spikes.spike1.model.RunOutcome
import com.romulus.spikes.spike1.model.RunStatus
import com.romulus.spikes.spike1.model.ScopeKind
import com.romulus.spikes.spike1.model.SelectionResolution
import com.romulus.spikes.spike1.model.TorrentFileDto
import com.romulus.spikes.spike1.model.TorrentInfoDto
import com.romulus.spikes.spike1.model.TraceEvent
import com.romulus.spikes.spike1.model.UnrestrictCallRecord
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class Spike1Runner(
    private val client: RealDebridClient,
    private val matrixPlanner: MatrixPlanner,
    private val selectionPlanner: SelectionPlanner,
    private val artifactWriter: ArtifactWriter,
    private val gateEvaluator: GateEvaluator,
    private val downloadVerifier: DownloadVerifier,
) {
    suspend fun run(envFile: Path, config: Spike1Config): Spike1RunReport {
        val startedAt = Instant.now()
        val plan = matrixPlanner.plan(config)
        val context = artifactWriter.createInvocation(plan, startedAt)
        val infoHash = RealDebridClient.extractInfoHash(config.magnet)
            ?: throw Spike1CliException("Could not extract info hash from SPIKE1_MAGNET")
        artifactWriter.writeSanitizedInputs(context, envFile, config, infoHash)

        val runOutcomes = mutableListOf<RunOutcome>()
        var cachedHost: String? = null
        var runCTorrentId: String? = null
        var runBSelectionIds: List<Int> = emptyList()

        val runB = plan.runs.first { it.id == "run-b-add-directory" }
        val runBExecution = executeAddFlow(runB, config)
        cachedHost = runBExecution.cachedHost
        if (runBExecution.outcome.selectedFileIds.isNotEmpty()) {
            runBSelectionIds = runBExecution.outcome.selectedFileIds
        }
        runOutcomes += runBExecution.outcome
        artifactWriter.writeRunArtifacts(context, runB, runBExecution.payload)

        val runA = plan.runs.first { it.id == "run-a-add-root" }
        val runAExecution = executeAddFlow(
            runCase = runA,
            config = config,
            cachedHost = cachedHost,
            expectDifferentSelection = runBSelectionIds,
        )
        cachedHost = runAExecution.cachedHost ?: cachedHost
        runOutcomes += runAExecution.outcome
        artifactWriter.writeRunArtifacts(context, runA, runAExecution.payload)

        val runC = plan.runs.first { it.id == "run-c-add-exact-zip" }
        val runCExecution = executeAddFlow(
            runCase = runC,
            config = config,
            cachedHost = cachedHost,
        )
        cachedHost = runCExecution.cachedHost ?: cachedHost
        runCTorrentId = runCExecution.torrentId
        runOutcomes += runCExecution.outcome
        artifactWriter.writeRunArtifacts(context, runC, runCExecution.payload)

        val runD = plan.runs.first { it.id == "run-d-selected-only-download" }
        val runDExecution = executeReuseFlow(
            runCase = runD,
            torrentId = runCTorrentId,
            dependencyLabel = "Run C",
            context = context,
        )
        runOutcomes += runDExecution.outcome
        artifactWriter.writeRunArtifacts(context, runD, runDExecution.payload)

        val runE = plan.runs.first { it.id == "run-e-deterministic-failure" }
        val runEExecution = executeDeterministicFailure(runE, cachedHost)
        runOutcomes += runEExecution.outcome
        artifactWriter.writeRunArtifacts(context, runE, runEExecution.payload)

        val summary = gateEvaluator.evaluate(
            startedAt = startedAt.toString(),
            finishedAt = Instant.now().toString(),
            artifactRoot = context.invocationDir.toString(),
            envFile = envFile.toString(),
            runOrder = plan.runs.map { it.id },
            runOutcomes = runOutcomes,
        )
        artifactWriter.writeMatrixSummary(context, summary)
        return Spike1RunReport(summary, context.invocationDir)
    }

    suspend fun runKnownUncached(
        envFile: Path,
        config: Spike1Config,
        workspacePaths: WorkspacePaths,
    ): KnownUncachedRunReport {
        val input = config.requireKnownUncached()
        val infoHash = RealDebridClient.extractInfoHash(input.magnet)
            ?: throw Spike1CliException("Could not extract info hash from SPIKE1_UNCACHED_MAGNET")
        val runCase = RunCase(
            id = knownUncachedRunId,
            specLabel = "Known-Uncached Profile",
            executionOrder = 1,
            scopeKind = ScopeKind.EXACT_PATH,
            scopePath = input.selectedPath,
            desiredPaths = listOf(input.selectedPath),
        )
        val checkpoint = loadKnownUncachedCheckpoint(workspacePaths.knownUncachedStateFile)
        return if (checkpoint == null) {
            startKnownUncached(envFile, config, input, infoHash, workspacePaths, runCase)
        } else {
            require(checkpoint.magnetHash.equals(infoHash, ignoreCase = true)) {
                "Existing known-uncached checkpoint targets a different magnet hash. Run just spike-1-delete or remove the checkpoint before starting a new known-uncached fixture."
            }
            require(checkpoint.selectedPath == input.selectedPath) {
                "Existing known-uncached checkpoint targets ${checkpoint.selectedPath}, not ${input.selectedPath}. Run just spike-1-delete or remove the checkpoint before starting a new known-uncached fixture."
            }
            resumeKnownUncached(envFile, config, input, checkpoint, workspacePaths, runCase)
        }
    }

    private suspend fun startKnownUncached(
        envFile: Path,
        config: Spike1Config,
        input: Spike1Config.KnownUncachedInput,
        infoHash: String,
        workspacePaths: WorkspacePaths,
        runCase: RunCase,
    ): KnownUncachedRunReport {
        val startedAt = Instant.now()
        val context = artifactWriter.createInvocation(MatrixPlan(listOf(runCase)), startedAt)
        artifactWriter.writeSanitizedInputs(context, envFile, config, infoHash)

        val traceEvents = mutableListOf<TraceEvent>()
        val timeline = mutableListOf<ProviderAcquisitionSample>()
        val markers = mutableListOf(
            LifecycleMarker(
                timestamp = Instant.now().toString(),
                event = "STARTED",
                note = "Started known-uncached provider-acquisition profile",
            ),
        )
        val availableHosts = client.getAvailableHosts(traceEvents)
        require(availableHosts.isNotEmpty()) { "No available hosts were returned by Real-Debrid" }
        val host = availableHosts.first().host
        val addedMagnet = client.addMagnet(input.magnet, host, traceEvents)
        val initialInfo = getPostAddInfo(addedMagnet.id, traceEvents)
        val selectionResolution = selectionPlanner.resolve(runCase, initialInfo.files)
        client.selectFiles(addedMagnet.id, selectionResolution.payload, traceEvents)
        markers += LifecycleMarker(
            timestamp = Instant.now().toString(),
            event = "SELECTION_SUBMITTED",
            note = "Submitted selectFiles for known-uncached profile",
        )

        val checkpoint = KnownUncachedCheckpoint(
            invocationDir = context.invocationDir.toString(),
            torrentId = addedMagnet.id,
            magnetHash = infoHash,
            selectedPath = input.selectedPath,
            selectedFileIds = selectionResolution.resolvedFiles.map { it.id },
            selectionPayload = selectionResolution.payload,
            startedAt = startedAt.toString(),
            lastUpdatedAt = Instant.now().toString(),
            host = host,
        )
        writeKnownUncachedCheckpoint(workspacePaths.knownUncachedStateFile, checkpoint)

        val initialPayload = RunArtifactPayload(
            traceEvents = traceEvents,
            providerFilesBeforeSelection = initialInfo.files,
            selectionResolution = selectionResolution,
            availableHosts = availableHosts,
            addMagnet = addedMagnet,
            lifecycleMarkers = markers,
            knownUncachedCheckpoint = checkpoint,
            resultMarkdown = buildKnownUncachedResultMarkdown(
                status = KnownUncachedStatus.IN_PROGRESS,
                message = "Known-uncached selection submitted; provider acquisition polling has started.",
            ),
        )
        artifactWriter.writeRunArtifacts(context, runCase, initialPayload)

        return continueKnownUncached(
            envFile = envFile,
            workspacePaths = workspacePaths,
            context = context,
            runCase = runCase,
            checkpoint = checkpoint,
            selectionResolution = selectionResolution,
            initialTraceEvents = traceEvents,
            timeline = timeline,
            lifecycleMarkers = markers,
        )
    }

    private suspend fun resumeKnownUncached(
        envFile: Path,
        config: Spike1Config,
        input: Spike1Config.KnownUncachedInput,
        checkpoint: KnownUncachedCheckpoint,
        workspacePaths: WorkspacePaths,
        runCase: RunCase,
    ): KnownUncachedRunReport {
        val invocationDir = Path.of(checkpoint.invocationDir)
        val runDir = invocationDir.resolve(runCase.id)
        runDir.createDirectories()
        val context = InvocationContext(invocationDir = invocationDir, runDirs = mapOf(runCase.id to runDir))
        val traceEvents = loadTraceEvents(runDir.resolve("trace.jsonl")).toMutableList()
        val timeline = loadProviderTimeline(runDir.resolve("provider-acquisition-timeline.json")).toMutableList()
        val lifecycleMarkers = loadLifecycleMarkers(runDir.resolve("lifecycle-markers.json")).toMutableList()
        if (lifecycleMarkers.none { it.event == "STARTED" }) {
            lifecycleMarkers += LifecycleMarker(
                timestamp = Instant.now().toString(),
                event = "STARTED",
                note = "Recovered started marker from checkpoint state",
            )
        }
        lifecycleMarkers += LifecycleMarker(
            timestamp = Instant.now().toString(),
            event = "RESUMED",
            note = "Resumed known-uncached provider-acquisition polling",
        )
        val infoHash = RealDebridClient.extractInfoHash(input.magnet)
            ?: throw Spike1CliException("Could not extract info hash from SPIKE1_UNCACHED_MAGNET")
        artifactWriter.writeSanitizedInputs(context, envFile, config, infoHash)

        val selectionResolution = SelectionResolution(
            scopeKind = runCase.scopeKind,
            scopePath = runCase.scopePath,
            desiredPaths = listOf(input.selectedPath),
            candidateFiles = emptyList(),
            resolvedFiles = checkpoint.selectedFileIds.map { id -> TorrentFileDto(id = id, path = input.selectedPath) },
            payload = checkpoint.selectionPayload,
            usesAllLiteral = false,
        )

        return continueKnownUncached(
            envFile = envFile,
            workspacePaths = workspacePaths,
            context = context,
            runCase = runCase,
            checkpoint = checkpoint,
            selectionResolution = selectionResolution,
            initialTraceEvents = traceEvents,
            timeline = timeline,
            lifecycleMarkers = lifecycleMarkers,
        )
    }

    private suspend fun continueKnownUncached(
        envFile: Path,
        workspacePaths: WorkspacePaths,
        context: InvocationContext,
        runCase: RunCase,
        checkpoint: KnownUncachedCheckpoint,
        selectionResolution: SelectionResolution,
        initialTraceEvents: MutableList<TraceEvent>,
        timeline: MutableList<ProviderAcquisitionSample>,
        lifecycleMarkers: MutableList<LifecycleMarker>,
    ): KnownUncachedRunReport {
        val traceEvents = initialTraceEvents
        val startedAt = Instant.parse(checkpoint.startedAt)
        val existingCheckpoint = if (workspacePaths.knownUncachedStateFile.exists()) checkpoint else null
        val initialInfo = existingCheckpoint?.let {
            runCatching { safeGetKnownUncachedInfo(it.torrentId, traceEvents) }.getOrElse { throwable ->
                val apiException = throwable as? RealDebridApiException
                if (apiException?.httpStatus == 404) {
                    clearKnownUncachedCheckpoint(workspacePaths.knownUncachedStateFile)
                    throw Spike1CliException("Known-uncached checkpoint torrent ${it.torrentId} no longer exists in Real-Debrid")
                }
                throw throwable
            }
        }

        val statusResult = waitForKnownUncachedStatus(
            torrentId = checkpoint.torrentId,
            traceEvents = traceEvents,
            existingTimeline = timeline,
            existingInfo = initialInfo,
        )

        val lastInfo = statusResult.lastInfo ?: initialInfo
        val linkObservation = lastInfo?.let(::observeLinkLand)
        val updatedCheckpoint = checkpoint.copy(
            lastUpdatedAt = Instant.now().toString(),
            firstReturnedLinksAt = statusResult.firstReturnedLinksAt ?: checkpoint.firstReturnedLinksAt,
        )
        val payload = RunArtifactPayload(
            traceEvents = traceEvents,
            providerFilesAfterSelection = lastInfo?.files.orEmpty(),
            lastPolledTorrentInfo = lastInfo,
            pollingSummary = statusResult.pollingSummary,
            providerSelectedFilesAfterSelection = linkObservation?.selectedProviderFiles.orEmpty(),
            returnedRestrictedLinks = lastInfo?.links.orEmpty(),
            linkMappingStatus = linkObservation?.linkMappingStatus,
            linkMappingNote = linkObservation?.linkMappingNote,
            linkMappings = linkObservation?.linkMappings.orEmpty(),
            providerAcquisitionTimeline = statusResult.timeline,
            lifecycleMarkers = lifecycleMarkers,
            knownUncachedCheckpoint = updatedCheckpoint,
            resultMarkdown = buildKnownUncachedResultMarkdown(
                status = statusResult.status,
                message = statusResult.message,
            ),
        )

        if (statusResult.status == KnownUncachedStatus.PASS && lastInfo != null) {
            val unrestrictCalls = lastInfo.links.map { restrictedLink ->
                val unrestricted = client.unrestrictLink(restrictedLink, traceEvents)
                UnrestrictCallRecord(
                    restrictedLink = restrictedLink,
                    responseFilename = unrestricted.filename,
                    downloadUrl = unrestricted.downloadUrl,
                    fileSize = unrestricted.fileSize,
                )
            }
            val successPayload = payload.copy(unrestrictCalls = unrestrictCalls)
            lifecycleMarkers += LifecycleMarker(
                timestamp = Instant.now().toString(),
                event = "LINKS_READY",
                note = "Returned links became available and were unrestricted",
            )
            val successSummary = KnownUncachedSummary(
                startedAt = checkpoint.startedAt,
                finishedAt = Instant.now().toString(),
                status = KnownUncachedStatus.PASS,
                artifactRoot = context.invocationDir.toString(),
                envFile = envFile.toString(),
                torrentId = checkpoint.torrentId,
                selectedPath = checkpoint.selectedPath,
                selectedFileIds = checkpoint.selectedFileIds,
                observedStatuses = statusResult.timeline.mapNotNull { it.status }.distinct(),
                lastObservedStatus = lastInfo.status,
                lastProgress = lastInfo.progress,
                lastSpeed = lastInfo.speed,
                lastSeeders = lastInfo.seeders,
                firstReturnedLinksAt = statusResult.firstReturnedLinksAt ?: checkpoint.firstReturnedLinksAt,
                totalSamples = statusResult.timeline.size,
                sessionPollAttempts = statusResult.pollingSummary.attempts,
                message = "Known-uncached provider acquisition reached returned links and unrestrict completed.",
            )
            artifactWriter.writeKnownUncachedProgress(context, runCase, checkpoint.selectedFileIds, listOf(checkpoint.selectedPath), successPayload)
            artifactWriter.writeKnownUncachedSummary(context, successSummary)
            clearKnownUncachedCheckpoint(workspacePaths.knownUncachedStateFile)
            return KnownUncachedRunReport(successSummary, context.invocationDir)
        }

        lifecycleMarkers += LifecycleMarker(
            timestamp = Instant.now().toString(),
            event = when (statusResult.status) {
                KnownUncachedStatus.IN_PROGRESS -> "SESSION_BUDGET_REACHED"
                KnownUncachedStatus.TERMINAL_FAILURE -> "TERMINAL_FAILURE"
                KnownUncachedStatus.INCOMPLETE -> "INCOMPLETE"
                KnownUncachedStatus.PASS -> "LINKS_READY"
            },
            note = statusResult.message,
        )

        val finalCheckpoint = updatedCheckpoint.copy(lastUpdatedAt = Instant.now().toString())
        val finalPayload = payload.copy(
            lifecycleMarkers = lifecycleMarkers,
            knownUncachedCheckpoint = finalCheckpoint,
            resultMarkdown = buildKnownUncachedResultMarkdown(
                status = statusResult.status,
                message = statusResult.message,
            ),
        )
        val summary = KnownUncachedSummary(
            startedAt = checkpoint.startedAt,
            finishedAt = Instant.now().toString(),
            status = statusResult.status,
            artifactRoot = context.invocationDir.toString(),
            envFile = envFile.toString(),
            torrentId = checkpoint.torrentId,
            selectedPath = checkpoint.selectedPath,
            selectedFileIds = checkpoint.selectedFileIds,
            observedStatuses = statusResult.timeline.mapNotNull { it.status }.distinct(),
            lastObservedStatus = lastInfo?.status,
            lastProgress = lastInfo?.progress,
            lastSpeed = lastInfo?.speed,
            lastSeeders = lastInfo?.seeders,
            firstReturnedLinksAt = statusResult.firstReturnedLinksAt ?: checkpoint.firstReturnedLinksAt,
            totalSamples = statusResult.timeline.size,
            sessionPollAttempts = statusResult.pollingSummary.attempts,
            message = statusResult.message,
        )
        artifactWriter.writeKnownUncachedProgress(context, runCase, checkpoint.selectedFileIds, listOf(checkpoint.selectedPath), finalPayload)
        artifactWriter.writeKnownUncachedSummary(context, summary)

        when (statusResult.status) {
            KnownUncachedStatus.IN_PROGRESS, KnownUncachedStatus.INCOMPLETE -> {
                writeKnownUncachedCheckpoint(workspacePaths.knownUncachedStateFile, finalCheckpoint)
            }
            KnownUncachedStatus.TERMINAL_FAILURE, KnownUncachedStatus.PASS -> {
                clearKnownUncachedCheckpoint(workspacePaths.knownUncachedStateFile)
            }
        }

        return KnownUncachedRunReport(summary, context.invocationDir)
    }

    private suspend fun waitForKnownUncachedStatus(
        torrentId: String,
        traceEvents: MutableList<TraceEvent>,
        existingTimeline: MutableList<ProviderAcquisitionSample>,
        existingInfo: TorrentInfoDto? = null,
    ): KnownUncachedStatusResult {
        val timeline = existingTimeline.toMutableList()
        var lastInfo = existingInfo
        var firstReturnedLinksAt: String? = existingInfo?.links?.takeIf { it.isNotEmpty() }?.let { Instant.now().toString() }
        val startedAtMillis = System.currentTimeMillis()
        var attempts = 0

        if (existingInfo != null) {
            timeline += toProviderAcquisitionSample(existingInfo)
            if (existingInfo.status in terminalStatuses) {
                return KnownUncachedStatusResult(
                    status = KnownUncachedStatus.TERMINAL_FAILURE,
                    message = "Provider reached terminal state ${existingInfo.status}.",
                    timeline = timeline,
                    lastInfo = existingInfo,
                    firstReturnedLinksAt = firstReturnedLinksAt,
                    pollingSummary = PollingSummary(
                        attempts = 0,
                        elapsedMillis = 0,
                        lastObservedStatus = existingInfo.status,
                        firstNonPreselectionStatus = existingInfo.status,
                        firstNonPreselectionElapsedMillis = 0,
                        firstReturnedLinksElapsedMillis = if (existingInfo.links.isNotEmpty()) 0 else null,
                    ),
                )
            }
            if (existingInfo.links.isNotEmpty()) {
                return KnownUncachedStatusResult(
                    status = KnownUncachedStatus.PASS,
                    message = "Returned links were already ready when the profile resumed.",
                    timeline = timeline,
                    lastInfo = existingInfo,
                    firstReturnedLinksAt = firstReturnedLinksAt,
                    pollingSummary = PollingSummary(
                        attempts = 0,
                        elapsedMillis = 0,
                        lastObservedStatus = existingInfo.status,
                        firstNonPreselectionStatus = existingInfo.status,
                        firstNonPreselectionElapsedMillis = 0,
                        firstReturnedLinksElapsedMillis = 0,
                    ),
                )
            }
        }

        repeat(uncachedSessionPollAttempts) { attempt ->
            if (attempt > 0 || existingInfo == null) {
                delay(uncachedSessionPollDelayMs)
            }
            val info = safeGetKnownUncachedInfo(torrentId, traceEvents) ?: return@repeat
            lastInfo = info
            attempts = attempt + 1
            recordStatus(info.status, traceEvents)
            timeline += toProviderAcquisitionSample(info)
            if (info.links.isNotEmpty()) {
                val returnedAt = Instant.now().toString()
                val elapsedMillis = System.currentTimeMillis() - startedAtMillis
                return KnownUncachedStatusResult(
                    status = KnownUncachedStatus.PASS,
                    message = "Returned links became available for the known-uncached profile.",
                    timeline = timeline,
                    lastInfo = info,
                    firstReturnedLinksAt = firstReturnedLinksAt ?: returnedAt,
                    pollingSummary = PollingSummary(
                        attempts = attempts,
                        elapsedMillis = elapsedMillis,
                        lastObservedStatus = info.status,
                        firstNonPreselectionStatus = info.status,
                        firstNonPreselectionElapsedMillis = if (attempts > 0) 0 else null,
                        firstReturnedLinksElapsedMillis = elapsedMillis,
                    ),
                )
            }
            if (info.status in terminalStatuses) {
                val elapsedMillis = System.currentTimeMillis() - startedAtMillis
                return KnownUncachedStatusResult(
                    status = KnownUncachedStatus.TERMINAL_FAILURE,
                    message = "Provider reached terminal state ${info.status}.",
                    timeline = timeline,
                    lastInfo = info,
                    firstReturnedLinksAt = firstReturnedLinksAt,
                    pollingSummary = PollingSummary(
                        attempts = attempts,
                        elapsedMillis = elapsedMillis,
                        lastObservedStatus = info.status,
                        firstNonPreselectionStatus = info.status,
                        firstNonPreselectionElapsedMillis = if (attempts > 0) 0 else null,
                        firstReturnedLinksElapsedMillis = null,
                    ),
                )
            }
        }

        val elapsedMillis = System.currentTimeMillis() - startedAtMillis
        val totalElapsed = Duration.between(Instant.parse(checkpointTimeFromTimeline(timeline) ?: Instant.now().toString()), Instant.now())
        val status = if (totalElapsed.toHours() >= knownUncachedMaxHours) KnownUncachedStatus.TERMINAL_FAILURE else KnownUncachedStatus.IN_PROGRESS
        val message = if (status == KnownUncachedStatus.TERMINAL_FAILURE) {
            "Known-uncached provider acquisition exceeded the 72-hour user-observed upper bound without returned links."
        } else {
            "Known-uncached provider acquisition is still active; rerun the profile command to resume polling."
        }
        return KnownUncachedStatusResult(
            status = status,
            message = message,
            timeline = timeline,
            lastInfo = lastInfo,
            firstReturnedLinksAt = firstReturnedLinksAt,
            pollingSummary = PollingSummary(
                attempts = attempts,
                elapsedMillis = elapsedMillis,
                lastObservedStatus = lastInfo?.status,
                firstNonPreselectionStatus = timeline.firstOrNull { !it.status.isNullOrBlank() }?.status,
                firstNonPreselectionElapsedMillis = null,
                firstReturnedLinksElapsedMillis = null,
            ),
        )
    }

    private suspend fun safeGetKnownUncachedInfo(
        torrentId: String,
        traceEvents: MutableList<TraceEvent>,
    ): TorrentInfoDto? {
        return try {
            client.getTorrentInfo(torrentId, traceEvents)
        } catch (throwable: Throwable) {
            val apiException = throwable as? RealDebridApiException
            if (apiException?.httpStatus == 404) {
                throw throwable
            }
            if (apiException?.httpStatus in transientHttpCodes || apiException?.providerCode in transientProviderCodes || isTransientJsonFailure(throwable)) {
                traceEvents += TraceEvent(
                    timestamp = Instant.now().toString(),
                    stage = "poll-transient-error",
                    method = "GET",
                    endpoint = "/torrents/info/$torrentId",
                    statusCode = apiException?.httpStatus,
                    providerCode = apiException?.providerCode,
                    summary = throwable.message ?: "Transient poll failure",
                )
                return null
            }
            throw throwable
        }
    }

    private fun checkpointTimeFromTimeline(timeline: List<ProviderAcquisitionSample>): String? {
        return timeline.firstOrNull()?.timestamp
    }

    private fun toProviderAcquisitionSample(info: TorrentInfoDto): ProviderAcquisitionSample {
        return ProviderAcquisitionSample(
            timestamp = Instant.now().toString(),
            status = info.status,
            progress = info.progress,
            speed = info.speed,
            seeders = info.seeders,
            ended = info.ended,
            linkCount = info.links.size,
        )
    }

    private fun writeKnownUncachedCheckpoint(path: Path, checkpoint: KnownUncachedCheckpoint) {
        path.parent?.createDirectories()
        path.writeText(checkpointJson.encodeToString(checkpoint))
    }

    private fun clearKnownUncachedCheckpoint(path: Path) {
        Files.deleteIfExists(path)
    }

    private fun loadKnownUncachedCheckpoint(path: Path): KnownUncachedCheckpoint? {
        if (!path.exists()) {
            return null
        }
        return runCatching { checkpointJson.decodeFromString<KnownUncachedCheckpoint>(path.readText()) }.getOrNull()
    }

    private fun loadTraceEvents(path: Path): List<TraceEvent> {
        if (!path.exists()) {
            return emptyList()
        }
        return runCatching {
            Files.readAllLines(path)
                .filter { it.isNotBlank() }
                .map { checkpointJson.decodeFromString<TraceEvent>(it) }
        }.getOrDefault(emptyList())
    }

    private fun loadProviderTimeline(path: Path): List<ProviderAcquisitionSample> {
        if (!path.exists()) {
            return emptyList()
        }
        return runCatching { checkpointJson.decodeFromString<List<ProviderAcquisitionSample>>(path.readText()) }.getOrDefault(emptyList())
    }

    private fun loadLifecycleMarkers(path: Path): List<LifecycleMarker> {
        if (!path.exists()) {
            return emptyList()
        }
        return runCatching { checkpointJson.decodeFromString<List<LifecycleMarker>>(path.readText()) }.getOrDefault(emptyList())
    }

    private fun isTransientJsonFailure(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty()
        return message.contains("Unexpected JSON token") ||
            message.contains("Expected end of the object") ||
            message.contains("EOF")
    }

    private suspend fun executeAddFlow(
        runCase: RunCase,
        config: Spike1Config,
        cachedHost: String? = null,
        expectDifferentSelection: List<Int>? = null,
    ): RunExecution {
        val traceEvents = mutableListOf<TraceEvent>()
        var payload = RunArtifactPayload(
            traceEvents = traceEvents,
            resultMarkdown = "# ${runCase.specLabel}\n",
        )
        return try {
            val availableHosts = if (cachedHost == null) client.getAvailableHosts(traceEvents) else null
            val addHost = if (cachedHost == null) {
                require(availableHosts?.isNotEmpty() == true) { "No available hosts were returned by Real-Debrid" }
                availableHosts.first().host
            } else {
                traceEvents += TraceEvent(
                    timestamp = Instant.now().toString(),
                    stage = "host-policy",
                    summary = "reused cached host from earlier successful add flow",
                )
                cachedHost
            }
            val addedMagnet = client.addMagnet(config.magnet, addHost, traceEvents)
            val initialInfo = getPostAddInfo(addedMagnet.id, traceEvents)
            val selectionResolution = selectionPlanner.resolve(runCase, initialInfo.files)
            if (expectDifferentSelection != null && selectionResolution.resolvedFiles.map { it.id } == expectDifferentSelection) {
                throw Spike1CliException("${runCase.specLabel} must prove a different selected ID set than Run B")
            }
            payload = payload.copy(
                availableHosts = availableHosts,
                addMagnet = addedMagnet,
                providerFilesBeforeSelection = initialInfo.files,
                selectionResolution = selectionResolution,
            )
            client.selectFiles(addedMagnet.id, selectionResolution.payload, traceEvents)
            val readyInfo = waitForLinksReady(addedMagnet.id, traceEvents)
            val linkObservation = observeLinkLand(readyInfo.info)
            payload = payload.copy(
                providerFilesAfterSelection = readyInfo.info.files,
                lastPolledTorrentInfo = readyInfo.info,
                pollingSummary = readyInfo.pollingSummary,
                providerSelectedFilesAfterSelection = linkObservation.selectedProviderFiles,
                returnedRestrictedLinks = readyInfo.info.links,
                linkMappingStatus = linkObservation.linkMappingStatus,
                linkMappingNote = linkObservation.linkMappingNote,
                linkMappings = linkObservation.linkMappings,
            )
            validateProviderSelection(selectionResolution, linkObservation.selectedProviderFiles)
            val outcome = finalizeRunOutcome(
                runCase = runCase,
                torrentId = addedMagnet.id,
                providerFilesBeforeSelection = initialInfo.files,
                selectionResolution = selectionResolution,
                readyInfo = readyInfo.info,
                pollingSummary = readyInfo.pollingSummary,
                traceEvents = traceEvents,
                reusedPriorTorrent = false,
                context = null,
                payload = payload,
                linkObservation = linkObservation,
            )
            RunExecution(
                outcome = outcome.outcome.copy(message = successMessage(runCase)),
                payload = outcome.payload,
                cachedHost = addHost,
                torrentId = addedMagnet.id,
            )
        } catch (throwable: Throwable) {
            failureExecution(runCase, traceEvents, throwable, payload = payload)
        }
    }

    private suspend fun executeReuseFlow(
        runCase: RunCase,
        torrentId: String?,
        dependencyLabel: String,
        context: InvocationContext? = null,
    ): RunExecution {
        val traceEvents = mutableListOf<TraceEvent>()
        if (torrentId.isNullOrBlank()) {
            return skippedExecution(runCase, traceEvents, "SKIPPED_DEPENDENCY: torrentId from $dependencyLabel is unavailable.")
        }
        var payload = RunArtifactPayload(
            traceEvents = traceEvents,
            resultMarkdown = "# ${runCase.specLabel}\n",
        )
        return try {
            val initialInfo = client.getTorrentInfo(torrentId, traceEvents)
            val selectionResolution = selectionPlanner.resolve(runCase, initialInfo.files)
            payload = payload.copy(
                providerFilesBeforeSelection = initialInfo.files,
                selectionResolution = selectionResolution,
            )
            client.selectFiles(torrentId, selectionResolution.payload, traceEvents)
            val readyInfo = waitForLinksReady(torrentId, traceEvents)
            val linkObservation = observeLinkLand(readyInfo.info)
            payload = payload.copy(
                providerFilesAfterSelection = readyInfo.info.files,
                lastPolledTorrentInfo = readyInfo.info,
                pollingSummary = readyInfo.pollingSummary,
                providerSelectedFilesAfterSelection = linkObservation.selectedProviderFiles,
                returnedRestrictedLinks = readyInfo.info.links,
                linkMappingStatus = linkObservation.linkMappingStatus,
                linkMappingNote = linkObservation.linkMappingNote,
                linkMappings = linkObservation.linkMappings,
            )
            validateProviderSelection(selectionResolution, linkObservation.selectedProviderFiles)
            val finalized = finalizeRunOutcome(
                runCase = runCase,
                torrentId = torrentId,
                providerFilesBeforeSelection = initialInfo.files,
                selectionResolution = selectionResolution,
                readyInfo = readyInfo.info,
                pollingSummary = readyInfo.pollingSummary,
                traceEvents = traceEvents,
                reusedPriorTorrent = true,
                context = context,
                payload = payload,
                linkObservation = linkObservation,
            )
            RunExecution(
                outcome = finalized.outcome.copy(message = successMessage(runCase)),
                payload = finalized.payload,
                cachedHost = null,
                torrentId = torrentId,
            )
        } catch (throwable: Throwable) {
            failureExecution(
                runCase,
                traceEvents,
                throwable,
                payload = payload,
                torrentId = torrentId,
                reusedTorrent = true,
            )
        }
    }

    private suspend fun executeDeterministicFailure(runCase: RunCase, cachedHost: String?): RunExecution {
        val traceEvents = mutableListOf<TraceEvent>()
        if (cachedHost.isNullOrBlank()) {
            return RunExecution(
                outcome = RunOutcome(
                    runId = runCase.id,
                    specLabel = runCase.specLabel,
                    executionOrder = runCase.executionOrder,
                    status = RunStatus.SKIPPED_DEPENDENCY,
                    message = "SKIPPED_DEPENDENCY: cached host from Run B is unavailable.",
                ),
                payload = RunArtifactPayload(
                    traceEvents = traceEvents,
                    resultMarkdown = "# ${runCase.specLabel}\n\nSKIPPED_DEPENDENCY: cached host from Run B is unavailable.",
                ),
                cachedHost = null,
                torrentId = null,
            )
        }

        val invalidMagnet = "magnet:?xt=urn:btih:INVALIDSPIKE1MAGNET&dn=spike1-invalid"
        return try {
            client.addMagnet(invalidMagnet, cachedHost, traceEvents)
            RunExecution(
                outcome = RunOutcome(
                    runId = runCase.id,
                    specLabel = runCase.specLabel,
                    executionOrder = runCase.executionOrder,
                    status = RunStatus.FAIL,
                    message = "Invalid magnet unexpectedly succeeded.",
                ),
                payload = RunArtifactPayload(
                    traceEvents = traceEvents,
                    deterministicFailureRecord = DeterministicFailureRecord(
                        invalidMagnet = invalidMagnet,
                        cachedHost = cachedHost,
                        httpStatus = 200,
                        providerMessage = "Invalid magnet unexpectedly succeeded.",
                    ),
                    resultMarkdown = "# ${runCase.specLabel}\n\nFAIL: invalid magnet unexpectedly succeeded.",
                ),
                cachedHost = cachedHost,
                torrentId = null,
            )
        } catch (throwable: Throwable) {
            val exception = throwable as? RealDebridApiException
            val expected = exception != null && isExpectedInvalidMagnet(exception)
            val status = if (expected) RunStatus.EXPECTED_FAILURE else RunStatus.FAIL
            val message = if (expected) {
                "Captured deterministic invalid-magnet failure."
            } else {
                throwable.message ?: "Deterministic failure run failed unexpectedly."
            }
            RunExecution(
                outcome = RunOutcome(
                    runId = runCase.id,
                    specLabel = runCase.specLabel,
                    executionOrder = runCase.executionOrder,
                    status = status,
                    message = message,
                    httpStatus = exception?.httpStatus,
                    providerCode = exception?.providerCode,
                ),
                payload = RunArtifactPayload(
                    traceEvents = traceEvents,
                    deterministicFailureRecord = DeterministicFailureRecord(
                        invalidMagnet = invalidMagnet,
                        cachedHost = cachedHost,
                        httpStatus = exception?.httpStatus ?: 0,
                        providerCode = exception?.providerCode,
                        providerMessage = exception?.providerMessage ?: throwable.message,
                    ),
                    resultMarkdown = "# ${runCase.specLabel}\n\n${status}: $message",
                ),
                cachedHost = cachedHost,
                torrentId = null,
            )
        }
    }

    private suspend fun finalizeRunOutcome(
        runCase: RunCase,
        torrentId: String,
        providerFilesBeforeSelection: List<TorrentFileDto>,
        selectionResolution: SelectionResolution,
        readyInfo: TorrentInfoDto,
        pollingSummary: PollingSummary,
        traceEvents: MutableList<TraceEvent>,
        reusedPriorTorrent: Boolean,
        context: InvocationContext?,
        payload: RunArtifactPayload,
        linkObservation: LinkObservation,
    ): FinalizedRun {
        if (runCase.downloadsSelectedFiles && readyInfo.links.isEmpty()) {
            throw Spike1CliException("Selected-only download proof requires at least one returned link after selection")
        }
        val unrestrictCalls = readyInfo.links.map { restrictedLink ->
            val unrestricted = client.unrestrictLink(restrictedLink, traceEvents)
            UnrestrictCallRecord(
                restrictedLink = restrictedLink,
                responseFilename = unrestricted.filename,
                downloadUrl = unrestricted.downloadUrl,
                fileSize = unrestricted.fileSize,
            )
        }

        val downloadManifest = if (runCase.downloadsSelectedFiles) {
            require(context != null) { "Run D requires invocation context for download output" }
            val outputDirectory = context.runDirs.getValue(runCase.id).resolve("downloads")
            downloadVerifier.downloadAll(unrestrictCalls, outputDirectory)
        } else {
            null
        }

        val observedStatuses = traceEvents.filter { it.stage == "torrent-status" }.map { it.summary.removePrefix("status=") }
        return FinalizedRun(
            outcome = RunOutcome(
                runId = runCase.id,
                specLabel = runCase.specLabel,
                executionOrder = runCase.executionOrder,
                status = RunStatus.PASS,
                message = successMessage(runCase),
                torrentId = torrentId,
                selectedFileIds = selectionResolution.resolvedFiles.map { it.id },
                selectedPaths = selectionResolution.resolvedFiles.map { it.path },
                providerSelectedFileIdsAfterSelection = linkObservation.selectedProviderFiles.map { it.id },
                providerSelectedPathsAfterSelection = linkObservation.selectedProviderFiles.map { it.path },
                candidateFileCount = selectionResolution.candidateFiles.size,
                selectionPayload = selectionResolution.payload,
                selectionUsedAllLiteral = selectionResolution.usesAllLiteral,
                observedStatuses = observedStatuses,
                linkCount = readyInfo.links.size,
                unrestrictCallCount = unrestrictCalls.size,
                downloadCount = downloadManifest?.size ?: 0,
                reusedPriorTorrent = reusedPriorTorrent,
                mismatchedLinkMapping = linkObservation.linkMappingStatus == LinkMappingStatus.COUNT_MISMATCH,
                linkMappingStatus = linkObservation.linkMappingStatus,
                transientFailureObserved = false,
            ),
            payload = payload.copy(
                providerFilesBeforeSelection = providerFilesBeforeSelection,
                selectionResolution = selectionResolution,
                providerFilesAfterSelection = readyInfo.files,
                lastPolledTorrentInfo = readyInfo,
                pollingSummary = pollingSummary,
                providerSelectedFilesAfterSelection = linkObservation.selectedProviderFiles,
                returnedRestrictedLinks = readyInfo.links,
                linkMappingStatus = linkObservation.linkMappingStatus,
                linkMappingNote = linkObservation.linkMappingNote,
                linkMappings = linkObservation.linkMappings,
                unrestrictCalls = unrestrictCalls,
                downloadManifest = downloadManifest,
                resultMarkdown = buildRunResultMarkdown(runCase, linkObservation.linkMappingNote),
            ),
        )
    }

    private suspend fun getPostAddInfo(torrentId: String, traceEvents: MutableList<TraceEvent>): TorrentInfoDto {
        repeat(postAddInfoAttempts) { attempt ->
            val info = client.getTorrentInfo(torrentId, traceEvents)
            recordStatus(info.status, traceEvents)
            if (info.files.isNotEmpty()) {
                return info
            }
            if (attempt < postAddInfoAttempts - 1) {
                delay(postAddDelayMs)
            }
        }
        throw Spike1CliException("Torrent info never became ready after addMagnet")
    }

    private suspend fun waitForLinksReady(torrentId: String, traceEvents: MutableList<TraceEvent>): LinkReadyResult {
        var lastInfo: TorrentInfoDto? = null
        var firstNonPreselectionStatus: String? = null
        var firstNonPreselectionElapsedMillis: Long? = null
        val startedAtMillis = System.currentTimeMillis()
        repeat(selectionPollAttempts) { attempt ->
            val info = client.getTorrentInfo(torrentId, traceEvents)
            lastInfo = info
            recordStatus(info.status, traceEvents)
            val status = info.status.orEmpty()
            if (status in terminalStatuses) {
                throw Spike1CliException("Torrent reached terminal state $status")
            }
            if (firstNonPreselectionStatus == null && status.isNotBlank() && status !in preselectionStatuses) {
                firstNonPreselectionStatus = status
                firstNonPreselectionElapsedMillis = System.currentTimeMillis() - startedAtMillis
            }
            if (info.links.isNotEmpty()) {
                val elapsedMillis = System.currentTimeMillis() - startedAtMillis
                return LinkReadyResult(
                    info = info,
                    pollingSummary = PollingSummary(
                        attempts = attempt + 1,
                        elapsedMillis = elapsedMillis,
                        lastObservedStatus = status.ifBlank { null },
                        firstNonPreselectionStatus = firstNonPreselectionStatus,
                        firstNonPreselectionElapsedMillis = firstNonPreselectionElapsedMillis,
                        firstReturnedLinksElapsedMillis = elapsedMillis,
                    ),
                )
            }
            delay(selectionPollDelayMs)
        }
        val elapsedMillis = System.currentTimeMillis() - startedAtMillis
        val lastStatus = lastInfo?.status?.takeIf { it.isNotBlank() }
        throw LinkReadyTimeoutException(
            message = buildString {
                append("Torrent never produced links after selection")
                append(" after ${selectionPollAttempts} polls over ${elapsedMillis}ms")
                lastStatus?.let { append("; last status=$it") }
            },
            lastInfo = lastInfo,
            pollingSummary = PollingSummary(
                attempts = selectionPollAttempts,
                elapsedMillis = elapsedMillis,
                lastObservedStatus = lastStatus,
                firstNonPreselectionStatus = firstNonPreselectionStatus,
                firstNonPreselectionElapsedMillis = firstNonPreselectionElapsedMillis,
                firstReturnedLinksElapsedMillis = null,
            ),
        )
    }

    private fun recordStatus(status: String?, traceEvents: MutableList<TraceEvent>) {
        val normalized = status?.takeIf { it.isNotBlank() } ?: return
        val lastStatus = traceEvents.lastOrNull { it.stage == "torrent-status" }?.summary?.removePrefix("status=")
        if (normalized != lastStatus) {
            traceEvents += TraceEvent(
                timestamp = Instant.now().toString(),
                stage = "torrent-status",
                summary = "status=$normalized",
            )
        }
    }

    private fun failureExecution(
        runCase: RunCase,
        traceEvents: MutableList<TraceEvent>,
        throwable: Throwable,
        payload: RunArtifactPayload,
        torrentId: String? = null,
        reusedTorrent: Boolean = false,
    ): RunExecution {
        val apiException = throwable as? RealDebridApiException
        val timeoutException = throwable as? LinkReadyTimeoutException
        val transientObserved = apiException?.httpStatus in transientHttpCodes || apiException?.providerCode in transientProviderCodes
        val timeoutObservation = timeoutException?.lastInfo?.let { observeLinkLand(it) }
        return RunExecution(
            outcome = RunOutcome(
                runId = runCase.id,
                specLabel = runCase.specLabel,
                executionOrder = runCase.executionOrder,
                status = RunStatus.FAIL,
                message = throwable.message ?: "Run failed.",
                torrentId = torrentId,
                reusedPriorTorrent = reusedTorrent,
                httpStatus = apiException?.httpStatus,
                providerCode = apiException?.providerCode,
                mismatchedLinkMapping = throwable.message?.contains("did not match returned links") == true,
                transientFailureObserved = transientObserved,
            ),
            payload = payload.copy(
                providerFilesAfterSelection = timeoutException?.lastInfo?.files ?: payload.providerFilesAfterSelection,
                lastPolledTorrentInfo = timeoutException?.lastInfo ?: payload.lastPolledTorrentInfo,
                pollingSummary = timeoutException?.pollingSummary ?: payload.pollingSummary,
                providerSelectedFilesAfterSelection = timeoutObservation?.selectedProviderFiles ?: payload.providerSelectedFilesAfterSelection,
                returnedRestrictedLinks = timeoutException?.lastInfo?.links ?: payload.returnedRestrictedLinks,
                linkMappingStatus = timeoutObservation?.linkMappingStatus ?: payload.linkMappingStatus,
                linkMappingNote = timeoutObservation?.linkMappingNote ?: payload.linkMappingNote,
                linkMappings = timeoutObservation?.linkMappings ?: payload.linkMappings,
                resultMarkdown = buildFailureMarkdown(runCase, throwable, timeoutException),
            ),
            cachedHost = null,
            torrentId = torrentId,
        )
    }

    private fun skippedExecution(runCase: RunCase, traceEvents: MutableList<TraceEvent>, message: String): RunExecution {
        return RunExecution(
            outcome = RunOutcome(
                runId = runCase.id,
                specLabel = runCase.specLabel,
                executionOrder = runCase.executionOrder,
                status = RunStatus.SKIPPED_DEPENDENCY,
                message = message,
            ),
            payload = RunArtifactPayload(
                traceEvents = traceEvents,
                resultMarkdown = "# ${runCase.specLabel}\n\n$message",
            ),
            cachedHost = null,
            torrentId = null,
        )
    }

    private fun successMessage(runCase: RunCase): String {
        return when (runCase.id) {
            "run-b-add-directory" -> "PASS: add flow, directory scope, and subset selection proved."
            "run-a-add-root" -> "PASS: add-again flow produced a different root selection."
            "run-c-add-exact-zip" -> "PASS: add-again exact path selection proved."
            "run-d-selected-only-download" -> "PASS: reuse of the exact-path torrent and selected-only download proof captured."
            else -> "PASS"
        }
    }

    private fun buildKnownUncachedResultMarkdown(status: KnownUncachedStatus, message: String): String {
        return buildString {
            appendLine("# Known-Uncached Profile")
            appendLine()
            appendLine("$status: $message")
        }.trimEnd()
    }

    private fun isExpectedInvalidMagnet(exception: RealDebridApiException): Boolean {
        return exception.providerCode == 30 || exception.providerMessage?.lowercase()?.contains("magnet") == true && exception.providerMessage.lowercase().contains("invalid")
    }

    private fun validateProviderSelection(
        selectionResolution: SelectionResolution,
        selectedProviderFiles: List<TorrentFileDto>,
    ) {
        val requestedIds = selectionResolution.resolvedFiles.map { it.id }
        val providerSelectedIds = selectedProviderFiles.map { it.id }
        if (requestedIds != providerSelectedIds) {
            throw Spike1CliException(
                "Provider-selected file IDs ${providerSelectedIds.joinToString()} did not match requested file IDs ${requestedIds.joinToString()}"
            )
        }
    }

    private fun observeLinkLand(readyInfo: TorrentInfoDto): LinkObservation {
        val selectedProviderFiles = readyInfo.files.filter { file -> file.selected == 1 }
        val linkMappingStatus = if (readyInfo.links.size == selectedProviderFiles.size) {
            LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER
        } else {
            LinkMappingStatus.COUNT_MISMATCH
        }
        val linkMappings = if (linkMappingStatus == LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER) {
            selectedProviderFiles.zip(readyInfo.links).map { (file, link) ->
                LinkMappingEntry(
                    fileId = file.id,
                    path = file.path,
                    restrictedLink = link,
                )
            }
        } else {
            emptyList()
        }
        val linkMappingNote = when (linkMappingStatus) {
            LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER ->
                "Selected provider file count matched returned link count. Provider-order mapping is observational only."
            LinkMappingStatus.COUNT_MISMATCH ->
                "Selected provider file count (${selectedProviderFiles.size}) differed from returned link count (${readyInfo.links.size}). Downstream handling operates on returned links directly."
        }
        return LinkObservation(
            selectedProviderFiles = selectedProviderFiles,
            linkMappingStatus = linkMappingStatus,
            linkMappingNote = linkMappingNote,
            linkMappings = linkMappings,
        )
    }

    private fun buildRunResultMarkdown(runCase: RunCase, linkMappingNote: String): String {
        return buildString {
            appendLine("# ${runCase.specLabel}")
            appendLine()
            appendLine(successMessage(runCase))
            appendLine()
            appendLine(linkMappingNote)
        }.trimEnd()
    }

    private fun buildFailureMarkdown(
        runCase: RunCase,
        throwable: Throwable,
        timeoutException: LinkReadyTimeoutException?,
    ): String {
        return buildString {
            appendLine("# ${runCase.specLabel}")
            appendLine()
            appendLine("FAIL: ${throwable.message}")
            timeoutException?.let { timeout ->
                appendLine()
                appendLine("- poll_attempts: ${timeout.pollingSummary.attempts}")
                appendLine("- elapsed_ms: ${timeout.pollingSummary.elapsedMillis}")
                appendLine("- last_observed_status: ${timeout.pollingSummary.lastObservedStatus ?: "UNCONFIRMED"}")
                appendLine("- first_non_preselection_status: ${timeout.pollingSummary.firstNonPreselectionStatus ?: "UNCONFIRMED"}")
                appendLine("- first_non_preselection_elapsed_ms: ${timeout.pollingSummary.firstNonPreselectionElapsedMillis ?: "UNCONFIRMED"}")
                appendLine("- first_returned_links_elapsed_ms: ${timeout.pollingSummary.firstReturnedLinksElapsedMillis ?: "UNOBSERVED"}")
            }
        }.trimEnd()
    }

    companion object {
        private const val postAddInfoAttempts = 3
        private const val postAddDelayMs = 750L
        private const val selectionPollAttempts = 120
        private const val selectionPollDelayMs = 2_000L
        private const val uncachedSessionPollAttempts = 12
        private const val uncachedSessionPollDelayMs = 10_000L
        private const val knownUncachedMaxHours = 72L
        private const val knownUncachedRunId = "uncached-provider-acquisition"
        private val preselectionStatuses = setOf("magnet_conversion", "waiting_files_selection")
        private val terminalStatuses = setOf("magnet_error", "error", "virus", "dead")
        private val transientHttpCodes = setOf(429, 502, 503, 504)
        private val transientProviderCodes = setOf(9, 21, 22, 34)
        private val checkpointJson = spikeJson()
    }
}

data class Spike1RunReport(
    val summary: MatrixSummary,
    val invocationDir: Path,
)

data class KnownUncachedRunReport(
    val summary: KnownUncachedSummary,
    val invocationDir: Path,
)

private data class RunExecution(
    val outcome: RunOutcome,
    val payload: RunArtifactPayload,
    val cachedHost: String?,
    val torrentId: String?,
)

private data class FinalizedRun(
    val outcome: RunOutcome,
    val payload: RunArtifactPayload,
)

private data class LinkObservation(
    val selectedProviderFiles: List<TorrentFileDto>,
    val linkMappingStatus: LinkMappingStatus,
    val linkMappingNote: String,
    val linkMappings: List<LinkMappingEntry>,
)

private data class LinkReadyResult(
    val info: TorrentInfoDto,
    val pollingSummary: PollingSummary,
)

private data class KnownUncachedStatusResult(
    val status: KnownUncachedStatus,
    val message: String,
    val timeline: List<ProviderAcquisitionSample>,
    val lastInfo: TorrentInfoDto?,
    val firstReturnedLinksAt: String?,
    val pollingSummary: PollingSummary,
)
