package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.FakeOutputFilesystem
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputCleanupServiceTest {
    @Test
    fun cleanupDeletesReservedButNotYetRecordedOutputs() = runTest {
        val root = createTempDirectory("cleanup-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(root)
        val service = OutputCleanupService(outputFilesystem)
        val reservation = OutputReservation(
            reservationId = ReservationId("reservation"),
            boundOutputDirectoryUri = "content://downloads/tree",
            tempArtifactPath = root.resolve("artifacts/file.part").absolutePath,
            extractionRootPath = root.resolve("extract/runtime").absolutePath,
            directOutput = ReservedDirectOutput(
                finalOutputId = FinalOutputId("direct"),
                relativePath = "shows/Episode.mkv",
                displayName = "Episode.mkv"
            ),
            extractionPlan = listOf(
                ReservedExtractionOutput(
                    finalOutputId = FinalOutputId("nested"),
                    archiveEntryPath = "folder/subtitle.srt",
                    relativePath = "shows/subtitle.srt",
                    displayName = "subtitle.srt"
                )
            )
        )
        File(reservation.tempArtifactPath).apply {
            parentFile?.mkdirs()
            writeBytes("partial".encodeToByteArray())
        }
        File(reservation.extractionRootPath).mkdirs()
        outputFilesystem.writtenOutputs["shows/Episode.mkv"] = "video".encodeToByteArray()
        outputFilesystem.writtenOutputs["shows/subtitle.srt"] = "subtitle".encodeToByteArray()
        root.resolve("shows/Episode.mkv").apply {
            parentFile?.mkdirs()
            writeBytes("video".encodeToByteArray())
        }
        root.resolve("shows/subtitle.srt").writeBytes("subtitle".encodeToByteArray())

        val result = service.cleanupForRestart(
            listOf(
                OutputCleanupScope(
                    reservation = reservation,
                    finalOutputs = emptyList()
                )
            )
        )

        assertTrue(result.isSuccess)
        assertFalse(root.resolve("shows/Episode.mkv").exists())
        assertFalse(root.resolve("shows/subtitle.srt").exists())
        assertFalse(File(reservation.tempArtifactPath).exists())
        assertFalse(File(reservation.extractionRootPath).exists())
    }
}
