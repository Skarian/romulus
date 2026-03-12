package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.output.OutputRootResolver
import com.romulus.mobile.downloads.output.ReservationId
import com.romulus.mobile.downloads.output.ReservedDirectOutput
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.FinalOutputId
import com.romulus.mobile.downloads.sampleQueueTask
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRecoveryPolicyTest {
    @Test
    fun interruptedResolvingRequeuesToFreshQueuedState() = runTest {
        val policy = createPolicy()

        val decision = policy.decide(
            QueueClaim(
                task = sampleQueueTask(),
                state = QueueTaskState.Resolving(Instant.parse("2026-03-10T18:00:00Z")),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = null,
                reservation = null,
                finalOutputs = emptyList()
            )
        )

        assertEquals(RecoveryDecision.Requeue(QueueTaskState.Queued), decision)
    }

    @Test
    fun interruptedRunningResumesWhenReservationIsIntact() = runTest {
        val policy = createPolicy()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 12,
            totalBytes = 24,
            lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
            tempFileToken = null,
            resumeByteOffset = 12
        )
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = createTempDirectory("recovery").toFile().resolve("artifact.part")
                .apply { writeBytes(ByteArray(12)) }
                .absolutePath,
            extractionRootPath = File(createTempDirectory("recovery-extract").toFile(), "extract")
                .absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("output"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = emptyList()
        )

        val decision = policy.decide(
            QueueClaim(
                task = sampleQueueTask(),
                state = QueueTaskState.Running(checkpoint),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = null,
                reservation = reservation,
                finalOutputs = emptyList()
            )
        )

        assertTrue(decision is RecoveryDecision.ResumeRunning)
        assertEquals(checkpoint, (decision as RecoveryDecision.ResumeRunning).checkpoint)
    }

    @Test
    fun interruptedRunningRepairsCheckpointToActualArtifactLength() = runTest {
        val policy = createPolicy()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 12,
            totalBytes = 24,
            lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
            tempFileToken = null,
            resumeByteOffset = 12
        )
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = createTempDirectory("recovery").toFile().resolve("artifact.part")
                .apply { writeBytes(ByteArray(16)) }
                .absolutePath,
            extractionRootPath = File(createTempDirectory("recovery-extract").toFile(), "extract")
                .absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("output"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = emptyList()
        )

        val decision = policy.decide(
            QueueClaim(
                task = sampleQueueTask(),
                state = QueueTaskState.Running(checkpoint),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = null,
                reservation = reservation,
                finalOutputs = emptyList()
            )
        )

        assertTrue(decision is RecoveryDecision.ResumeRunning)
        assertEquals(16, (decision as RecoveryDecision.ResumeRunning).checkpoint.resumeByteOffset)
        assertEquals(16, decision.checkpoint.downloadedBytes)
    }

    @Test
    fun interruptedRunningWithCompleteArtifactResumesFinalizationWithoutPersistedCursor() = runTest {
        val policy = createPolicy()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 12,
            totalBytes = 24,
            lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
            tempFileToken = null,
            resumeByteOffset = 12
        )
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = createTempDirectory("recovery").toFile().resolve("artifact.part")
                .apply { writeBytes(ByteArray(24)) }
                .absolutePath,
            extractionRootPath = File(createTempDirectory("recovery-extract").toFile(), "extract")
                .absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("output"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = emptyList()
        )

        val decision = policy.decide(
            QueueClaim(
                task = sampleQueueTask(),
                state = QueueTaskState.Running(checkpoint),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = null,
                reservation = reservation,
                finalOutputs = emptyList()
            )
        )

        assertTrue(decision is RecoveryDecision.ResumeFinalization)
        assertEquals(checkpoint, (decision as RecoveryDecision.ResumeFinalization).checkpoint)
        assertEquals(
            FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING),
            decision.cursor
        )
    }

    @Test
    fun interruptedRunningRequeuesWhenArtifactIsShorterThanCheckpoint() = runTest {
        val policy = createPolicy()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 12,
            totalBytes = 24,
            lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
            tempFileToken = null,
            resumeByteOffset = 12
        )
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = createTempDirectory("recovery").toFile().resolve("artifact.part")
                .apply { writeBytes(ByteArray(8)) }
                .absolutePath,
            extractionRootPath = File(createTempDirectory("recovery-extract").toFile(), "extract")
                .absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("output"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = emptyList()
        )

        val decision = policy.decide(
            QueueClaim(
                task = sampleQueueTask(),
                state = QueueTaskState.Running(checkpoint),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = null,
                reservation = reservation,
                finalOutputs = emptyList()
            )
        )

        assertEquals(RecoveryDecision.Requeue(QueueTaskState.Queued), decision)
    }

    @Test
    fun pausedRowWithResumeRequestResumesFromCheckpoint() = runTest {
        val policy = createPolicy()
        val task = sampleQueueTask()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 12,
            totalBytes = 24,
            lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
            tempFileToken = null,
            resumeByteOffset = 12
        )
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = createTempDirectory("recovery").toFile().resolve("artifact.part")
                .apply { writeBytes(ByteArray(12)) }
                .absolutePath,
            extractionRootPath = File(createTempDirectory("recovery-extract").toFile(), "extract")
                .absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("output"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = emptyList()
        )

        val decision = policy.decide(
            QueueClaim(
                task = task,
                state = QueueTaskState.Paused(checkpoint),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = QueueActionRequest(
                    taskId = task.taskId,
                    action = PendingQueueAction.RESUME,
                    requestedAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                reservation = reservation,
                finalOutputs = emptyList()
            )
        )

        assertTrue(decision is RecoveryDecision.ResumeRunning)
    }

    @Test
    fun pausedRowWithFinalizationCursorResumesFinalizationBeforeNetworkTransfer() = runTest {
        val policy = createPolicy()
        val task = sampleQueueTask()
        val checkpoint = TransferCheckpoint(
            downloadedBytes = 12,
            totalBytes = 24,
            lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
            tempFileToken = null,
            resumeByteOffset = 12
        )
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = createTempDirectory("recovery").toFile().resolve("artifact.part")
                .apply { writeBytes(ByteArray(12)) }
                .absolutePath,
            extractionRootPath = File(createTempDirectory("recovery-extract").toFile(), "extract")
                .absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("output"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = emptyList()
        )

        val decision = policy.decide(
            QueueClaim(
                task = task,
                state = QueueTaskState.Paused(checkpoint),
                lease = ClaimLease(
                    claimedAt = Instant.parse("2026-03-10T18:00:00Z"),
                    leaseExpiresAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                attemptCount = 1,
                pendingAction = QueueActionRequest(
                    taskId = task.taskId,
                    action = PendingQueueAction.RESUME,
                    requestedAt = Instant.parse("2026-03-10T18:01:00Z")
                ),
                reservation = reservation,
                finalOutputs = emptyList(),
                finalizationCursor = FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING)
            )
        )

        assertTrue(decision is RecoveryDecision.ResumeFinalization)
        assertEquals(checkpoint, (decision as RecoveryDecision.ResumeFinalization).checkpoint)
        assertEquals(
            FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING),
            decision.cursor
        )
    }

    private suspend fun createPolicy(): QueueRecoveryPolicy {
        val settingsService = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 1
                )
            },
            outputAccess = FakeOutputDirectoryAccess(usableUris = setOf("content://downloads/tree"))
        )
        return QueueRecoveryPolicy(
            outputReservationService = OutputReservationService(
                outputFilesystem = FakeOutputFilesystem(createTempDirectory("output-root").toFile()),
                outputRootResolver = OutputRootResolver(
                    settingsService = settingsService,
                    clock = testClock()
                ),
                artifactRoot = createTempDirectory("artifacts").toFile()
            )
        )
    }

    private fun testClock(): Clock =
        Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC)
}
