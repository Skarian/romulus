package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.ConfigurableDownloadLedgerStore
import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeDownloadTransport
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.FakeProviderRuntimeGateway
import com.romulus.mobile.downloads.RecordingWorkerLauncher
import com.romulus.mobile.downloads.attempts.DownloadStream
import com.romulus.mobile.downloads.attempts.DownloadTransport
import com.romulus.mobile.downloads.attempts.ProviderRuntimeGateway
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
import com.romulus.mobile.realdebrid.ProviderSelectionRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun runUntilDrainedRefillsFreedSlotBeforeSlowestAttemptFinishes() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-11T01:00:00Z"), ZoneOffset.UTC)
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("worker-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 2
                )
            },
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )
        val providerGateway = NamedFileProviderRuntimeGateway()
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
        queueService.enqueue(
            listOf(
                sampleQueueTaskInput("slow.mkv"),
                sampleQueueTaskInput("fast.mkv"),
                sampleQueueTaskInput("late.mkv")
            )
        )
        val slowStarted = CountDownLatch(1)
        val lateStarted = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)
        val downloadTransport = SlotTrackingDownloadTransport(
            slowFileName = "slow.mkv",
            slowStarted = slowStarted,
            lateStarted = lateStarted,
            releaseSlow = releaseSlow
        )
        val entryPoint = createEntryPoint(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            workScheduler = workScheduler,
            queueService = queueService,
            clock = clock,
            downloadTransport = downloadTransport
        )

        val resultDeferred = async(Dispatchers.Default) { entryPoint.runUntilDrained() }

        assertTrue(slowStarted.await(2, TimeUnit.SECONDS))
        assertTrue(lateStarted.await(2, TimeUnit.SECONDS))
        assertEquals(2, downloadTransport.maxActiveCount.get())

        releaseSlow.countDown()

        val result = resultDeferred.await()

        assertTrue(result.isSuccess)
        assertTrue(ledgerStore.readRows().all { row -> row.state == QueueTaskState.Completed })
        assertEquals(2, downloadTransport.maxActiveCount.get())
    }

    @Test
    fun runUntilDrainedHonorsPendingCancelWhenTransferFailsAfterCancelRequest() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-11T01:30:00Z"), ZoneOffset.UTC)
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
        val executionControlRegistry = ExecutionControlRegistry()
        val workScheduler = WorkScheduler(
            settingsService = settingsService,
            ledgerStore = ledgerStore,
            providerGateway = providerGateway,
            workerLauncher = RecordingWorkerLauncher(),
            clock = clock
        )
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = executionControlRegistry,
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = workScheduler,
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        val firstChunkRead = CountDownLatch(1)
        val allowFailure = CountDownLatch(1)
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
            executionControlRegistry = executionControlRegistry,
            standardAttemptRunner = StandardAttemptRunner(
                providerGateway = providerGateway,
                downloadTransport = FailingAfterCancelDownloadTransport(
                    firstChunkRead = firstChunkRead,
                    allowFailure = allowFailure
                ),
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

        val resultDeferred = async(Dispatchers.Default) { entryPoint.runUntilDrained() }

        assertTrue(firstChunkRead.await(2, TimeUnit.SECONDS))
        queueService.performAction(com.romulus.mobile.downloads.queue.QueueActionCommand.Cancel(taskId))
            .getOrThrow()
        allowFailure.countDown()

        val result = resultDeferred.await()

        assertTrue(result.isSuccess)
        assertTrue(checkNotNull(ledgerStore.readRow(taskId)).state is QueueTaskState.Cancelled)
    }

    private fun queueJson(): Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    private fun createEntryPoint(
        ledgerStore: DownloadLedgerStore,
        settingsService: DownloadSettingsService,
        providerGateway: ProviderRuntimeGateway,
        outputFilesystem: FakeOutputFilesystem,
        workScheduler: WorkScheduler,
        queueService: QueueService,
        clock: Clock,
        downloadTransport: DownloadTransport = FakeDownloadTransport("test".encodeToByteArray())
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
            downloadTransport = downloadTransport,
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

private class NamedFileProviderRuntimeGateway : ProviderRuntimeGateway {
    private val readiness = MutableStateFlow(
        com.romulus.mobile.realdebrid.auth.TokenReadiness(
            isUsable = true,
            brokenReason = null
        )
    )

    override fun observeTokenReadiness() = readiness

    override suspend fun readTokenReadiness() = readiness.value

    override suspend fun startAcquisition(
        request: ProviderSelectionRequest
    ): Result<AcquisitionStatus> {
        val fileName = request.normalizedPath.substringAfterLast('/')
        return Result.success(
            AcquisitionStatus.LinksReady(
                resumeMarker = ProviderResumeMarker(
                    torrentId = "download-$fileName",
                    sourceMagnetUri = request.sourceMagnetUri,
                    selectedProviderFileIds = listOf("provider-$fileName")
                ),
                readyLinks = listOf(ProviderReadyLink("https://restricted.example/$fileName"))
            )
        )
    }

    override suspend fun resumeAcquisition(
        marker: ProviderResumeMarker
    ): Result<AcquisitionStatus> =
        Result.failure(IllegalStateException("Resume was not expected in this test"))

    override suspend fun resolveReadyLink(
        link: ProviderReadyLink
    ): Result<ResolvedDownloadUnit> {
        val fileName = link.restrictedUrl.substringAfterLast('/')
        return Result.success(
            ResolvedDownloadUnit(
                downloadUrl = "https://download.example/$fileName",
                originalName = fileName,
                sizeBytes = fileName.encodeToByteArray().size.toLong()
            )
        )
    }
}

private class SlotTrackingDownloadTransport(
    private val slowFileName: String,
    private val slowStarted: CountDownLatch,
    private val lateStarted: CountDownLatch,
    private val releaseSlow: CountDownLatch
) : DownloadTransport {
    val maxActiveCount = AtomicInteger(0)
    private val activeCount = AtomicInteger(0)

    override suspend fun open(url: String, resumeByteOffset: Long): Result<DownloadStream> {
        val fileName = url.substringAfterLast('/')
        val activeNow = activeCount.incrementAndGet()
        maxActiveCount.updateAndGet { current -> maxOf(current, activeNow) }
        if (fileName == "late.mkv") {
            lateStarted.countDown()
        }
        val bytes = fileName.encodeToByteArray()
        val inputStream = if (fileName == slowFileName) {
            BlockingInputStream(
                delegate = ByteArrayInputStream(bytes),
                started = slowStarted,
                release = releaseSlow
            )
        } else {
            ByteArrayInputStream(bytes)
        }
        return Result.success(
            DownloadStream(
                inputStream = inputStream,
                totalBytes = bytes.size.toLong(),
                resumeAccepted = resumeByteOffset == 0L,
                closeable = Closeable {
                    activeCount.decrementAndGet()
                }
            )
        )
    }
}

private class BlockingInputStream(
    private val delegate: ByteArrayInputStream,
    private val started: CountDownLatch,
    private val release: CountDownLatch
) : InputStream() {
    private val startSignaled = AtomicBoolean(false)

    override fun read(): Int {
        blockIfNeeded()
        return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        blockIfNeeded()
        return delegate.read(b, off, len)
    }

    private fun blockIfNeeded() {
        if (startSignaled.compareAndSet(false, true)) {
            started.countDown()
        }
        check(release.await(2, TimeUnit.SECONDS)) {
            "Slow download was not released in time"
        }
    }
}

private class FailingAfterCancelDownloadTransport(
    private val firstChunkRead: CountDownLatch,
    private val allowFailure: CountDownLatch
) : DownloadTransport {
    override suspend fun open(url: String, resumeByteOffset: Long): Result<DownloadStream> =
        Result.success(
            DownloadStream(
                inputStream = object : InputStream() {
                    private var readCount = 0

                    override fun read(): Int {
                        readCount += 1
                        return when (readCount) {
                            1 -> {
                                firstChunkRead.countDown()
                                'a'.code
                            }

                            2 -> {
                                check(allowFailure.await(2, TimeUnit.SECONDS)) {
                                    "Cancel-triggered failure was not released in time"
                                }
                                throw java.io.IOException("Transfer failed after cancel request")
                            }

                            else -> -1
                        }
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val value = read()
                        if (value < 0) {
                            return -1
                        }
                        b[off] = value.toByte()
                        return 1
                    }
                },
                totalBytes = 4L,
                resumeAccepted = resumeByteOffset == 0L,
                closeable = Closeable {}
            )
        )
}
