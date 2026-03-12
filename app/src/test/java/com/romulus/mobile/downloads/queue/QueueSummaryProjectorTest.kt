package com.romulus.mobile.downloads.queue

import com.romulus.mobile.downloads.output.FinalOutputId
import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.output.OutputReservation
import com.romulus.mobile.downloads.output.ReservationId
import com.romulus.mobile.downloads.output.ReservedDirectOutput
import com.romulus.mobile.downloads.output.ReservedExtractionOutput
import com.romulus.mobile.downloads.sampleQueueTask
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSummaryProjectorTest {
    @Test
    fun supportedArchivesUseManifestPreviewButUnsupportedFilesKeepDirectSaveName() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T21:00:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("projection-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val archiveTask = sampleQueueTask(
            taskId = TaskId("archive-task"),
            createdAt = Instant.parse("2026-03-10T21:00:00Z"),
            name = "Archive.zip",
            unarchiveIntent = true
        )
        val normalTask = sampleQueueTask(
            taskId = TaskId("normal-task"),
            createdAt = Instant.parse("2026-03-10T21:01:00Z"),
            name = "Episode.mkv",
            unarchiveIntent = true
        )
        store.insertTasks(listOf(archiveTask, normalTask)).getOrThrow()
        store.persistReservation(
            archiveTask.taskId,
            extractionReservationFor(
                displayNames = listOf("Alpha.mkv", "Beta.srt", "Gamma.txt")
            )
        ).getOrThrow()
        store.persistReservation(
            normalTask.taskId,
            directReservationFor(displayName = "Episode.mkv")
        ).getOrThrow()
        val projector = QueueSummaryProjector(
            ledgerStore = store,
            dispatcher = Dispatchers.Unconfined
        )

        val summariesByTaskId = projector.observeProjection().value.rows.associate { row ->
            row.taskId to row.details.outputSummary
        }

        assertEquals(
            "Flattened extraction (3 files): Alpha.mkv, Beta.srt +1 more",
            summariesByTaskId[archiveTask.taskId]
        )
        assertEquals("Episode.mkv", summariesByTaskId[normalTask.taskId])
    }

    @Test
    fun completedSingleFileExtractionRetainsExtractionContext() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-03-10T21:05:00Z"), ZoneOffset.UTC)
        val store = FileDownloadLedgerStore(
            ledgerFile = createTempDirectory("projection-ledger").toFile().resolve("ledger.json"),
            json = queueJson(),
            clock = clock
        )
        val archiveTask = sampleQueueTask(
            taskId = TaskId("archive-task"),
            createdAt = Instant.parse("2026-03-10T21:05:00Z"),
            name = "Archive.zip",
            unarchiveIntent = true
        )
        store.insertTasks(listOf(archiveTask)).getOrThrow()
        store.persistFinalOutputs(
            archiveTask.taskId,
            listOf(
                FinalOutputRecord(
                    finalOutputId = FinalOutputId("output-episode"),
                    relativePath = "shows/Episode.mkv",
                    displayName = "Episode.mkv",
                    sizeBytes = 42
                )
            )
        ).getOrThrow()
        val projector = QueueSummaryProjector(
            ledgerStore = store,
            dispatcher = Dispatchers.Unconfined
        )

        val summary = projector.observeProjection().value.rows.single().details.outputSummary

        assertEquals("Flattened extraction (1 file): Episode.mkv", summary)
    }

    private fun directReservationFor(displayName: String): OutputReservation = OutputReservation(
        reservationId = ReservationId("reservation-$displayName"),
        boundOutputDirectoryUri = "content://downloads/tree",
        tempArtifactPath = "/tmp/$displayName.part",
        extractionRootPath = "/tmp/$displayName-extract",
        directOutput = ReservedDirectOutput(
            finalOutputId = FinalOutputId("output-$displayName"),
            relativePath = "shows/$displayName",
            displayName = displayName
        ),
        extractionPlan = emptyList()
    )

    private fun extractionReservationFor(displayNames: List<String>): OutputReservation =
        OutputReservation(
            reservationId = ReservationId("reservation-extraction"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = "/tmp/archive.zip.part",
            extractionRootPath = "/tmp/archive.zip-extract",
            directOutput = null,
            extractionPlan = displayNames.mapIndexed { index, displayName ->
                ReservedExtractionOutput(
                    finalOutputId = FinalOutputId("output-$index"),
                    archiveEntryPath = "entry-$index",
                    relativePath = "shows/$displayName",
                    displayName = displayName
                )
            }
        )

    private fun queueJson(): Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }
}
