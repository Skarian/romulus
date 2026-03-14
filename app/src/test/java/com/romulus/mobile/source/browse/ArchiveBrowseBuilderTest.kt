package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.remotezip.ArchiveEntryDescriptor
import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.remotezip.EnumeratedRemoteZip
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.ExtractionLayoutMode
import com.romulus.mobile.source.snapshot.ExtractionLayoutPolicy
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.snapshot.SourceTorrentRef
import com.romulus.mobile.source.snapshot.UnarchivePolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBrowseBuilderTest {
    @Test
    fun resolvesExactZipEnumeratesEntriesAndAppliesIgnoreRules() = runTest {
        val builder = ArchiveBrowseBuilder(
            loadArchiveBrowse = { _, _ ->
                Result.success(
                    ArchiveBrowseLoadResult.Ready(
                        outerZip = ArchiveContainerLocator(
                            archiveUrl = "https://download.example/archive.zip",
                            originalName = "archive.zip",
                            providerLocator = ProviderLocator(
                                sourceMagnetUri = "magnet:?xt=urn:btih:test",
                                torrentId = "torrent-1",
                                providerFileIds = listOf("provider-zip"),
                                selectedProviderFileId = "provider-zip",
                                path = "Show/archive.zip",
                                partLabel = "Disc 1"
                            )
                        ),
                        entries = EnumeratedRemoteZip(
                            entries = listOf(
                                descriptor("folder/Beta.bin", 40L, 2L),
                                descriptor("folder/Alpha.pdf", 20L, 1L),
                                descriptor("folder/Alpha.bin", 10L, 3L)
                            )
                        )
                    )
                )
            }
        )

        val result = builder.build(
            snapshotId = SnapshotId("snapshot-1"),
            entry = SourceSnapshotEntry(
                entryId = SourceEntryId("entry-1"),
                displayName = "Archive source",
                subfolder = "roms",
                torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:test", "Disc 1")),
                normalizedPath = "/Show/archive.zip",
                ignoreGlobs = listOf("*.pdf"),
                renameRule = RenameRule("(.*)", "$1"),
                unarchivePolicy = UnarchivePolicy(
                    recursiveDefault = false,
                    layout = ExtractionLayoutPolicy(mode = ExtractionLayoutMode.FLAT)
                )
            )
        ) as BrowseResult.Loaded

        assertEquals(BrowseMode.ARCHIVE_SELECTION, result.mode)
        assertEquals(
            listOf("Alpha.bin", "Beta.bin"),
            result.items.map(SelectableItem::originalDisplayName)
        )
        val archiveItems = result.items.map { it as SelectableItem.ArchiveEntry }
        assertTrue(archiveItems.all { it.sourceContext.providerFileId == "provider-zip" })
    }

    @Test
    fun exactZipResolutionFailureStaysInArchiveResolverState() = runTest {
        val builder = ArchiveBrowseBuilder(
            loadArchiveBrowse = { _, _ -> Result.failure(IllegalStateException("zip not found")) }
        )

        val result = builder.build(
            snapshotId = SnapshotId("snapshot-1"),
            entry = SourceSnapshotEntry(
                entryId = SourceEntryId("entry-1"),
                displayName = "Archive source",
                subfolder = "roms",
                torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:test", null)),
                normalizedPath = "/Show/archive.zip",
                ignoreGlobs = emptyList(),
                renameRule = null,
                unarchivePolicy = null
            )
        )

        val failure = result as BrowseResult.Failed
        assertEquals("zip not found", (failure.failure as BrowseFailure.ArchiveResolver).message)
    }

    @Test
    fun waitingArchivePreparationReturnsPreparingBrowseState() = runTest {
        val builder = ArchiveBrowseBuilder(
            loadArchiveBrowse = { _, _ ->
                Result.success(
                    ArchiveBrowseLoadResult.Preparing(
                        statusLabel = "downloading",
                        progressPercent = 42.0,
                        timeoutAtEpochMillis = 1234L
                    )
                )
            }
        )

        val result = builder.build(
            snapshotId = SnapshotId("snapshot-1"),
            entry = SourceSnapshotEntry(
                entryId = SourceEntryId("entry-1"),
                displayName = "Archive source",
                subfolder = "roms",
                torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:test", null)),
                normalizedPath = "/Show/archive.zip",
                ignoreGlobs = emptyList(),
                renameRule = null,
                unarchivePolicy = null
            )
        )

        val preparing = result as BrowseResult.Preparing
        assertEquals(BrowseMode.ARCHIVE_SELECTION, preparing.mode)
        assertEquals("downloading", preparing.statusLabel)
        assertEquals(42.0, preparing.progressPercent)
        assertEquals(1234L, preparing.timeoutAtEpochMillis)
    }

    private fun descriptor(path: String, sizeBytes: Long, offset: Long): ArchiveEntryDescriptor =
        ArchiveEntryDescriptor(
            identity = ArchiveEntryIdentity(
                localHeaderOffset = offset,
                compressedSize = sizeBytes,
                uncompressedSize = sizeBytes,
                crc32 = offset,
                normalizedPath = path
            ),
            entryPath = path,
            sizeBytes = sizeBytes
        )
}
