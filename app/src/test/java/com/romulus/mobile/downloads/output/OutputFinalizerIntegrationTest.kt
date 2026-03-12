package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.FakeArchiveExtraction
import com.romulus.mobile.downloads.FakeArchiveExtractionEntry
import com.romulus.mobile.downloads.FakeArchiveRuntime
import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.renameIntent
import com.romulus.mobile.downloads.sampleQueueTask
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputFinalizerIntegrationTest {
    @Test
    fun recursiveUnarchiveReservesPerPassAndPromotesFinalOutputs() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(rootDirectory)
        val reservationService = createReservationService(rootDirectory, outputFilesystem)
        val task = sampleQueueTask(
            name = "Outer.zip",
            namingIntent = renameIntent(
                pattern = "^(.*)\\.(.*)$",
                replacement = "$1-renamed.$2"
            ),
            unarchiveIntent = true,
            recursiveUnarchiveIntent = true
        )
        val reservation = reservationService.reserve(task).getOrThrow()
        val archiveRuntime = FakeArchiveRuntime().apply {
            inspections["Outer.zip"] = listOf(
                ArchiveRuntimeEntry(
                    rawEntryPath = "folder/video.mkv",
                    isArchiveCandidate = false
                ),
                ArchiveRuntimeEntry(
                    rawEntryPath = "folder/inner.zip",
                    isArchiveCandidate = true
                )
            )
            extractions["Outer.zip"] = FakeArchiveExtraction(
                entries = listOf(
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "folder/video.mkv",
                        bytes = "video".encodeToByteArray(),
                        isArchiveCandidate = false
                    ),
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "folder/inner.zip",
                        bytes = "inner-archive".encodeToByteArray(),
                        isArchiveCandidate = true
                    )
                )
            )
            inspections["inner.zip"] = listOf(
                ArchiveRuntimeEntry(
                    rawEntryPath = "deep/subtitle.srt",
                    isArchiveCandidate = false
                )
            )
            extractions["inner.zip"] = FakeArchiveExtraction(
                entries = listOf(
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "deep/subtitle.srt",
                        bytes = "subtitle".encodeToByteArray(),
                        isArchiveCandidate = false
                    )
                )
            )
        }
        val finalizer = OutputFinalizer(
            reservationService = reservationService,
            extractionController = ArchiveExtractionController(
                archiveRuntime = archiveRuntime,
                outputFilesystem = outputFilesystem
            ),
            outputFilesystem = outputFilesystem
        )
        val artifact = rootDirectory.resolve("Outer.zip").apply {
            writeBytes("outer-archive".encodeToByteArray())
        }
        val persistedReservations = mutableListOf<OutputReservation>()
        val persistedOutputs = mutableListOf<List<FinalOutputRecord>>()

        val result = finalizer.finalize(
            task = task,
            reservation = reservation,
            artifactPath = artifact.absolutePath,
            existingOutputs = emptyList(),
            initialCursor = null,
            persistence = OutputFinalizationPersistence(
                onReservationUpdated = { updated ->
                    persistedReservations += updated
                    Result.success(Unit)
                },
                onOutputsUpdated = { outputs ->
                    persistedOutputs += outputs
                    Result.success(Unit)
                },
                onCursorUpdated = { Result.success(Unit) }
            ),
            control = NoOpFinalizationControl
        ).getOrThrow()
        result as OutputFinalizationOutcome.Completed

        assertEquals(2, result.result.outputs.size)
        assertEquals(
            setOf("shows/video-renamed.mkv", "shows/subtitle-renamed.srt"),
            outputFilesystem.writtenOutputs.keys
        )
        assertEquals(2, persistedReservations.size)
        assertEquals(1, persistedReservations[0].extractionPlan.size)
        assertEquals(2, persistedReservations[1].extractionPlan.size)
        assertEquals(2, persistedOutputs.last().size)
        assertFalse(artifact.exists())
        assertFalse(java.io.File(result.result.reservation.extractionRootPath).exists())
    }

    @Test
    fun failedLaterRecursivePassKeepsEarlierOutputsPersisted() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(rootDirectory)
        val reservationService = createReservationService(rootDirectory, outputFilesystem)
        val task = sampleQueueTask(
            name = "Outer.zip",
            unarchiveIntent = true,
            recursiveUnarchiveIntent = true
        )
        val reservation = reservationService.reserve(task).getOrThrow()
        val archiveRuntime = FakeArchiveRuntime().apply {
            inspections["Outer.zip"] = listOf(
                ArchiveRuntimeEntry(
                    rawEntryPath = "folder/video.mkv",
                    isArchiveCandidate = false
                ),
                ArchiveRuntimeEntry(
                    rawEntryPath = "folder/inner.zip",
                    isArchiveCandidate = true
                )
            )
            extractions["Outer.zip"] = FakeArchiveExtraction(
                entries = listOf(
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "folder/video.mkv",
                        bytes = "video".encodeToByteArray(),
                        isArchiveCandidate = false
                    ),
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "folder/inner.zip",
                        bytes = "inner-archive".encodeToByteArray(),
                        isArchiveCandidate = true
                    )
                )
            )
            inspections["inner.zip"] = listOf(
                ArchiveRuntimeEntry(
                    rawEntryPath = "deep/broken.srt",
                    isArchiveCandidate = false
                )
            )
            extractions["inner.zip"] = FakeArchiveExtraction(
                entries = listOf(
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "deep/broken.srt",
                        bytes = "subtitle".encodeToByteArray(),
                        isArchiveCandidate = false
                    )
                ),
                failureMessage = "broken nested archive"
            )
        }
        val finalizer = OutputFinalizer(
            reservationService = reservationService,
            extractionController = ArchiveExtractionController(
                archiveRuntime = archiveRuntime,
                outputFilesystem = outputFilesystem
            ),
            outputFilesystem = outputFilesystem
        )
        val artifact = rootDirectory.resolve("Outer.zip").apply {
            writeBytes("outer-archive".encodeToByteArray())
        }
        val persistedOutputs = mutableListOf<List<FinalOutputRecord>>()

        val result = finalizer.finalize(
            task = task,
            reservation = reservation,
            artifactPath = artifact.absolutePath,
            existingOutputs = emptyList(),
            initialCursor = null,
            persistence = OutputFinalizationPersistence(
                onReservationUpdated = { Result.success(Unit) },
                onOutputsUpdated = { outputs ->
                    persistedOutputs += outputs
                    Result.success(Unit)
                },
                onCursorUpdated = { Result.success(Unit) }
            ),
            control = NoOpFinalizationControl
        )

        assertTrue(result.isFailure)
        assertEquals("video.mkv", persistedOutputs.first().single().displayName)
        assertEquals(2, persistedOutputs.last().size)
        assertEquals(
            setOf("shows/video.mkv", "shows/broken.srt"),
            outputFilesystem.writtenOutputs.keys
        )
    }

    @Test
    fun failedOutputPersistenceRollsBackCurrentPassPromotions() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(rootDirectory)
        val reservationService = createReservationService(rootDirectory, outputFilesystem)
        val task = sampleQueueTask(
            name = "Outer.zip",
            unarchiveIntent = true,
            recursiveUnarchiveIntent = false
        )
        val reservation = reservationService.reserve(task).getOrThrow()
        val archiveRuntime = FakeArchiveRuntime().apply {
            inspections["Outer.zip"] = listOf(
                ArchiveRuntimeEntry(
                    rawEntryPath = "folder/video.mkv",
                    isArchiveCandidate = false
                )
            )
            extractions["Outer.zip"] = FakeArchiveExtraction(
                entries = listOf(
                    FakeArchiveExtractionEntry(
                        rawEntryPath = "folder/video.mkv",
                        bytes = "video".encodeToByteArray(),
                        isArchiveCandidate = false
                    )
                )
            )
        }
        val finalizer = OutputFinalizer(
            reservationService = reservationService,
            extractionController = ArchiveExtractionController(
                archiveRuntime = archiveRuntime,
                outputFilesystem = outputFilesystem
            ),
            outputFilesystem = outputFilesystem
        )
        val artifact = rootDirectory.resolve("Outer.zip").apply {
            writeBytes("outer-archive".encodeToByteArray())
        }

        val result = finalizer.finalize(
            task = task,
            reservation = reservation,
            artifactPath = artifact.absolutePath,
            existingOutputs = emptyList(),
            initialCursor = null,
            persistence = OutputFinalizationPersistence(
                onReservationUpdated = { Result.success(Unit) },
                onOutputsUpdated = {
                    Result.failure(IllegalStateException("Output persistence failed"))
                },
                onCursorUpdated = { Result.success(Unit) }
            ),
            control = NoOpFinalizationControl
        )

        assertTrue(result.isFailure)
        assertTrue(outputFilesystem.writtenOutputs.isEmpty())
    }

    private suspend fun createReservationService(
        rootDirectory: java.io.File,
        outputFilesystem: FakeOutputFilesystem
    ): OutputReservationService {
        return OutputReservationService(
            outputFilesystem = outputFilesystem,
            outputRootResolver = OutputRootResolver(
                settingsService = DownloadSettingsService.create(
                    store = FakeDownloadSettingsStore().apply {
                        persistedState = DownloadSettingsState(
                            outputDirectoryUri = "content://downloads/tree",
                            maxConcurrency = 1
                        )
                    },
                    outputAccess = FakeOutputDirectoryAccess(
                        usableUris = setOf("content://downloads/tree")
                    )
                ),
                clock = testClock()
            ),
            artifactRoot = rootDirectory.resolve("runtime")
        )
    }

    private fun testClock(): Clock =
        Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC)
}

private object NoOpFinalizationControl : FinalizationControl {
    override fun currentSignal(): com.romulus.mobile.downloads.queue.ControlSignal =
        com.romulus.mobile.downloads.queue.ControlSignal.NONE

    override fun pulse(): Result<Unit> = Result.success(Unit)
}
