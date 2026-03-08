package com.romulus.spikes.spike4

import android.content.Context
import android.os.Build
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.io.File

class Spike4MatrixRunner(
    private val appContext: Context,
    private val runtimeConfig: Spike4RuntimeConfig,
    private val selectedDeviceSerial: String?,
) {
    private val artifactWriter = Spike4ArtifactWriter(appContext)
    private val extractor = SevenZipArchiveExtractor()

    fun runFullMatrix(): SessionRecord = runBlocking {
        val sessionId = "session-${System.currentTimeMillis()}"
        val startedAtUtc = nowUtc()
        artifactWriter.resetLatestRoot()
        val runtimeInit = extractor.captureRuntimeInit()
        val deviceMetadata = DeviceMetadata(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            product = Build.PRODUCT,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
        )

        val runResults = mutableListOf<Pair<RunStatusRecord, OutputManifest?>>()
        val definitions = listOf(
            Spike4RunDefinition(
                id = "run-a-archive-selection-unarchive-on",
                title = "Run A",
                description = "Archive-selection happy path with unarchive enabled.",
                unarchiveEnabled = true,
                selectedInternalPaths = runtimeConfig.normalizedSelectedInternalPaths,
                expectedOutcome = RunExpectation.SUCCESS,
            ),
            Spike4RunDefinition(
                id = "run-b-archive-selection-unarchive-off",
                title = "Run B",
                description = "Archive-selection happy path with unarchive disabled so selected-only copy remains visible.",
                unarchiveEnabled = false,
                selectedInternalPaths = runtimeConfig.normalizedSelectedInternalPaths,
                expectedOutcome = RunExpectation.SUCCESS,
            ),
            Spike4RunDefinition(
                id = "run-c-selection-failure",
                title = "Run C",
                description = "Deterministic selected-entry failure after successful remote enumeration.",
                unarchiveEnabled = true,
                selectedInternalPaths = runtimeConfig.normalizedSelectedInternalPaths + "__spike4_missing__/not-present.zip",
                expectedOutcome = RunExpectation.EXPECTED_FAILURE,
                expectedFailureStage = Spike4Stage.ARCHIVE_SELECTION,
            ),
        )

        definitions.forEach { definition ->
            runResults += executeRun(
                sessionId = sessionId,
                deviceMetadata = deviceMetadata,
                definition = definition,
            )
        }

        val sessionRecord = SessionRecord(
            sessionId = sessionId,
            startedAtUtc = startedAtUtc,
            finishedAtUtc = nowUtc(),
            selectedDeviceSerial = selectedDeviceSerial,
            appId = appContext.packageName,
            device = deviceMetadata,
            runtime = runtimeInit,
            runs = runResults.map { it.first },
        )
        artifactWriter.writeSession(sessionRecord)
        sessionRecord
    }

    private suspend fun executeRun(
        sessionId: String,
        deviceMetadata: DeviceMetadata,
        definition: Spike4RunDefinition,
    ): Pair<RunStatusRecord, OutputManifest?> {
        val runRoot = artifactWriter.runRoot(definition.id)
        artifactWriter.writeRunDefinition(runRoot, definition)
        val diagnostics = Spike4DiagnosticsStore(sessionId, appContext.packageName, deviceMetadata)
        val traceRecorder = HttpTraceRecorder()
        val rangeClient = OkHttpRangeClient(OkHttpClient.Builder().build(), traceRecorder)
        val resolver = Spike4Resolver(RealDebridClient.create(runtimeConfig.rdApiToken))
        val outputPolicy = Spike4OutputPolicy(runRoot)
        val cleanupActions = mutableListOf<CleanupActionRecord>()
        val identityTrace = linkedMapOf<String, IdentityTraceRecord>()
        val finalOutputRoot = runRoot.resolve("final-output-tree/${runtimeConfig.normalizedDestinationSubfolder}").apply { mkdirs() }
        val stagedCopyRoot = runRoot.resolve("work/copied").apply { mkdirs() }
        var finalManifest: OutputManifest? = null

        return try {
            diagnostics.record("archive-selection", "run-start", "started", runId = definition.id)
            val resolverOutcome = resolver.resolveExactZip(definition.id, runtimeConfig, diagnostics)
            artifactWriter.writeResolverArtifacts(runRoot, resolverOutcome.record, resolverOutcome.providerInfo)

            RemoteZipSession(
                caseId = definition.id,
                archiveSource = resolverOutcome.record.exactZipPath,
                archiveUrl = resolverOutcome.record.unrestrictedDownloadUrl.toArchiveHttpUrl(),
                rangeHttpClient = rangeClient,
            ).use { session ->
                diagnostics.record("archive-selection", "enumeration-start", "started", runId = definition.id)
                val enumerated = session.enumerate()
                val filtered = IgnoreGlobFilter.apply(
                    entries = enumerated,
                    ignoreGlobs = runtimeConfig.normalizedIgnoreGlobs,
                    selectedPaths = definition.selectedInternalPaths,
                )
                diagnostics.record(
                    "archive-selection",
                    "enumeration-complete",
                    "succeeded",
                    runId = definition.id,
                    details = mapOf(
                        "candidateCount" to enumerated.count { !it.isDirectory }.toString(),
                        "visibleCount" to filtered.visibleEntries.size.toString(),
                        "selectedCount" to filtered.selectedEntries.size.toString(),
                    ),
                )
                artifactWriter.writeArchiveSelectionArtifacts(
                    runRoot = runRoot,
                    candidates = enumerated,
                    ignored = filtered.ignoredEntries,
                    selected = filtered.selectedEntries,
                )

                val copiedFiles = session.copySelected(filtered.selectedEntries.map { it.identity }, stagedCopyRoot)
                val queueTasks = filtered.selectedEntries.zip(copiedFiles).mapIndexed { index, pair ->
                    val entry = pair.first
                    val copiedFile = pair.second
                    val taskId = "task-${(index + 1).toString().padStart(2, '0')}"
                    QueueTaskRecord(
                        taskId = taskId,
                        entryIdentity = entry.identity,
                        originalDisplayName = entry.identity.entryPath.substringAfterLast('/'),
                        stagedArchiveRelativePath = copiedFile.invariantRelativeTo(runRoot),
                        destinationSubfolder = runtimeConfig.normalizedDestinationSubfolder,
                    )
                }
                val beforeUnarchive = OutputManifest(
                    passId = "before-unarchive",
                    outputs = queueTasks.mapIndexed { index, task ->
                        OutputManifestEntry(
                            taskId = task.taskId,
                            sourceEntryPath = task.entryIdentity.entryPath,
                            passId = "before-unarchive",
                            outputRelativePath = copiedFiles[index].invariantRelativeTo(runRoot),
                            bytesWritten = copiedFiles[index].length(),
                            isArchiveCandidate = true,
                            renameApplied = false,
                            collisionIndex = null,
                        )
                    }
                )
                artifactWriter.writeBeforeUnarchive(runRoot, beforeUnarchive)

                if (!definition.unarchiveEnabled) {
                    queueTasks.forEachIndexed { index, task ->
                        identityTrace[task.taskId] = IdentityTraceRecord(
                            taskId = task.taskId,
                            providerFilePath = runtimeConfig.normalizedExactZipPath,
                            selectedEntryPath = task.entryIdentity.entryPath,
                            stagedArchiveRelativePath = copiedFiles[index].invariantRelativeTo(runRoot),
                            finalOutputs = listOf(copiedFiles[index].invariantRelativeTo(runRoot)),
                        )
                    }
                    finalManifest = beforeUnarchive
                    artifactWriter.writeFinalOutputManifest(runRoot, beforeUnarchive)
                } else {
                    finalManifest = runExtractionPipeline(
                        definition = definition,
                        runRoot = runRoot,
                        finalOutputRoot = finalOutputRoot,
                        copiedFiles = copiedFiles,
                        queueTasks = queueTasks,
                        diagnostics = diagnostics,
                        outputPolicy = outputPolicy,
                        cleanupActions = cleanupActions,
                        identityTrace = identityTrace,
                        artifactWriter = artifactWriter,
                    )
                }
            }

            artifactWriter.writeCleanup(runRoot, CleanupRecord(cleanupActions))
            artifactWriter.writeIdentityTrace(runRoot, identityTrace.values.toList())
            artifactWriter.writeStageTimeline(runRoot, diagnostics.events())
            diagnostics.write(runRoot)
            runRoot.resolve("http-trace.ndjson").writeText(
                traceRecorder.all().joinToString(separator = "\n", postfix = if (traceRecorder.all().isEmpty()) "" else "\n") {
                    spike4Json.encodeToString(it)
                }
            )

            val status = RunStatusCode.PASSED
            Pair(
                RunStatusRecord(definition.id, status, "Run completed with required evidence."),
                finalManifest,
            )
        } catch (failure: Throwable) {
            val spikeFailure = failure as? Spike4FailureException
            if (spikeFailure != null) {
                diagnostics.recordFailure(
                    domain = "archive-selection",
                    event = "run-failed",
                    stage = spikeFailure.stage,
                    errorCode = spikeFailure.errorCode,
                    message = spikeFailure.message ?: spikeFailure.errorCode,
                    runId = definition.id,
                )
            }
            diagnostics.write(runRoot)
            artifactWriter.writeStageTimeline(runRoot, diagnostics.events())
            artifactWriter.writeCleanup(runRoot, CleanupRecord(cleanupActions))
            val failureRecord = FailureRecord(
                taskId = definition.id,
                sourceEntryPath = definition.selectedInternalPaths.joinToString(","),
                archivePath = runtimeConfig.normalizedExactZipPath,
                errorType = failure::class.java.name,
                message = failure.message,
                operationResults = emptyList(),
                stackTrace = failure.renderStackTrace(),
                firstCause = failure.cause?.renderSummary(),
                lastCause = failure.cause?.cause?.renderSummary(),
                firstPotentialCause = null,
                lastPotentialCause = null,
            )
            artifactWriter.writeFailure(runRoot, failureRecord)
            val expected = definition.expectedOutcome == RunExpectation.EXPECTED_FAILURE &&
                spikeFailure?.stage == definition.expectedFailureStage
            val status = when {
                expected -> RunStatusCode.EXPECTED_FAILURE_OBSERVED
                spikeFailure?.errorCode == "RECURSIVE_DELTA_MISSING" -> RunStatusCode.BLOCKED
                else -> RunStatusCode.FAILED
            }
            Pair(
                RunStatusRecord(
                    runId = definition.id,
                    status = status,
                    summary = failure.message ?: failure::class.java.simpleName,
                ),
                null,
            )
        }
    }

    private fun runExtractionPipeline(
        definition: Spike4RunDefinition,
        runRoot: File,
        finalOutputRoot: File,
        copiedFiles: List<File>,
        queueTasks: List<QueueTaskRecord>,
        diagnostics: Spike4DiagnosticsStore,
        outputPolicy: Spike4OutputPolicy,
        cleanupActions: MutableList<CleanupActionRecord>,
        identityTrace: MutableMap<String, IdentityTraceRecord>,
        artifactWriter: Spike4ArtifactWriter,
    ): OutputManifest {
        val currentInputs = queueTasks.zip(copiedFiles).map { (task, file) ->
            ArchiveTaskInput(
                taskId = task.taskId,
                sourceEntryPath = task.entryIdentity.entryPath,
                sourceFile = file,
                providerFilePath = runtimeConfig.normalizedExactZipPath,
                deleteAfterSuccess = true,
            )
        }
        val extractor = SevenZipArchiveExtractor()
        val passNumber = 1
        val passId = "pass-${passNumber.toString().padStart(2, '0')}"
        diagnostics.record("downloads", "pass-start", "started", snapshotId = passId)
        val passRoot = artifactWriter.passRoot(runRoot, passNumber)
        val passItems = mutableListOf<ArchiveItemRecord>()
        val passOutputs = mutableListOf<OutputManifestEntry>()

        currentInputs.forEach { input ->
            val extraction = extractor.extract(
                archiveInput = input,
                passId = passId,
                runRoot = runRoot,
                outputRoot = finalOutputRoot,
                renameRule = runtimeConfig.renameRule,
                outputPolicy = outputPolicy,
            )
            passItems += extraction.itemRecords
            passOutputs += extraction.outputEntries
            if (extraction.failure != null) {
                throw Spike4FailureException(Spike4Stage.UNARCHIVE, "LOCAL_UNARCHIVE_FAILED", extraction.failure.message ?: "Local unarchive failed")
            }
            if (input.deleteAfterSuccess && input.sourceFile.exists()) {
                input.sourceFile.delete()
                cleanupActions += CleanupActionRecord(
                    taskId = input.taskId,
                    action = CleanupActionType.DELETED,
                    path = input.sourceFile.invariantRelativeTo(runRoot),
                    reason = "deleted after successful extraction",
                )
            }
            identityTrace[input.taskId] = IdentityTraceRecord(
                taskId = input.taskId,
                providerFilePath = input.providerFilePath,
                selectedEntryPath = input.sourceEntryPath,
                stagedArchiveRelativePath = input.sourceFile.invariantRelativeTo(runRoot),
                finalOutputs = extraction.outputEntries.map { it.outputRelativePath },
            )
        }

        artifactWriter.writePassItems(passRoot, passItems.sortedBy { it.itemIndex })
        artifactWriter.writePassOutputManifest(passRoot, OutputManifest(passId, passOutputs.sortedBy { it.outputRelativePath }))
        diagnostics.record("downloads", "pass-complete", "succeeded", snapshotId = passId, details = mapOf("outputCount" to passOutputs.size.toString()))

        val finalOutputs = finalOutputRoot.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.invariantRelativeTo(runRoot) }
            .map { file ->
                OutputManifestEntry(
                    taskId = identityTrace.values.firstOrNull { trace -> file.invariantRelativeTo(runRoot) in trace.finalOutputs }?.taskId ?: "unknown",
                    sourceEntryPath = identityTrace.values.firstOrNull { trace -> file.invariantRelativeTo(runRoot) in trace.finalOutputs }?.selectedEntryPath ?: "unknown",
                    passId = "final",
                    outputRelativePath = file.invariantRelativeTo(runRoot),
                    bytesWritten = file.length(),
                    isArchiveCandidate = outputPolicy.isArchiveCandidate(file.name),
                    renameApplied = runtimeConfig.renameRule != null && !outputPolicy.isArchiveCandidate(file.name),
                    collisionIndex = null,
                )
            }
            .toList()
        val finalManifest = OutputManifest(passId = "final", outputs = finalOutputs)
        artifactWriter.writeFinalOutputManifest(runRoot, finalManifest)
        return finalManifest
    }
}
