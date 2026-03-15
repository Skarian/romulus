package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeDownloadTransport
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.FakeProviderRuntimeGateway
import com.romulus.mobile.downloads.RecordingWorkerLauncher
import com.romulus.mobile.downloads.ConfigurableDownloadLedgerStore
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.output.ArchiveExtractionController
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.output.OutputFinalizer
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.output.OutputRootResolver
import com.romulus.mobile.downloads.queue.DownloadLedgerStore
import com.romulus.mobile.downloads.queue.DirectSaveStage
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.downloads.queue.FinalizationCursor
import com.romulus.mobile.downloads.queue.FailureReason
import com.romulus.mobile.downloads.queue.FileDownloadLedgerStore
import com.romulus.mobile.downloads.queue.PreparingMetadata
import com.romulus.mobile.downloads.queue.QueueActionCommand
import com.romulus.mobile.downloads.queue.QueueClaim
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.QueueTaskState
import com.romulus.mobile.downloads.queue.RecoveryDecision
import com.romulus.mobile.downloads.sampleQueueTaskInput
import com.romulus.mobile.downloads.work.ExecutionControlRegistry
import com.romulus.mobile.downloads.work.WorkScheduler
import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.AuthRequiredException
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ResolvedDownloadUnit
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardAttemptRunnerTest {
    @Test
    fun directSaveAttemptCompletesAndFinalizesOutput() = runTest {
        val clock = testClock()
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
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
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(
                        ProviderReadyLink(
                            restrictedUrl = "https://downloads.example/file"
                        )
                    )
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://downloads.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile())
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
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)
        val runner = StandardAttemptRunner(
            providerGateway = providerGateway,
            downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
            outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = clock
                ),
                artifactRoot = createTempDirectory("runner-artifacts").toFile()
            ),
            outputFinalizer = OutputFinalizer(
                reservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("runner-artifacts-finalizer").toFile()
                ),
                extractionController = ArchiveExtractionController(
                    archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                    outputFilesystem = outputFilesystem
                ),
                outputFilesystem = outputFilesystem
            ),
            queueService = queueService,
            clock = clock
        )

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = ExecutionControlRegistry().register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Completed)
        assertEquals(
            setOf("shows/Episode.mkv"),
            outputFilesystem.writtenOutputs.keys
        )
        assertEquals("test", outputFilesystem.writtenOutputs["shows/Episode.mkv"]?.decodeToString())
    }

    @Test
    fun preparingPersistenceFailureStopsAttempt() = runTest {
        val clock = testClock()
        val delegateStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val ledgerStore = ConfigurableDownloadLedgerStore(delegateStore).apply {
            persistStateError = IllegalStateException("Preparing state write failed")
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
            startResult = Result.success(
                AcquisitionStatus.Waiting(
                    statusLabel = "waiting_files_selection",
                    progressPercent = 25.0,
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    )
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile())
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
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)
        val runner = StandardAttemptRunner(
            providerGateway = providerGateway,
            downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
            outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = clock
                ),
                artifactRoot = createTempDirectory("runner-artifacts").toFile()
            ),
            outputFinalizer = OutputFinalizer(
                reservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("runner-artifacts-finalizer").toFile()
                ),
                extractionController = ArchiveExtractionController(
                    archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                    outputFilesystem = outputFilesystem
                ),
                outputFilesystem = outputFilesystem
            ),
            queueService = queueService,
            clock = clock
        )

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = ExecutionControlRegistry().register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Failed)
        val reason = (outcome as AttemptOutcome.Failed).reason
        assertTrue(reason is FailureReason.OutputFailure)
        assertEquals("Preparing state write failed", (reason as FailureReason.OutputFailure).message)
    }

    @Test
    fun authFailureStopsAttemptWithoutRetry() = runTest {
        val clock = testClock()
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
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
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile())
        val runner = createRunner(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            clock = clock
        )
        val enqueueResult = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = providerGateway,
                workerLauncher = RecordingWorkerLauncher(),
                clock = clock
            ),
            clock = clock
        ).enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = providerGateway,
                workerLauncher = RecordingWorkerLauncher(),
                clock = clock
            ),
            clock = clock
        )
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = ExecutionControlRegistry().register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Failed)
        outcome as AttemptOutcome.Failed
        assertTrue(outcome.reason is FailureReason.AuthRequired)
        assertTrue(!outcome.retryable)
    }

    @Test
    fun preparingTimeoutStopsAttemptWithoutRetry() = runTest {
        val clock = testClock()
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
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
            resumeResult = Result.success(
                AcquisitionStatus.Waiting(
                    statusLabel = "queued",
                    progressPercent = 25.0,
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    )
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile())
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = providerGateway,
                workerLauncher = RecordingWorkerLauncher(),
                clock = clock
            ),
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)
        val runner = createRunner(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            clock = clock
        )

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.ResumePreparing(
                PreparingMetadata(
                    enteredAt = Instant.parse("2026-03-09T18:00:00Z"),
                    timeoutAt = Instant.parse("2026-03-10T17:59:59Z"),
                    lastProviderStatus = "queued",
                    lastProviderProgress = 25.0,
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    )
                )
            ),
            controlHandle = ExecutionControlRegistry().register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Failed)
        outcome as AttemptOutcome.Failed
        assertTrue(outcome.reason is FailureReason.ProviderFailure)
        assertEquals("Preparing", (outcome.reason as FailureReason.ProviderFailure).stage)
        assertTrue(!outcome.retryable)
    }

    @Test
    fun multipleReadyLinksFailInsteadOfGuessingFileIdentity() = runTest {
        val clock = testClock()
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
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
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(
                        ProviderReadyLink("https://downloads.example/file-a"),
                        ProviderReadyLink("https://downloads.example/file-b")
                    )
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile())
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = providerGateway,
                workerLauncher = RecordingWorkerLauncher(),
                clock = clock
            ),
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)
        val runner = createRunner(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            clock = clock
        )

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = ExecutionControlRegistry().register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Failed)
        assertEquals(
            "Provider returned multiple ready links for one queue row",
            ((outcome as AttemptOutcome.Failed).reason as FailureReason.ProviderFailure).message
        )
    }

    @Test
    fun cancelDuringDirectSaveFinalizationReturnsCancelledAndLeavesDurableCursor() = runTest {
        val clock = testClock()
        val ledgerStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
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
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(ProviderReadyLink("https://downloads.example/file"))
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://downloads.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile()).apply {
            copyChunkSize = 1
        }
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
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)
        val runner = StandardAttemptRunner(
            providerGateway = providerGateway,
            downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
            outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = clock
                ),
                artifactRoot = createTempDirectory("runner-artifacts").toFile()
            ),
            outputFinalizer = OutputFinalizer(
                reservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("runner-artifacts-finalizer").toFile()
                ),
                extractionController = ArchiveExtractionController(
                    archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                    outputFilesystem = outputFilesystem
                ),
                outputFilesystem = outputFilesystem
            ),
            queueService = queueService,
            clock = clock
        )
        outputFilesystem.onWriteChunk = {
            outputFilesystem.onWriteChunk = null
            runBlocking {
                queueService.performAction(QueueActionCommand.Cancel(taskId)).getOrThrow()
            }
        }

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = executionControlRegistry.register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Cancelled)
        val row = checkNotNull(ledgerStore.readRow(taskId))
        assertTrue(row.state is QueueTaskState.Running)
        assertEquals(
            FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING),
            row.finalizationCursor
        )
        assertTrue(outputFilesystem.writtenOutputs.isEmpty())
    }

    @Test
    fun directSaveFinalizationRenewsClaimDuringLongCopy() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-10T18:00:00Z"))
        val delegateStore = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("runner-ledger").toFile().resolve("ledger.json"),
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
        val providerGateway = FakeProviderRuntimeGateway().apply {
            startResult = Result.success(
                AcquisitionStatus.LinksReady(
                    resumeMarker = ProviderResumeMarker(
                        torrentId = "torrent-1",
                        sourceMagnetUri = "magnet:?xt=urn:btih:test",
                        selectedProviderFileIds = listOf("provider-file")
                    ),
                    readyLinks = listOf(ProviderReadyLink("https://downloads.example/file"))
                )
            )
            resolveResult = Result.success(
                ResolvedDownloadUnit(
                    downloadUrl = "https://downloads.example/file",
                    originalName = "Episode.mkv",
                    sizeBytes = 4
                )
            )
        }
        val outputFilesystem = FakeOutputFilesystem(createTempDirectory("runner-output").toFile()).apply {
            copyChunkSize = 1
            onWriteChunk = {
                clock.advanceBy(Duration.ofSeconds(31))
            }
        }
        val queueService = QueueService(
            ledgerStore = ledgerStore,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = providerGateway,
                workerLauncher = RecordingWorkerLauncher(),
                clock = clock
            ),
            clock = clock
        )
        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Episode.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        queueService.beginAttempt(taskId)
        val claim = ledgerStore.toClaim(taskId)
        val runner = createRunner(
            ledgerStore = ledgerStore,
            settingsService = settingsService,
            providerGateway = providerGateway,
            outputFilesystem = outputFilesystem,
            clock = clock
        )

        val outcome = runner.run(
            claim = claim,
            recoveryDecision = RecoveryDecision.Requeue(claim.state),
            controlHandle = ExecutionControlRegistry().register(taskId)
        )

        assertTrue(outcome is AttemptOutcome.Completed)
        assertTrue(ledgerStore.renewClaimCount > 0)
    }

    private suspend fun createRunner(
        ledgerStore: DownloadLedgerStore,
        settingsService: DownloadSettingsService,
        providerGateway: FakeProviderRuntimeGateway,
        outputFilesystem: FakeOutputFilesystem,
        clock: Clock
    ): StandardAttemptRunner {
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
        return StandardAttemptRunner(
            providerGateway = providerGateway,
            downloadTransport = FakeDownloadTransport("test".encodeToByteArray()),
            outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = clock
                ),
                artifactRoot = createTempDirectory("runner-artifacts").toFile()
            ),
            outputFinalizer = OutputFinalizer(
                reservationService = OutputReservationService(
                    outputFilesystem = outputFilesystem,
                    outputRootResolver = OutputRootResolver(
                        settingsService = settingsService,
                        clock = clock
                    ),
                    artifactRoot = createTempDirectory("runner-artifacts-finalizer").toFile()
                ),
                extractionController = ArchiveExtractionController(
                    archiveRuntime = com.romulus.mobile.downloads.FakeArchiveRuntime(),
                    outputFilesystem = outputFilesystem
                ),
                outputFilesystem = outputFilesystem
            ),
            queueService = queueService,
            clock = clock
        )
    }

    private suspend fun DownloadLedgerStore.toClaim(
        taskId: com.romulus.mobile.downloads.queue.TaskId
    ): QueueClaim {
        val row = checkNotNull(readRow(taskId))
        return QueueClaim(
            task = row.task,
            state = row.state,
            lease = com.romulus.mobile.downloads.queue.ClaimLease(
                claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
            ),
            attemptCount = row.attemptCount,
            pendingAction = row.pendingAction,
            reservation = row.reservation,
            finalOutputs = row.finalOutputs,
            finalizationCursor = row.finalizationCursor
        )
    }

    private fun queueJson(): Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    private fun testClock(): Clock =
        Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC)
}

private class MutableClock(private var currentInstant: Instant) : Clock() {
    override fun instant(): Instant = currentInstant

    override fun getZone(): java.time.ZoneId = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this

    fun advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}
