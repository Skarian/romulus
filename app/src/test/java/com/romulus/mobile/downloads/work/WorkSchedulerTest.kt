package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeProviderRuntimeGateway
import com.romulus.mobile.downloads.RecordingWorkerLauncher
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.queue.DownloadLedgerStore
import com.romulus.mobile.downloads.queue.FileDownloadLedgerStore
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.sampleQueueTaskInput
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkSchedulerTest {
    @Test
    fun readClaimLimitUsesPersistedConcurrency() = runTest {
        launcher.immediateLaunchCount = 0
        launcher.immediateLaunchAttempts = 0
        launcher.nextImmediateLaunchFailure = null
        launcher.scheduledInstants.clear()
        val store = createStore(testClock())
        val scheduler = createScheduler(store = store, maxConcurrency = 3)

        val claimLimit = scheduler.readClaimLimit()

        assertEquals(3, claimLimit)
    }

    @Test
    fun scheduleNextRetryWakeUsesEarliestPersistedDeadline() = runTest {
        launcher.immediateLaunchCount = 0
        launcher.immediateLaunchAttempts = 0
        launcher.nextImmediateLaunchFailure = null
        launcher.scheduledInstants.clear()
        val clock = testClock()
        val store = createStore(clock)
        val scheduler = createScheduler(store = store, maxConcurrency = 2, clock = clock)
        val queueService = createQueueService(store, scheduler, clock)
        val taskId = (queueService.enqueue(listOf(sampleQueueTaskInput("Retry.mkv")))
            as com.romulus.mobile.downloads.queue.EnqueueResult.Enqueued).taskIds.single()
        store.acknowledgeDispatchStart().getOrThrow()

        queueService.beginAttempt(taskId)
        queueService.scheduleRetry(
            taskId = taskId,
            retryAt = Instant.parse("2026-03-10T18:05:00Z"),
            attemptIndex = 1
        )

        assertEquals(
            listOf(Instant.parse("2026-03-10T18:05:00Z")),
            launcher.scheduledInstants
        )
    }

    @Test
    fun gateReflectsBrokenTokenReadiness() = runTest {
        launcher.immediateLaunchCount = 0
        launcher.immediateLaunchAttempts = 0
        launcher.nextImmediateLaunchFailure = null
        launcher.scheduledInstants.clear()
        val providerGateway = FakeProviderRuntimeGateway(
            initialReadiness = com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = false,
                brokenReason = "API key is invalid"
            )
        )
        val store = createStore(testClock())
        val scheduler = createScheduler(
            store = store,
            maxConcurrency = 1,
            providerGateway = providerGateway
        )

        val gate = scheduler.readGate()

        assertTrue(gate.authBlocked)
        assertEquals("API key is invalid", gate.reason)
        assertTrue(scheduler.observeGate().value.authBlocked)
    }

    @Test
    fun authRepairRequestsImmediateWake() = runTest {
        launcher.immediateLaunchCount = 0
        launcher.immediateLaunchAttempts = 0
        launcher.nextImmediateLaunchFailure = null
        launcher.scheduledInstants.clear()
        val providerGateway = FakeProviderRuntimeGateway(
            initialReadiness = com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = false,
                brokenReason = "API key is invalid"
            )
        )
        val store = createStore(testClock())
        createScheduler(
            store = store,
            maxConcurrency = 1,
            providerGateway = providerGateway
        )

        providerGateway.updateReadiness(
            com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = true,
                brokenReason = null
            )
        )

        withTimeout(2_000) {
            while (launcher.immediateLaunchCount == 0) {
                delay(10)
            }
        }

        assertEquals(1, launcher.immediateLaunchCount)
    }

    @Test
    fun authRepairKeepsDispatchPendingWhenImmediateWakeFails() = runTest {
        launcher.immediateLaunchCount = 0
        launcher.immediateLaunchAttempts = 0
        launcher.nextImmediateLaunchFailure = IllegalStateException("Auth wake failed")
        launcher.scheduledInstants.clear()
        val providerGateway = FakeProviderRuntimeGateway(
            initialReadiness = com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = false,
                brokenReason = "API key is invalid"
            )
        )
        val store = createStore(testClock())
        createScheduler(
            store = store,
            maxConcurrency = 1,
            providerGateway = providerGateway
        )

        providerGateway.updateReadiness(
            com.romulus.mobile.realdebrid.auth.TokenReadiness(
                isUsable = true,
                brokenReason = null
            )
        )

        withTimeout(2_000) {
            while (launcher.immediateLaunchAttempts == 0) {
                delay(10)
            }
        }

        assertEquals(0, launcher.immediateLaunchCount)
        assertTrue(store.hasPendingDispatch())
    }

    @Test
    fun scheduleNextDeferredWakeLaunchesPendingDispatchImmediately() = runTest {
        launcher.immediateLaunchCount = 0
        launcher.immediateLaunchAttempts = 0
        launcher.nextImmediateLaunchFailure = null
        launcher.scheduledInstants.clear()
        val clock = testClock()
        val store = createStore(clock)
        val scheduler = createScheduler(store = store, maxConcurrency = 2, clock = clock)
        val queueService = createQueueService(store, scheduler, clock)
        launcher.nextImmediateLaunchFailure = IllegalStateException("Immediate wake failed")

        val enqueueResult = queueService.enqueue(listOf(sampleQueueTaskInput("Retry.mkv")))

        assertTrue(
            enqueueResult is com.romulus.mobile.downloads.queue.EnqueueResult.EnqueuedPendingDispatch
        )
        assertTrue(store.hasPendingDispatch())

        val result = scheduler.scheduleNextDeferredWake()

        assertTrue(result.isSuccess)
        assertEquals(1, launcher.immediateLaunchCount)
        assertTrue(launcher.scheduledInstants.isEmpty())
    }

    private val launcher = RecordingWorkerLauncher()

    private fun createScheduler(
        store: DownloadLedgerStore,
        maxConcurrency: Int,
        clock: Clock = testClock(),
        providerGateway: FakeProviderRuntimeGateway = FakeProviderRuntimeGateway()
    ): WorkScheduler {
        val settingsService = kotlinx.coroutines.runBlocking {
            DownloadSettingsService.create(
                store = FakeDownloadSettingsStore().apply {
                    persistedState = DownloadSettingsState(
                        outputDirectoryUri = "content://downloads/tree",
                        maxConcurrency = maxConcurrency
                    )
                },
                outputAccess = FakeOutputDirectoryAccess(
                    usableUris = setOf("content://downloads/tree")
                )
            )
        }
        return WorkScheduler(
            settingsService = settingsService,
            ledgerStore = store,
            providerGateway = providerGateway,
            workerLauncher = launcher,
            clock = clock
        )
    }

    private fun createQueueService(
        store: DownloadLedgerStore,
        scheduler: WorkScheduler,
        clock: Clock
    ): QueueService = QueueService(
        ledgerStore = store,
        executionControlRegistry = ExecutionControlRegistry(),
        outputCleanupService = OutputCleanupService(
            outputFilesystem = com.romulus.mobile.downloads.FakeOutputFilesystem(
                createTempDirectory("scheduler-output").toFile()
            )
        ),
        workScheduler = scheduler,
        clock = clock
    )

    private fun createStore(clock: Clock): DownloadLedgerStore = FileDownloadLedgerStore(
        ledgerFile = createTempDirectory("scheduler-ledger").toFile().resolve("ledger.json"),
        json = queueJson(),
        clock = clock
    )

    private fun queueJson(): Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    private fun testClock(): Clock =
        Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC)
}
