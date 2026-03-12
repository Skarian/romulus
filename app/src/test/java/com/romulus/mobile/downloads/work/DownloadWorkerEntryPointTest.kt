package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.ConfigurableDownloadLedgerStore
import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeDownloadTransport
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.FakeProviderRuntimeGateway
import com.romulus.mobile.downloads.RecordingWorkerLauncher
import com.romulus.mobile.downloads.attempts.ArchiveEntryAttemptRunner
import com.romulus.mobile.downloads.attempts.StandardAttemptRunner
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.output.ArchiveExtractionController
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.output.OutputFinalizer
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.output.OutputRootResolver
import com.romulus.mobile.downloads.queue.DownloadLedgerStore
import com.romulus.mobile.downloads.queue.QueueActionRequest
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.downloads.queue.FileDownloadLedgerStore
import com.romulus.mobile.downloads.queue.FailureReason
import com.romulus.mobile.downloads.queue.PendingQueueAction
import com.romulus.mobile.downloads.queue.QueueRecoveryPolicy
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.QueueTaskState
import com.romulus.mobile.downloads.queue.TransferCheckpoint
import com.romulus.mobile.downloads.sampleQueueTaskInput
import com.romulus.mobile.realdebrid.AcquisitionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.romulus.mobile.realdebrid.AuthRequiredException
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ResolvedDownloadUnit

class DownloadWorkerEntryPointTest {
    @Test
    fun runUntilDrainedFailsWhenRetryPersistenceFails() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T20:00:00Z"), ZoneOffset.UTC)
        val delegateStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val ledgerStore = ConfigurableDownloadLedgerStore(delegateStore).apply {
            persistStateError = IllegalStateException("Retry state write failed")
        }
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.failure(IllegalStateException("Provider acquisition failed"))
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val entryPoint = DownloadWorkerEntryPoint(
            queueService = queueService,
            recoveryPolicy = QueueRecoveryPolicy(
                outputReservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts").toFile()
                )
            ),
            workScheduler = workScheduler,
            executionControlRegistry = ExecutionControlRegistry(),
            standardAttemptRunner = StandardAttemptRunner(
                providerGateway = providerGateway,
                downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
                outputReservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts-runner").toFile()
                ),
                outputFinalizer = OutputFinalizer(
                    reservationService = OutputReservationService(
                        outputFilesystem = outputFilesystem,
                        outputRootResolver = OutputRootResolver(
                            settingsService = settingsService,
                            clock = clock
                        ),
                        artifactRoot = createTempDirectory("worker-artifacts-finalizer").toFile()
                    ),
                    extractionController = ArchiveExtractionController(
                        archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                        outputFilesystem = outputFilesystem
                    ),
                    outputFilesystem = outputFilesystem
                ),
                queueService = queueService,
                clock = clock
            ),
            archiveEntryAttemptRunner = ArchiveEntryAttemptRunner(),
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isFailure)
        assertEquals("Retry state write failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun authRequiredFailsRowInsteadOfSchedulingRetry() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T21:00:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.failure(AuthRequiredException("Auth required"))
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued)
            .taskIds.single()
        val entryPoint = DownloadWorkerEntryPoint(
            queueService = queueService,
            recoveryPolicy = QueueRecoveryPolicy(
                outputReservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts").toFile()
                )
            ),
            workScheduler = workScheduler,
            executionControlRegistry = ExecutionControlRegistry(),
            standardAttemptRunner = StandardAttemptRunner(
                providerGateway = providerGateway,
                downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
                outputReservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts-runner").toFile()
                ),
                outputFinalizer = OutputFinalizer(
                    reservationService = OutputReservationService(
                        outputFilesystem = outputFilesystem,
                        outputRootResolver = OutputRootResolver(
                            settingsService = settingsService,
                            clock = clock
                        ),
                        artifactRoot = createTempDirectory("worker-artifacts-finalizer").toFile()
                    ),
                    extractionController = ArchiveExtractionController(
                        archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                        outputFilesystem = outputFilesystem
                    ),
                    outputFilesystem = outputFilesystem
                ),
                queueService = queueService,
                clock = clock
            ),
            archiveEntryAttemptRunner = ArchiveEntryAttemptRunner(),
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isSuccess)
        val row = checkNotNull(ledgerStore.readRow(taskId))
        assertTrue(row.state is com.romulus.mobile.downloads.queue.QueueTaskState.Failed)
        val reason = (row.state as com.romulus.mobile.downloads.queue.QueueTaskState.Failed).reason
        assertTrue(reason is FailureReason.AuthRequired)
    }

    @Test
    fun authBlockedStillHonorsPersistedCancelRecovery() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T21:30:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway(
            initialReadiness = com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = false,
                brokenReason = "API key is invalid"
            )
        )
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued)
            .taskIds.single()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 10,
            totalBytes = 20,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 10
        )
        ledgerStore.persistState(taskId, QueueTaskState.Running(checkpoint)).getOrThrow()
        ledgerStore.persistActionRequest(
            taskId = taskId,
            request = QueueActionRequest(
                taskId = taskId,
                action = PendingQueueAction.CANCEL,
                requestedAt = clock.instant()
            )
        ).getOrThrow()
        val entryPoint = DownloadWorkerEntryPoint(
            queueService = queueService,
            recoveryPolicy = QueueRecoveryPolicy(
                outputReservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts").toFile()
                )
            ),
            workScheduler = workScheduler,
            executionControlRegistry = ExecutionControlRegistry(),
            standardAttemptRunner = StandardAttemptRunner(
                providerGateway = providerGateway,
                downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
                outputReservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts-runner").toFile()
                ),
                outputFinalizer = OutputFinalizer(
                    reservationService = OutputReservationService(
                        outputFilesystem = outputFilesystem,
                        outputRootResolver = OutputRootResolver(
                            settingsService = settingsService,
                            clock = clock
                        ),
                        artifactRoot = createTempDirectory("worker-artifacts-finalizer").toFile()
                    ),
                    extractionController = ArchiveExtractionController(
                        archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                        outputFilesystem = outputFilesystem
                    ),
                    outputFilesystem = outputFilesystem
                ),
                queueService = queueService,
                clock = clock
            ),
            archiveEntryAttemptRunner = ArchiveEntryAttemptRunner(),
            clock = clock
        )

        val result = entryPoint.runOnce()

        assertTrue(result.isSuccess)
        assertTrue(checkNotNull(ledgerStore.readRow(taskId)).state is QueueTaskState.Cancelled)
    }

    @Test
    fun runUntilDrainedCompletesRowAndPersistsOutputs() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T22:00:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.success(
                AcquisitionStatus.LinksReady(
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "download-torrent",
                        sourceMagnetUri = "magnet:?xt=urn:btih:source",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(ProviderReadyLink("https://restricted.example/file"))
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://download.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued)
            .taskIds.single()
        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isSuccess)
        val row = checkNotNull(ledgerStore.readRow(taskId))
        assertEquals(QueueTaskState.Completed, row.state)
        assertEquals(listOf("shows/Episode.mkv"), row.finalOutputs.map { it.relativePath })
        assertEquals(setOf("shows/Episode.mkv"), outputFilesystem.writtenOutputs.keys)
    }

    @Test
    fun runUntilDrainedClearsPendingDispatchAfterFailedEnqueueWake() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T22:10:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.success(
                AcquisitionStatus.LinksReady(
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "download-torrent",
                        sourceMagnetUri = "magnet:?xt=urn:btih:source",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(ProviderReadyLink("https://restricted.example/file"))
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://download.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val launcher = RecordingWorkerLauncher().apply {
            nextImmediateLaunchFailure = IllegalStateException("Immediate wake failed")
        }
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = launcher,
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )

        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))

        assertTrue(enqueueResult is EnqueueResult.EnqueuedPendingDispatch)
        assertTrue(ledgerStore.hasPendingDispatch())

        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isSuccess)
        assertTrue(!ledgerStore.hasPendingDispatch())
        assertEquals(QueueTaskState.Completed, ledgerStore.readRows().single().state)
    }

    @Test
    fun runUntilDrainedHonorsPendingCancelBeforeAttemptExecution() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T22:30:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.failure(IllegalStateException("Attempt should not start"))
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued)
            .taskIds.single()
        queueService.beginAttempt(taskId)
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 2,
            totalBytes = 4,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 2
        )
        ledgerStore.persistState(taskId, QueueTaskState.Running(checkpoint)).getOrThrow()
        ledgerStore.persistActionRequest(
            taskId = taskId,
            request = QueueActionRequest(
                taskId = taskId,
                action = PendingQueueAction.CANCEL,
                requestedAt = clock.instant()
            )
        ).getOrThrow()
        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isSuccess)
        val row = checkNotNull(ledgerStore.readRow(taskId))
        assertTrue(row.state is QueueTaskState.Cancelled)
    }

    @Test
    fun runOnceSchedulesLeaseExpiryWakeForPendingCancelRecovery() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T22:45:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway()
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val launcher = RecordingWorkerLauncher()
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = launcher,
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued)
            .taskIds.single()
        queueService.claimRunnableTasks(clock.instant(), limit = 1)
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 2,
            totalBytes = 4,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 2
        )
        ledgerStore.persistState(taskId, QueueTaskState.Running(checkpoint)).getOrThrow()
        queueService.performAction(com.romulus.mobile.downloads.queue.QueueActionCommand.Cancel(taskId))
            .getOrThrow()
        val leaseExpiry = checkNotNull(ledgerStore.readRow(taskId)?.lease).leaseExpiresAt
        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock
        )

        val result = entryPoint.runOnce()

        assertTrue(result.isSuccess)
        assertEquals(listOf(leaseExpiry), launcher.scheduledInstants)
    }

    @Test
    fun runUntilDrainedKeepsDrainingWhenWakeGenerationAdvancesAtEmptyBoundary() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T23:00:00Z"), ZoneOffset.UTC)
        val delegateStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.success(
                AcquisitionStatus.LinksReady(
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "download-torrent",
                        sourceMagnetUri = "magnet:?xt=urn:btih:source",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(ProviderReadyLink("https://restricted.example/file"))
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://download.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        lateinit var queueService: QueueService
        val ledgerStore = WakeGenerationCallbackLedgerStore(delegateStore) { readCount ->
            if (readCount == 2) {
                queueService.enqueue(listOf(sampleQueueTaskInput("Late.mkv")))
            }
        }
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isSuccess)
        val rows = ledgerStore.readRows()
        assertEquals(2, rows.size)
        assertTrue(rows.all { row -> row.state == QueueTaskState.Completed })
    }

    @Test
    fun authRepairDuringDrainExitKeepsQueuedRowsDraining() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-11T00:00:00Z"), ZoneOffset.UTC)
        val delegateStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val ledgerStore = ConfigurableDownloadLedgerStore(delegateStore)
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = FakeProviderRuntimeGateway(
            initialReadiness = com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = false,
                brokenReason = "API key is invalid"
            )
        ).apply {
            startResult = Result.success(
                AcquisitionStatus.LinksReady(
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "download-torrent",
                        sourceMagnetUri = "magnet:?xt=urn:btih:source",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(ProviderReadyLink("https://restricted.example/file"))
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://download.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        ledgerStore.onClaimActionRecoveryTasks = {
            ledgerStore.onClaimActionRecoveryTasks = null
            providerGateway.updateReadiness(
                com.romulus.mobile.realdebrid.auth.TokenReadiness(
                    isUsable = true,
                    brokenReason = null
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("worker-output").toFile())
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued)
            .taskIds.single()
        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock
        )

        val result = entryPoint.runUntilDrained()

        assertTrue(result.isSuccess)
        assertEquals(QueueTaskState.Completed, checkNotNull(ledgerStore.readRow(taskId)).state)
    }

    private fun queueJson(): Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    private fun createEntryPoint(
        ledgerStore: DownloadLedgerStore,
        settingsService: DownloadSettingsService,
        providerGateway: FakeProviderRuntimeGateway,
        outputFilesystem: FakeOutputFilesystem,
        workScheduler: WorkScheduler,
        queueService: QueueService,
        clock: Clock
    ): DownloadWorkerEntryPoint = DownloadWorkerEntryPoint(
        queueService = queueService,
        recoveryPolicy = QueueRecoveryPolicy(
            outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = clock
                ),
                artifactRoot = createTempDirectory("worker-artifacts").toFile()
            )
        ),
        workScheduler = workScheduler,
        executionControlRegistry = ExecutionControlRegistry(),
        standardAttemptRunner = StandardAttemptRunner(
            providerGateway = providerGateway,
            downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
            outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = clock
                ),
                artifactRoot = createTempDirectory("worker-artifacts-runner").toFile()
            ),
            outputFinalizer = OutputFinalizer(
                reservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("worker-artifacts-finalizer").toFile()
                ),
                extractionController = ArchiveExtractionController(
                    archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                    outputFilesystem = outputFilesystem
                ),
                outputFilesystem = outputFilesystem
            ),
            queueService = queueService,
            clock = clock
        ),
        archiveEntryAttemptRunner = ArchiveEntryAttemptRunner(),
        clock = clock
    )
}

private class WakeGenerationCallbackLedgerStore(
    private val delegate: DownloadLedgerStore,
    private val onWakeObservation: suspend (Int) -> Unit
) : DownloadLedgerStore by delegate {
    private var wakeObservations = 0

    override suspend fun readWakeGeneration(): Long {
        wakeObservations += 1
        onWakeObservation(wakeObservations)
        return delegate.readWakeGeneration()
    }

    override suspend fun acknowledgeDispatchStart(): Result<Long> {
        wakeObservations += 1
        onWakeObservation(wakeObservations)
        return delegate.acknowledgeDispatchStart()
    }
}
