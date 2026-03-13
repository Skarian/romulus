package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.FakeDownloadSettingsStore
import com.romulus.mobile.downloads.FakeOutputDirectoryAccess
import com.romulus.mobile.downloads.FakeOutputFilesystem
import com.romulus.mobile.downloads.renameIntent
import com.romulus.mobile.downloads.sampleQueueTask
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.queue.TransferCheckpoint
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputReservationServiceTest {
    @Test
    fun reserveAppliesCollisionSuffixWithinOutputSubfolder() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(rootDirectory).also { filesystem ->
            filesystem.writtenOutputs["shows/Episode.mkv"] = byteArrayOf(1)
        }
        val store = FakeDownloadSettingsStore().apply {
            persistedState = DownloadSettingsState(
                outputDirectoryUri = "content://downloads/tree",
                maxConcurrency = 2
            )
        }
        val service = OutputReservationService(
            outputFilesystem = outputFilesystem,
            outputRootResolver = OutputRootResolver(
                settingsService = DownloadSettingsService.create(
                    store = store,
                    outputAccess = FakeOutputDirectoryAccess(
                        usableUris = setOf("content://downloads/tree")
                    )
                ),
                clock = testClock()
            ),
            artifactRoot = rootDirectory.resolve("runtime")
        )

        val reservation = service.reserve(sampleQueueTask(name = "Episode.mkv")).getOrThrow()

        assertEquals("shows/Episode (1).mkv", reservation.directOutput?.relativePath)
        assertEquals("Episode (1).mkv", reservation.directOutput?.displayName)
    }

    @Test
    fun reserveAvoidsPathsAlreadyReservedByOtherRows() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val service = OutputReservationService(
            outputFilesystem = FakeOutputFilesystem(rootDirectory),
            outputRootResolver = OutputRootResolver(
                settingsService = DownloadSettingsService.create(
                    store = FakeDownloadSettingsStore().apply {
                        persistedState = DownloadSettingsState(
                            outputDirectoryUri = "content://downloads/tree",
                            maxConcurrency = 2
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

        val reservation = service.reserve(
            task = sampleQueueTask(name = "Episode.mkv"),
            occupiedRelativePathsProvider = { setOf("shows/Episode.mkv") }
        ).getOrThrow()

        assertEquals("shows/Episode (1).mkv", reservation.directOutput?.relativePath)
    }

    @Test
    fun reserveKeepsArchiveExtensionOnTempArtifactForLocalUnarchive() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(rootDirectory)
        val service = OutputReservationService(
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

        val reservation = service.reserve(
            sampleQueueTask(
                name = "Archive.zip",
                unarchiveIntent = true,
                recursiveUnarchiveIntent = false
            )
        ).getOrThrow()

        assertEquals("zip", reservation.tempArtifactPath.substringAfterLast('.'))
        assertTrue(reservation.tempArtifactPath.contains(".part."))
    }

    @Test
    fun inspectRecoveryDispositionUsesPersistedArtifactLengthForTransferResume() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val service = OutputReservationService(
            outputFilesystem = FakeOutputFilesystem(rootDirectory),
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
        val reservation = service.reserve(sampleQueueTask(name = "Resume.mkv")).getOrThrow()
        val tempArtifact = java.io.File(reservation.tempArtifactPath)
        tempArtifact.parentFile?.mkdirs()
        tempArtifact.writeBytes(ByteArray(16))

        val disposition = service.inspectRecoveryDisposition(
            reservation = reservation,
            checkpoint = TransferCheckpoint(
                downloadedBytes = 16,
                totalBytes = 32,
                lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
                tempFileToken = null,
                resumeByteOffset = 8
            ),
            persistedCursor = null
        )

        assertTrue(disposition is ArtifactRecoveryDisposition.ResumeTransfer)
        assertEquals(16L, (disposition as ArtifactRecoveryDisposition.ResumeTransfer).safeResumeOffset)
    }

    @Test
    fun inspectRecoveryDispositionPromotesCompleteArtifactToFinalizationResume() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val service = OutputReservationService(
            outputFilesystem = FakeOutputFilesystem(rootDirectory),
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
        val reservation = service.reserve(sampleQueueTask(name = "Complete.mkv")).getOrThrow()
        val tempArtifact = java.io.File(reservation.tempArtifactPath)
        tempArtifact.parentFile?.mkdirs()
        tempArtifact.writeBytes(ByteArray(32))

        val disposition = service.inspectRecoveryDisposition(
            reservation = reservation,
            checkpoint = TransferCheckpoint(
                downloadedBytes = 16,
                totalBytes = 32,
                lastPersistedAt = Instant.parse("2026-03-10T18:00:00Z"),
                tempFileToken = null,
                resumeByteOffset = 16
            ),
            persistedCursor = null
        )

        assertTrue(disposition is ArtifactRecoveryDisposition.ResumeFinalization)
        assertEquals(
            createInitialFinalizationCursor(reservation),
            (disposition as ArtifactRecoveryDisposition.ResumeFinalization).cursor
        )
    }

    @Test
    fun expandExtractionPlanAppendsReservationsAndSkipsRenameForArchiveOutputs() = runTest {
        val rootDirectory = createTempDirectory("downloads-output").toFile()
        val outputFilesystem = FakeOutputFilesystem(rootDirectory)
        val service = OutputReservationService(
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
        val task = sampleQueueTask(
            name = "Archive.zip",
            namingIntent = renameIntent(
                pattern = "^(.*)\\.(.*)$",
                replacement = "$1-renamed.$2"
            ),
            unarchiveIntent = true,
            recursiveUnarchiveIntent = true
        )
        val reservation = service.reserve(task).getOrThrow()

        val firstExpansion = service.expandExtractionPlan(
            task = task,
            reservation = reservation,
            extractedEntries = listOf(
                ExtractionManifestEntry(
                    archiveEntryPath = "outer/video.mkv",
                    renameEligible = true
                )
            )
        ).getOrThrow()
        val secondExpansion = service.expandExtractionPlan(
            task = task,
            reservation = firstExpansion,
            extractedEntries = listOf(
                ExtractionManifestEntry(
                    archiveEntryPath = "outer/inner.zip",
                    renameEligible = false
                )
            )
        ).getOrThrow()

        assertEquals(2, secondExpansion.extractionPlan.size)
        assertEquals("video-renamed.mkv", secondExpansion.extractionPlan[0].displayName)
        assertEquals("inner.zip", secondExpansion.extractionPlan[1].displayName)
    }

    private fun testClock(): Clock =
        Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC)
}
