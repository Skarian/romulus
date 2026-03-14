package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.remotezip.ArchiveEntryDescriptor
import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.remotezip.EnumeratedRemoteZip
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.snapshot.SourceTorrentRef
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveContainerPreparationServiceTest {
    @Test
    fun preparingRevisitResumesExistingAcquisitionInsteadOfStartingAgain() = runTest {
        val store = InMemoryArchivePreparationStateStore()
        val exactMatch = exactMatch()
        var startCalls = 0
        var resumeCalls = 0
        val service = ArchiveContainerPreparationService(
            stateStore = store,
            findExactZipMatch = { Result.success(exactMatch) },
            startArchivePreparation = {
                startCalls += 1
                Result.success(
                    AcquisitionStatus.Waiting(
                        statusLabel = "downloading",
                        progressPercent = 25.0,
                        resumeMarker = ProviderResumeMarker(
                            torrentId = "torrent-1",
                            sourceMagnetUri = exactMatch.locator.sourceMagnetUri,
                            selectedProviderFileIds = listOf("22")
                        )
                    )
                )
            },
            resumeArchivePreparation = {
                resumeCalls += 1
                Result.success(
                    AcquisitionStatus.LinksReady(
                        resumeMarker = it,
                        readyLinks = listOf(ProviderReadyLink("https://restricted/archive"))
                    )
                )
            },
            materializeArchiveContainer = { _, ready ->
                Result.success(
                    ArchiveContainerLocator(
                        archiveUrl = "https://download/archive.zip",
                        originalName = "archive.zip",
                        providerLocator = exactMatch.locator.copy(
                            torrentId = ready.resumeMarker.torrentId
                        )
                    )
                )
            },
            enumerateRemoteZip = {
                Result.success(
                    EnumeratedRemoteZip(
                        entries = listOf(
                            ArchiveEntryDescriptor(
                                identity = ArchiveEntryIdentity(
                                    localHeaderOffset = 1L,
                                    compressedSize = 10L,
                                    uncompressedSize = 10L,
                                    crc32 = 123L,
                                    normalizedPath = "ROMs/Game.gba"
                                ),
                                entryPath = "ROMs/Game.gba",
                                sizeBytes = 10L
                            )
                        )
                    )
                )
            },
            clock = fixedClock()
        )

        val first = service.loadForBrowse(SNAPSHOT_ID, sourceEntry()).getOrThrow()
        val second = service.loadForBrowse(SNAPSHOT_ID, sourceEntry()).getOrThrow()

        assertTrue(first is ArchiveBrowseLoadResult.Preparing)
        assertTrue(second is ArchiveBrowseLoadResult.Ready)
        assertEquals(1, startCalls)
        assertEquals(1, resumeCalls)
    }

    @Test
    fun readyCacheReturnsWithoutRestartingPreparation() = runTest {
        val exactMatch = exactMatch()
        val store = InMemoryArchivePreparationStateStore().apply {
            state = ArchivePreparationCachedState.Ready(
                exactMatch = exactMatch,
                enteredAt = Instant.parse("2026-03-13T00:00:00Z"),
                timeoutAt = Instant.parse("2026-03-14T00:00:00Z"),
                resumeMarker = ProviderResumeMarker(
                    torrentId = "torrent-1",
                    sourceMagnetUri = exactMatch.locator.sourceMagnetUri,
                    selectedProviderFileIds = listOf("provider-zip")
                ),
                outerZip = ArchiveContainerLocator(
                    archiveUrl = "https://download/archive.zip",
                    originalName = "archive.zip",
                    providerLocator = exactMatch.locator
                ),
                entries = EnumeratedRemoteZip(emptyList())
            )
        }
        var startCalls = 0
        var resumeCalls = 0
        val service = ArchiveContainerPreparationService(
            stateStore = store,
            findExactZipMatch = { Result.failure(IllegalStateException("should not run")) },
            startArchivePreparation = {
                startCalls += 1
                Result.failure(IllegalStateException("should not run"))
            },
            resumeArchivePreparation = {
                resumeCalls += 1
                Result.failure(IllegalStateException("should not run"))
            },
            materializeArchiveContainer = { _, _ ->
                Result.failure(IllegalStateException("should not run"))
            },
            enumerateRemoteZip = { Result.failure(IllegalStateException("should not run")) },
            clock = fixedClock()
        )

        val result = service.loadForBrowse(SNAPSHOT_ID, sourceEntry()).getOrThrow()

        assertTrue(result is ArchiveBrowseLoadResult.Ready)
        assertEquals(0, startCalls)
        assertEquals(0, resumeCalls)
    }

    @Test
    fun resolveReadyContainerReusesSavedResumeMarkerWithoutRestartingPreparation() = runTest {
        val exactMatch = exactMatch()
        val preparationKey = ArchivePreparationKey(SNAPSHOT_ID, SourceEntryId("entry-1"))
        val store = InMemoryArchivePreparationStateStore().apply {
            state = ArchivePreparationCachedState.Ready(
                exactMatch = exactMatch,
                enteredAt = Instant.parse("2026-03-13T00:00:00Z"),
                timeoutAt = Instant.parse("2026-03-14T00:00:00Z"),
                resumeMarker = ProviderResumeMarker(
                    torrentId = "torrent-1",
                    sourceMagnetUri = exactMatch.locator.sourceMagnetUri,
                    selectedProviderFileIds = listOf("provider-zip")
                ),
                outerZip = ArchiveContainerLocator(
                    archiveUrl = "https://download/archive.zip",
                    originalName = "archive.zip",
                    providerLocator = exactMatch.locator.copy(torrentId = "torrent-1")
                ),
                entries = EnumeratedRemoteZip(emptyList())
            )
        }
        var startCalls = 0
        var resumeCalls = 0
        val service = ArchiveContainerPreparationService(
            stateStore = store,
            findExactZipMatch = { Result.failure(IllegalStateException("should not run")) },
            startArchivePreparation = {
                startCalls += 1
                Result.failure(IllegalStateException("should not run"))
            },
            resumeArchivePreparation = {
                resumeCalls += 1
                Result.success(
                    AcquisitionStatus.LinksReady(
                        resumeMarker = it,
                        readyLinks = listOf(ProviderReadyLink("https://restricted/archive"))
                    )
                )
            },
            materializeArchiveContainer = { _, ready ->
                Result.success(
                    ArchiveContainerLocator(
                        archiveUrl = "https://download/refreshed.zip",
                        originalName = "archive.zip",
                        providerLocator = exactMatch.locator.copy(
                            torrentId = ready.resumeMarker.torrentId
                        )
                    )
                )
            },
            enumerateRemoteZip = { Result.failure(IllegalStateException("should not run")) },
            clock = fixedClock()
        )

        val resolved = service.resolveReadyContainer(preparationKey).getOrThrow()

        assertEquals("https://download/refreshed.zip", resolved.archiveUrl)
        assertEquals(0, startCalls)
        assertEquals(1, resumeCalls)
    }

    private fun exactMatch(): ProviderFileRecord = ProviderFileRecord(
        providerFileId = "provider-zip",
        originalName = "archive.zip",
        path = "Show/archive.zip",
        sizeBytes = 300L,
        partLabel = "Disc 1",
        locator = ProviderLocator(
            sourceMagnetUri = "magnet:?xt=urn:btih:test",
            torrentId = "browse-torrent",
            providerFileIds = listOf("provider-zip"),
            selectedProviderFileId = "provider-zip",
            path = "Show/archive.zip",
            partLabel = "Disc 1"
        )
    )

    private fun sourceEntry(): SourceSnapshotEntry = SourceSnapshotEntry(
        entryId = SourceEntryId("entry-1"),
        displayName = "Archive source",
        subfolder = "roms",
        torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:test", "Disc 1")),
        normalizedPath = "/Show/archive.zip",
        ignoreGlobs = emptyList(),
        renameRule = null,
        unarchivePolicy = null
    )

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-03-13T00:00:00Z"),
        ZoneOffset.UTC
    )

    private class InMemoryArchivePreparationStateStore : ArchivePreparationStateStore {
        var state: ArchivePreparationCachedState? = null

        override suspend fun read(key: ArchivePreparationKey): ArchivePreparationCachedState? = state

        override suspend fun write(
            key: ArchivePreparationKey,
            state: ArchivePreparationCachedState
        ): Result<Unit> {
            this.state = state
            return Result.success(Unit)
        }

        override suspend fun clear(key: ArchivePreparationKey): Result<Unit> {
            state = null
            return Result.success(Unit)
        }
    }

    private companion object {
        val SNAPSHOT_ID = SnapshotId("snapshot-1")
    }
}
