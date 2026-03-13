package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.downloads.config.DownloadSettingsReadiness
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.config.DownloadSettingsStore
import com.romulus.mobile.downloads.config.OutputDirectoryAccess
import com.romulus.mobile.downloads.output.ArchiveExtractionController
import com.romulus.mobile.downloads.output.ArchiveRuntime
import com.romulus.mobile.downloads.output.ArchiveRuntimeEntry
import com.romulus.mobile.downloads.output.ArchiveRuntimePassResult
import com.romulus.mobile.downloads.output.FinalizationControl
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.output.OutputFilesystem
import com.romulus.mobile.downloads.output.OutputFinalizer
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.output.OutputRootResolver
import com.romulus.mobile.downloads.queue.ControlSignal
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.downloads.queue.FileDownloadLedgerStore
import com.romulus.mobile.downloads.queue.NamingIntent
import com.romulus.mobile.downloads.queue.QueueExecutionContext
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.QueueTaskInput
import com.romulus.mobile.downloads.queue.RecoveryDecision
import com.romulus.mobile.downloads.queue.SourceQueueMetadata
import com.romulus.mobile.downloads.queue.StorageTargetContext
import com.romulus.mobile.downloads.work.ExecutionControlRegistry
import com.romulus.mobile.downloads.work.WorkScheduler
import com.romulus.mobile.downloads.work.WorkerLauncher
import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.auth.TokenReadiness
import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.source.browse.ArchivePreparationKey
import com.romulus.mobile.source.browse.SelectableItemId
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEntryAttemptRunnerTest {
    @Test
    fun copiesSelectedArchiveEntryIntoReservedArtifactAndFinalizesDirectSave() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-13T12:00:00Z"), java.time.ZoneOffset.UTC)
        val outputRoot = Files.createTempDirectory("archive-entry-output").toFile()
        val runtimeRoot = Files.createTempDirectory("archive-entry-runtime").toFile()
        val settingsService = DownloadSettingsService.create(
            store = InMemoryDownloadSettingsStore(
                DownloadSettingsState(
                    outputDirectoryUri = outputRoot.absolutePath,
                    maxConcurrency = 1
                )
            ),
            outputAccess = object : OutputDirectoryAccess {
                override suspend fun check(outputDirectoryUri: String?): DownloadSettingsReadiness =
                    DownloadSettingsReadiness(
                        isUsable = outputDirectoryUri != null && File(outputDirectoryUri).exists(),
                        brokenReason = if (outputDirectoryUri == null) {
                            "missing"
                        } else {
                            null
                        }
                    )
            }
        )
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = runtimeRoot.resolve("ledger.json"),
            json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                classDiscriminator = "kind"
            },
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(LocalPathOutputFilesystem()),
            workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = object : ProviderRuntimeGateway {
                    private val readiness = MutableStateFlow(TokenReadiness(true, null))
                    override fun observeTokenReadiness(): StateFlow<TokenReadiness> = readiness
                    override suspend fun readTokenReadiness(): TokenReadiness = readiness.value
                    override suspend fun startAcquisition(request: com.romulus.mobile.realdebrid.ProviderSelectionRequest) =
                        error("unused")
                    override suspend fun resumeAcquisition(marker: com.romulus.mobile.realdebrid.ProviderResumeMarker) =
                        error("unused")
                    override suspend fun resolveReadyLink(link: com.romulus.mobile.realdebrid.ProviderReadyLink) =
                        error("unused")
                },
                workerLauncher = object : WorkerLauncher {
                    override suspend fun launchNow(): Result<Unit> = Result.success(Unit)
                    override suspend fun launchAt(instant: Instant): Result<Unit> = Result.success(Unit)
                },
                clock = clock
            ),
            clock = clock
        )
        val outputReservationService = OutputReservationService(
            outputFilesystem = LocalPathOutputFilesystem(),
            outputRootResolver = OutputRootResolver(settingsService, clock),
            artifactRoot = runtimeRoot
        )
        val runner = ArchiveEntryAttemptRunner(
            archiveContainerGateway = object : ArchiveContainerGateway {
                override suspend fun resolveReadyArchiveContainer(
                    preparationKey: ArchivePreparationKey
                ): Result<ArchiveContainerLocator> = Result.success(
                    ArchiveContainerLocator(
                        archiveUrl = "https://download.example/archive.zip",
                        originalName = "archive.zip",
                        providerLocator = exactMatchProviderLocator()
                    )
                )
            },
            remoteZipCopyGateway = object : RemoteZipCopyGateway {
                override suspend fun copySelectedEntry(
                    request: com.romulus.mobile.remotezip.CopySelectedEntryRequest
                ): Result<Unit> {
                    Files.write(request.destination, "picked-entry".toByteArray())
                    request.onProgress("picked-entry".length.toLong())
                    return Result.success(Unit)
                }
            },
            outputReservationService = outputReservationService,
            outputFinalizer = OutputFinalizer(
                reservationService = outputReservationService,
                extractionController = ArchiveExtractionController(
                    archiveRuntime = object : ArchiveRuntime {
                        override suspend fun inspect(archiveFile: File): Result<List<ArchiveRuntimeEntry>> =
                            Result.success(emptyList())
                        override suspend fun extract(
                            archiveFile: File,
                            targetFiles: Map<String, File>,
                            control: FinalizationControl?
                        ): Result<ArchiveRuntimePassResult> = error("unused")
                    },
                    outputFilesystem = LocalPathOutputFilesystem()
                ),
                outputFilesystem = LocalPathOutputFilesystem()
            ),
            queueService = queueService,
            clock = clock
        )

        val enqueueResult = queueService.enqueue(
            listOf(
                QueueTaskInput(
                    snapshotId = SnapshotId("snapshot-1"),
                    entryId = SourceEntryId("entry-1"),
                    selectedItemId = SelectableItemId("item-1"),
                    originalDisplayName = "picked-entry.bin",
                    originalSizeBytes = 12L,
                    sourceMetadata = SourceQueueMetadata(
                        entryDisplayName = "Archive source",
                        partLabel = "Disc 1",
                        providerFileId = "outer-zip"
                    ),
                    namingIntent = NamingIntent(
                        applyRename = false,
                        renameRule = null
                    ),
                    unarchiveIntent = false,
                    recursiveUnarchiveIntent = false,
                    storageTarget = StorageTargetContext(subfolder = "archive"),
                    executionContext = QueueExecutionContext.ArchiveEntry(
                        preparationKey = ArchivePreparationKey(
                            snapshotId = SnapshotId("snapshot-1"),
                            entryId = SourceEntryId("entry-1")
                        ),
                        archiveEntryIdentity = ArchiveEntryIdentity(
                            localHeaderOffset = 10L,
                            compressedSize = 12L,
                            uncompressedSize = 12L,
                            crc32 = 99L,
                            normalizedPath = "folder/picked-entry.bin"
                        )
                    )
                )
            )
        )
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        val claim = queueService.claimRunnableTasks(clock.instant(), 1).single()

        val result = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = object : ControlHandle {
                override suspend fun awaitSignal(): ControlSignal = ControlSignal.NONE
                override fun current(): ControlSignal = ControlSignal.NONE
            }
        )

        val completed = result as AttemptOutcome.Completed
        assertEquals("picked-entry.bin", completed.outputs.single().displayName)
        assertTrue(outputRoot.resolve("archive/picked-entry.bin").exists())
        assertEquals("picked-entry", outputRoot.resolve("archive/picked-entry.bin").readText())
        val row = ledgerStore.readRow(taskId)
        assertEquals(1, row?.finalOutputs?.size)
        assertEquals(
            com.romulus.mobile.downloads.queue.QueueTaskState.Running(
                com.romulus.mobile.downloads.queue.TransferCheckpoint(
                    downloadedBytes = 12L,
                    totalBytes = 12L,
                    lastPersistedAt = clock.instant(),
                    tempFileToken = null,
                    resumeByteOffset = 12L
                )
            ),
            row?.state
        )
    }

    private fun exactMatchProviderLocator() = com.romulus.mobile.realdebrid.ProviderLocator(
        sourceMagnetUri = "magnet:?xt=urn:btih:test",
        torrentId = "torrent-1",
        providerFileIds = listOf("outer-zip"),
        selectedProviderFileId = "outer-zip",
        path = "Show/archive.zip",
        partLabel = "Disc 1"
    )

    private class InMemoryDownloadSettingsStore(
        initialState: DownloadSettingsState
    ) : DownloadSettingsStore {
        private var state = initialState

        override suspend fun read(): DownloadSettingsState = state

        override suspend fun write(state: DownloadSettingsState): Result<Unit> {
            this.state = state
            return Result.success(Unit)
        }
    }

    private class LocalPathOutputFilesystem : OutputFilesystem {
        override suspend fun listRelativePaths(
            outputDirectoryUri: String,
            subfolder: String
        ): Result<Set<String>> {
            val directory = File(outputDirectoryUri, subfolder)
            val relativePaths = if (!directory.exists()) {
                emptySet()
            } else {
                directory.walkTopDown()
                    .filter { it.isFile }
                    .map { file ->
                        file.relativeTo(File(outputDirectoryUri)).invariantSeparatorsPath
                    }
                    .toSet()
            }
            return Result.success(relativePaths)
        }

        override suspend fun writeArtifactToFinalOutput(
            artifactPath: String,
            outputDirectoryUri: String,
            relativePath: String,
            control: FinalizationControl?
        ): Result<Long> = runCatching {
            val target = File(outputDirectoryUri, relativePath)
            target.parentFile?.mkdirs()
            control?.currentSignal()?.let { signal ->
                check(signal == ControlSignal.NONE) { "stopped" }
            }
            File(artifactPath).copyTo(target, overwrite = false)
            target.length()
        }

        override suspend fun deleteFinalOutput(
            outputDirectoryUri: String,
            relativePath: String
        ): Result<Unit> = runCatching {
            File(outputDirectoryUri, relativePath).delete()
            Unit
        }

        override fun isSupportedArchive(localArtifactPath: String): Boolean = false
    }
}
