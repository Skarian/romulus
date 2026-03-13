package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.attempts.AttemptOutcome
import com.romulus.mobile.downloads.ConfigurableDownloadLedgerStore
import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.FakeProviderRuntimeGateway
import com.romulus.mobile.downloads.RecordingWorkerLauncher
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.output.FinalOutputId
import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.output.ReservationId
import com.romulus.mobile.downloads.output.ReservedDirectOutput
import com.romulus.mobile.downloads.sampleQueueTaskInput
import com.romulus.mobile.downloads.work.ExecutionControlRegistry
import com.romulus.mobile.downloads.work.WorkScheduler
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueServiceTest {
    @Test
    fun enqueuePersistsRowsAcrossLedgerReload() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val result = service.enqueue(listOf(sampleQueueTaskInput("Alpha.mkv")))

        assertTrue(result is EnqueueResult.Enqueued)
        assertEquals(1, schedulerLauncher.immediateLaunchCount)
        val reloadedStore = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val rows = reloadedStore.readRows()

        assertEquals(1, rows.size)
        assertEquals("Alpha.mkv", rows.single().task.originalDisplayName)
        assertEquals(QueueTaskState.Queued, rows.single().state)
    }

    @Test
    fun enqueueReturnsPendingDispatchWhenImmediateWakeFails() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T18:10:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        schedulerLauncher.nextImmediateLaunchFailure =
            IllegalStateException("Immediate wake failed")
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val result = service.enqueue(listOf(sampleQueueTaskInput("Pending.mkv")))

        assertTrue(result is EnqueueResult.EnqueuedPendingDispatch)
        assertEquals("Immediate wake failed", (result as EnqueueResult.EnqueuedPendingDispatch).message)
        assertEquals(1, schedulerLauncher.immediateLaunchAttempts)
        assertEquals(0, schedulerLauncher.immediateLaunchCount)
        assertTrue(store.hasPendingDispatch())
        assertEquals(1, store.readRows().size)
    }

    @Test
    fun clearHistoryHidesTerminalRowsAndExcludesThemFromSummaryTotals() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T19:00:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Beta.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        service.performAction(QueueActionCommand.Cancel(taskId))
        val projector = QueueSummaryProjector(
            ledgerStore = store,
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

        val clearResult = service.clearHistory(includeFailed = false)

        assertTrue(clearResult.isSuccess)
        assertTrue(store.readRows(includeHidden = false).isEmpty())
        val hiddenRows = store.readRows(includeHidden = true)
        assertEquals(1, hiddenRows.size)
        assertEquals(QueueVisibility.HIDDEN, hiddenRows.single().visibility)
        assertEquals(0, projector.observeProjection().value.summary.total)
        assertEquals(0, projector.observeProjection().value.summary.cancelled)
        assertTrue(projector.observeProjection().value.rows.isEmpty())
    }

    @Test
    fun resumeLeavesRowPausedUntilTransferActuallyRestarts() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T19:30:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Gamma.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 10,
            totalBytes = 20,
            lastPersistedAt = Instant.parse("2026-03-10T19:25:00Z"),
            tempFileToken = null,
            resumeByteOffset = 10
        )
        store.persistState(taskId, QueueTaskState.Paused(checkpoint))

        val result = service.performAction(QueueActionCommand.Resume(taskId))

        assertTrue(result.isSuccess)
        val row = checkNotNull(store.readRow(taskId))
        assertEquals(QueueTaskState.Paused(checkpoint), row.state)
        assertEquals(PendingQueueAction.RESUME, row.pendingAction?.action)
    }

    @Test
    fun wakeGenerationAdvancesForExecutionMutations() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T19:45:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Wake.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        assertEquals(1L, store.readWakeGeneration())

        val checkpoint = TransferCheckpoint(
            downloadedBytes = 10,
            totalBytes = 20,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 10
        )
        store.persistState(taskId, QueueTaskState.Paused(checkpoint)).getOrThrow()
        service.performAction(QueueActionCommand.Resume(taskId)).getOrThrow()
        assertEquals(2L, store.readWakeGeneration())

        store.persistState(
            taskId,
            QueueTaskState.Failed(FailureReason.OutputFailure("retry me"))
        ).getOrThrow()
        service.performAction(QueueActionCommand.Retry(taskId)).getOrThrow()
        assertEquals(3L, store.readWakeGeneration())

        store.persistState(taskId, QueueTaskState.Completed).getOrThrow()
        service.performAction(QueueActionCommand.Restart(taskId)).getOrThrow()
        assertEquals(4L, store.readWakeGeneration())
    }

    @Test
    fun cancelDuringResolvingStaysLiveUntilWorkerAcknowledges() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T19:50:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val executionControlRegistry = ExecutionControlRegistry()
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = executionControlRegistry,
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Resolve.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        service.claimRunnableTasks(clock.instant(), limit = 1)
        service.beginAttempt(taskId).getOrThrow()
        val controlHandle = executionControlRegistry.register(taskId)

        val result = service.performAction(QueueActionCommand.Cancel(taskId))

        assertTrue(result.isSuccess)
        val row = checkNotNull(store.readRow(taskId))
        assertTrue(row.state is QueueTaskState.Resolving)
        assertEquals(PendingQueueAction.CANCEL, row.pendingAction?.action)
        assertEquals(ControlSignal.CANCEL, controlHandle.current())
    }

    @Test
    fun cancelAfterPauseRequestWinsWhenWorkerAcknowledgesStop() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T19:55:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val executionControlRegistry = ExecutionControlRegistry()
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = executionControlRegistry,
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Race.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        service.claimRunnableTasks(clock.instant(), limit = 1)
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 5,
            totalBytes = 10,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 5
        )
        service.recordRunning(taskId, checkpoint).getOrThrow()
        val controlHandle = executionControlRegistry.register(taskId)

        service.performAction(QueueActionCommand.Pause(taskId)).getOrThrow()
        service.performAction(QueueActionCommand.Cancel(taskId)).getOrThrow()

        val rowAfterCancel = checkNotNull(store.readRow(taskId))
        assertEquals(PendingQueueAction.CANCEL, rowAfterCancel.pendingAction?.action)
        assertEquals(ControlSignal.CANCEL, controlHandle.current())

        service.acknowledgePause(taskId, checkpoint).getOrThrow()

        val finalRow = checkNotNull(store.readRow(taskId))
        assertTrue(finalRow.state is QueueTaskState.Cancelled)
        assertEquals(null, finalRow.pendingAction)
    }

    @Test
    fun pendingCancelOverridesFailedOutcomeInsteadOfSchedulingRetry() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T19:57:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )

        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Cancel.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        service.claimRunnableTasks(clock.instant(), limit = 1)
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 5,
            totalBytes = 10,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 5
        )
        service.recordRunning(taskId, checkpoint).getOrThrow()
        service.performAction(QueueActionCommand.Cancel(taskId)).getOrThrow()

        service.settleAttemptOutcome(
            taskId = taskId,
            outcome = AttemptOutcome.Failed(
                reason = FailureReason.ProviderFailure(
                    stage = "Download",
                    message = "Transfer failed"
                )
            ),
            attemptIndex = 1
        ).getOrThrow()

        val row = checkNotNull(store.readRow(taskId))
        assertTrue(row.state is QueueTaskState.Cancelled)
        assertEquals(null, row.pendingAction)
    }

    @Test
    fun completeKeepsPromotedOutputsWhenLedgerWriteFails() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T20:00:00Z"), ZoneOffset.UTC)
        val delegateStore = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val store = ConfigurableDownloadLedgerStore(delegateStore).apply {
            completeTaskError = IllegalStateException("Completion write failed")
        }
        val outputRoot = createTempDirectory("queue-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(outputRoot)
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = scheduler,
            clock = clock
        )
        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Delta.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        val outputs = listOf(
            FinalOutputRecord(
                finalOutputId = FinalOutputId("direct"),
                relativePath = "shows/Delta.mkv",
                displayName = "Delta.mkv",
                sizeBytes = 4
            )
        )
        outputFilesystem.writtenOutputs["shows/Delta.mkv"] = "test".encodeToByteArray()
        outputRoot.resolve("shows/Delta.mkv").apply {
            parentFile?.mkdirs()
            writeBytes("test".encodeToByteArray())
        }

        val result = service.complete(taskId, outputs)

        assertTrue(result.isFailure)
        assertEquals(setOf("shows/Delta.mkv"), outputFilesystem.writtenOutputs.keys)
        assertTrue(File(outputRoot, "shows/Delta.mkv").exists())
    }

    @Test
    fun manualRetryClearsExistingReservationForFreshRebind() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T20:30:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )
        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Retry.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        store.persistReservation(
            taskId = taskId,
            reservation = OutputReservation(
                reservationId = ReservationId("reservation"),
                boundOutputDirectoryUri = "content://downloads/broken-tree",
                tempArtifactPath = "/tmp/retry.part",
                extractionRootPath = "/tmp/retry",
                directOutput = ReservedDirectOutput(
                    finalOutputId = FinalOutputId("direct"),
                    relativePath = "shows/Retry.mkv",
                    displayName = "Retry.mkv"
                ),
                extractionPlan = emptyList()
            )
        )
        store.persistState(
            taskId = taskId,
            state = QueueTaskState.Failed(
                FailureReason.DirectoryAccessFailure("Download directory is no longer accessible")
            )
        )

        val result = service.performAction(QueueActionCommand.Retry(taskId))

        assertTrue(result.isSuccess)
        assertEquals(null, checkNotNull(store.readRow(taskId)).reservation)
    }

    @Test
    fun manualRetryRetainsCleanupScopeSoLaterRestartCanSucceed() = runTest {
        val ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json")
        val clock = Clock.fixed(Instant.parse("2026-03-10T20:45:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = ledgerFile,
            json = queueJson(),
            clock = clock
        )
        val outputRoot = createTempDirectory("queue-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(outputRoot)
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(outputFilesystem),
            workScheduler = scheduler,
            clock = clock
        )
        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("History.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/old-tree",
            tempArtifactPath = outputRoot.resolve("artifacts/history.part").absolutePath,
            extractionRootPath = outputRoot.resolve("extract/history").absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("direct"),
                relativePath = "shows/History.mkv",
                displayName = "History.mkv"
            ),
            extractionPlan = emptyList()
        )
        val output = FinalOutputRecord(
            finalOutputId = FinalOutputId("direct"),
            relativePath = "shows/History.mkv",
            displayName = "History.mkv",
            sizeBytes = 7
        )
        File(reservation.tempArtifactPath).apply {
            parentFile?.mkdirs()
            writeBytes("partial".encodeToByteArray())
        }
        File(reservation.extractionRootPath).mkdirs()
        outputFilesystem.writtenOutputs[output.relativePath] = "history".encodeToByteArray()
        outputRoot.resolve(output.relativePath).apply {
            parentFile?.mkdirs()
            writeBytes("history".encodeToByteArray())
        }
        store.persistReservation(taskId, reservation).getOrThrow()
        store.persistFinalOutputs(taskId, listOf(output)).getOrThrow()
        store.persistState(
            taskId,
            QueueTaskState.Failed(FailureReason.OutputFailure("retry me"))
        ).getOrThrow()

        service.performAction(QueueActionCommand.Retry(taskId)).getOrThrow()

        val queuedRow = checkNotNull(store.readRow(taskId))
        assertEquals(QueueTaskState.Queued, queuedRow.state)
        assertEquals(null, queuedRow.reservation)
        assertTrue(queuedRow.finalOutputs.isEmpty())
        assertEquals(1, queuedRow.cleanupScopes.size)

        store.persistState(
            taskId,
            QueueTaskState.Failed(FailureReason.OutputFailure("still broken"))
        ).getOrThrow()

        val restartResult = service.performAction(QueueActionCommand.Restart(taskId))

        assertTrue(restartResult.isSuccess)
        val restartedRow = checkNotNull(store.readRow(taskId))
        assertEquals(QueueTaskState.Queued, restartedRow.state)
        assertTrue(restartedRow.cleanupScopes.isEmpty())
        assertTrue(!outputRoot.resolve(output.relativePath).exists())
        assertTrue(!File(reservation.tempArtifactPath).exists())
        assertTrue(!File(reservation.extractionRootPath).exists())
    }

    @Test
    fun activeStatePersistenceRenewsClaimLease() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-10T21:00:00Z"))
        val store = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )
        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Lease.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()

        service.claimRunnableTasks(clock.instant(), limit = 1)
        val initialLeaseExpiry = checkNotNull(store.readRow(taskId)?.lease).leaseExpiresAt

        clock.advanceBy(Duration.ofMinutes(10))
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 1,
            totalBytes = 2,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 1
        )
        service.recordRunning(taskId, checkpoint)
        val renewedLeaseExpiry = checkNotNull(store.readRow(taskId)?.lease).leaseExpiresAt

        assertTrue(renewedLeaseExpiry.isAfter(initialLeaseExpiry))
    }

    @Test
    fun enterFinalizationPersistsCheckpointAndCursorTogether() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T21:15:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )
        val enqueueResult = service.enqueue(listOf(sampleQueueTaskInput("Finalize.mkv")))
        val taskId = (enqueueResult as EnqueueResult.Enqueued).taskIds.single()
        service.claimRunnableTasks(clock.instant(), limit = 1)
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 20,
            totalBytes = 20,
            lastPersistedAt = clock.instant(),
            tempFileToken = null,
            resumeByteOffset = 20
        )
        val cursor = FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING)

        service.enterFinalization(taskId, checkpoint, cursor).getOrThrow()

        val row = checkNotNull(store.readRow(taskId))
        assertEquals(QueueTaskState.Running(checkpoint), row.state)
        assertEquals(cursor, row.finalizationCursor)
    }

    @Test
    fun freshReservationSeesAlreadyReservedQueuePaths() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T21:30:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("queue-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val scheduler = createScheduler(store, clock)
        val service = QueueService(
            ledgerStore = store,
            executionControlRegistry = ExecutionControlRegistry(),
            outputCleanupService = OutputCleanupService(
                FakeOutputFilesystem(createTempDirectory("queue-output").toFile())
            ),
            workScheduler = scheduler,
            clock = clock
        )
        val enqueueResult = service.enqueue(
            listOf(
                sampleQueueTaskInput("Episode.mkv"),
                sampleQueueTaskInput("Episode.mkv")
            )
        ) as EnqueueResult.Enqueued

        service.reserveFreshOutput(
            taskId = enqueueResult.taskIds[0],
            outputDirectoryUri = "content://downloads/tree",
            filesystemRelativePaths = emptySet()
        ) { occupied ->
            assertTrue(occupied.isEmpty())
            reservation(
                reservationId = "reservation-1",
                finalOutputId = "output-1",
                relativePath = "shows/Episode.mkv"
            )
        }.getOrThrow()

        val secondReservation = service.reserveFreshOutput(
            taskId = enqueueResult.taskIds[1],
            outputDirectoryUri = "content://downloads/tree",
            filesystemRelativePaths = emptySet()
        ) { occupied ->
            assertTrue("shows/Episode.mkv" in occupied)
            reservation(
                reservationId = "reservation-2",
                finalOutputId = "output-2",
                relativePath = "shows/Episode (1).mkv"
            )
        }.getOrThrow()

        assertEquals("shows/Episode (1).mkv", secondReservation.directOutput?.relativePath)
    }
}

private val schedulerLauncher = RecordingWorkerLauncher()

private fun queueJson(): Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "kind"
}

private fun reservation(
    reservationId: String,
    finalOutputId: String,
    relativePath: String
) = OutputReservation(
    reservationId = ReservationId(reservationId),
    boundOutputDirectoryUri = "content://downloads/tree",
    tempArtifactPath = "/tmp/$reservationId.part",
    extractionRootPath = "/tmp/$reservationId",
    directOutput = ReservedDirectOutput(
        finalOutputId = FinalOutputId(finalOutputId),
        relativePath = relativePath,
        displayName = relativePath.substringAfterLast('/')
    ),
    extractionPlan = emptyList()
)

private class MutableClock(private var current: Instant) : Clock() {
    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?) = this

    override fun instant(): Instant = current

    fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }
}

private suspend fun createScheduler(
    store: DownloadLedgerStore,
    clock: Clock
): WorkScheduler {
    schedulerLauncher.immediateLaunchCount = 0
    schedulerLauncher.immediateLaunchAttempts = 0
    schedulerLauncher.nextImmediateLaunchFailure = null
    schedulerLauncher.scheduledInstants.clear()
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
    return WorkScheduler(
        settingsService = settingsService,
        ledgerStore = store,
        providerGateway = FakeProviderRuntimeGateway(),
        workerLauncher = schedulerLauncher,
        clock = clock
    )
}
