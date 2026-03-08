package com.romulus.spikes.spike2

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import java.io.File

class Spike2MatrixRunner(
    private val appContext: Context,
    private val fixtureAssetManager: AssetManager,
    private val selectedDeviceSerial: String?,
) {
    private val artifactWriter = Spike2ArtifactWriter(appContext)
    private val fixtureStager = Spike2FixtureStager(fixtureAssetManager, appContext)
    private val extractor = SevenZipArchiveExtractor()

    fun runFullMatrix(): SessionRecord {
        val sessionId = "session-${System.currentTimeMillis()}"
        val startedAtUtc = nowUtc()
        val definitions = Spike2RunDefinitions.all()
        val requiredPaths = definitions.flatMap { run -> run.fixtures.map { it.relativeInputPath } }.toSet()
        val stagedWorkspace = fixtureStager.stage(sessionId, requiredPaths)
        artifactWriter.resetLatestRoot()

        val runStatuses = mutableListOf<RunStatusRecord>()
        val sessionRuntime = extractor.captureRuntimeInit()

        definitions.forEach { definition ->
            val result = executeRun(definition, stagedWorkspace)
            runStatuses += RunStatusRecord(
                runId = definition.id,
                status = result.status,
                summary = result.summary,
            )
        }

        val sessionRecord = SessionRecord(
            sessionId = sessionId,
            startedAtUtc = startedAtUtc,
            finishedAtUtc = nowUtc(),
            selectedDeviceSerial = selectedDeviceSerial,
            appId = appContext.packageName,
            device = DeviceMetadata(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                product = Build.PRODUCT,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                abis = Build.SUPPORTED_ABIS.toList(),
            ),
            runtime = sessionRuntime,
            runs = runStatuses,
        )
        artifactWriter.writeSession(sessionRecord)
        return sessionRecord
    }

    private fun executeRun(
        definition: RunDefinition,
        stagedWorkspace: StagedFixtureWorkspace,
    ): RunExecutionResult {
        val runRoot = artifactWriter.runRoot(definition.id)
        val runInputRoot = stagedWorkspace.sessionRoot.resolve("run-inputs/${definition.id}").apply {
            deleteRecursively()
            mkdirs()
        }
        artifactWriter.writeRunDefinition(runRoot, definition)
        val runtimeInit = extractor.captureRuntimeInit()
        artifactWriter.writeRuntime(runRoot, runtimeInit)
        val outputPolicy = Spike2OutputPolicy(runRoot)
        val cleanupActions = mutableListOf<CleanupActionRecord>()
        val volumeResolutions = mutableListOf<VolumeResolutionRecord>()
        val finalOutputRoot = runRoot.resolve("final-output-tree")
        finalOutputRoot.mkdirs()

        var currentInputs = definition.fixtures.map { fixture ->
            val stagedSourceFile = stagedWorkspace.stagedInputRoot.resolve(fixture.relativeInputPath)
            val runSourceFile = runInputRoot.resolve(fixture.relativeInputPath).also { destination ->
                destination.parentFile?.mkdirs()
                stagedSourceFile.copyTo(destination, overwrite = true)
                applyFixtureInputMutation(destination, fixture.inputMutation)
            }
            PassArchiveInput(
                fixture = fixture,
                origin = PassInputOrigin.GENERATED_INPUT,
                sourceFile = runSourceFile,
                hostRelativeSourcePath = fixture.hostInputPath,
                deleteAfterSuccess = true,
            )
        }
        var passNumber = 1
        var failureRecord: FailureRecord? = null
        val maxRecursivePasses = 4

        while (currentInputs.isNotEmpty()) {
            if (passNumber > maxRecursivePasses) {
                failureRecord = FailureRecord(
                    fixtureId = definition.id,
                    archiveFamily = currentInputs.first().fixture.archiveFamily,
                    archivePath = currentInputs.first().sourceFile.absolutePath,
                    errorType = "max-recursive-passes-exceeded",
                    message = "Recursive unarchive exceeded $maxRecursivePasses passes.",
                    operationResults = emptyList(),
                    stackTrace = null,
                    firstCause = null,
                    lastCause = null,
                    firstPotentialCause = null,
                    lastPotentialCause = null,
                )
                break
            }
            val passId = "pass-${passNumber.toString().padStart(2, '0')}"
            val passRoot = artifactWriter.passRoot(runRoot, passNumber)
            artifactWriter.writeInputManifest(
                passRoot,
                PassInputManifest(
                    passId = passId,
                    archives = currentInputs.map { input ->
                        PassInputArchiveRecord(
                            fixtureId = input.fixture.id,
                            origin = input.origin,
                            archiveFamily = input.fixture.archiveFamily,
                            multipart = input.fixture.multipart,
                            inputMutation = input.fixture.inputMutation,
                            hostRelativeSourcePath = input.hostRelativeSourcePath,
                            deviceSourcePath = input.sourceFile.absolutePath,
                            outputSubdirectory = input.fixture.outputSubdirectory,
                            deleteAfterSuccess = input.deleteAfterSuccess,
                        )
                    },
                ),
            )

            val passItems = mutableListOf<ArchiveItemRecord>()
            val passOutputs = mutableListOf<OutputManifestEntry>()
            val nextInputs = mutableListOf<PassArchiveInput>()

            currentInputs.forEach { archiveInput ->
                val extraction = extractor.extract(
                    runDefinition = definition,
                    archiveInput = archiveInput,
                    passId = passId,
                    runRoot = runRoot,
                    finalOutputRoot = finalOutputRoot,
                    outputPolicy = outputPolicy,
                )
                passItems += extraction.itemRecords
                passOutputs += extraction.outputEntries
                volumeResolutions += extraction.volumeResolutions

                if (extraction.failure != null) {
                    failureRecord = extraction.failure
                    cleanupActions += CleanupActionRecord(
                        fixtureId = archiveInput.fixture.id,
                        action = CleanupActionType.RETAINED,
                        path = archiveInput.sourceFile.absolutePath,
                        reason = "retained because extraction failed",
                    )
                } else {
                    if (archiveInput.deleteAfterSuccess && archiveInput.sourceFile.exists()) {
                        archiveInput.sourceFile.delete()
                        cleanupActions += CleanupActionRecord(
                            fixtureId = archiveInput.fixture.id,
                            action = CleanupActionType.DELETED,
                            path = archiveInput.sourceFile.absolutePath,
                            reason = "deleted after successful extraction",
                        )
                    }
                    if (definition.recursiveUnarchiveEnabled) {
                        extraction.outputEntries
                            .filter { it.isArchiveCandidate }
                            .forEach { outputEntry ->
                                nextInputs += PassArchiveInput(
                                    fixture = archiveInput.fixture.copy(
                                        id = "${archiveInput.fixture.id}-${outputEntry.itemIndex}",
                                        relativeInputPath = outputEntry.outputRelativePath,
                                        hostInputPath = outputEntry.outputRelativePath,
                                        multipart = false,
                                    ),
                                    origin = PassInputOrigin.RECURSIVE_OUTPUT,
                                    sourceFile = runRoot.resolve(outputEntry.outputRelativePath),
                                    hostRelativeSourcePath = null,
                                    deleteAfterSuccess = true,
                                )
                            }
                    }
                }
            }

            artifactWriter.writeItems(passRoot, passItems.sortedBy { it.itemIndex })
            artifactWriter.writeOutputManifest(
                passRoot,
                OutputManifest(
                    passId = passId,
                    outputs = passOutputs.sortedBy { it.outputRelativePath },
                ),
            )

            if (failureRecord != null) {
                break
            }
            if (!definition.recursiveUnarchiveEnabled) {
                break
            }
            currentInputs = nextInputs
            passNumber += 1
        }

        artifactWriter.writeCleanup(runRoot, CleanupRecord(cleanupActions))
        if (volumeResolutions.isNotEmpty()) {
            artifactWriter.writeVolumeResolution(runRoot, volumeResolutions)
        }
        if (failureRecord != null) {
            artifactWriter.writeFailure(runRoot, failureRecord)
        }

        val status = when {
            definition.expectedOutcome == RunExpectation.EXPECTED_FAILURE && failureRecord != null -> {
                RunStatusCode.EXPECTED_FAILURE_OBSERVED
            }
            definition.expectedOutcome == RunExpectation.EXPECTED_FAILURE && failureRecord == null -> {
                RunStatusCode.FAILED
            }
            failureRecord == null -> RunStatusCode.PASSED
            else -> RunStatusCode.FAILED
        }
        val summary = when (status) {
            RunStatusCode.PASSED -> "Run completed without runtime failures."
            RunStatusCode.EXPECTED_FAILURE_OBSERVED -> "Expected failure captured for ${failureRecord?.fixtureId}."
            RunStatusCode.FAILED -> failureRecord?.message ?: "Run failed without a recorded message."
        }

        return RunExecutionResult(
            status = status,
            summary = summary,
            runtimeInit = runtimeInit,
            cleanup = CleanupRecord(cleanupActions),
            volumeResolutions = volumeResolutions,
        )
    }

    private fun applyFixtureInputMutation(
        destination: File,
        inputMutation: FixtureInputMutation?,
    ) {
        when (inputMutation) {
            null -> Unit
            FixtureInputMutation.REPLACE_WITH_GARBAGE_BYTES -> {
                destination.writeBytes("spike2-invalid-archive".encodeToByteArray())
            }
        }
    }
}
