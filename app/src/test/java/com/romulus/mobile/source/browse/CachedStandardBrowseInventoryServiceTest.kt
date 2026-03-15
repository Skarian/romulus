package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderInventory
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.snapshot.SourcePathScope
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.snapshot.SourceTorrentRef
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedStandardBrowseInventoryServiceTest {
    @Test
    fun cachesInventoryAfterFirstEnumeration() = runTest {
        val cacheDirectory = Files.createTempDirectory("browse-cache").toFile()
        val requests = mutableListOf<ProviderInventoryRequest>()
        val service = CachedStandardBrowseInventoryService(
            cacheStore = FileStandardBrowseInventoryCacheStore(
                cacheDirectory = cacheDirectory,
                json = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            ),
            enumerateProviderFiles = { request ->
                requests += request
                Result.success(
                    ProviderInventory(
                        files = listOf(
                            providerFileRecord(
                                magnetUri = "magnet:?xt=urn:btih:one",
                                path = "shows/Alpha.mkv",
                                partLabel = "Part A"
                            )
                        )
                    )
                )
            }
        )

        val entry = sourceEntry(
            torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:one", "Part A"))
        )
        val snapshotId = SnapshotId("snapshot-1")

        val first = service.load(snapshotId, entry).getOrThrow()
        val second = service.load(snapshotId, entry).getOrThrow()

        assertEquals(1, requests.size)
        assertEquals(first, second)
        assertTrue(cacheDirectory.listFiles().orEmpty().isNotEmpty())
        assertEquals("magnet:?xt=urn:btih:one", first.files.single().selectionIntent.sourceMagnetUri)
    }

    @Test
    fun preservesOccurrenceOrderAcrossDuplicateProviderPaths() = runTest {
        val cacheDirectory = Files.createTempDirectory("browse-cache").toFile()
        val service = CachedStandardBrowseInventoryService(
            cacheStore = FileStandardBrowseInventoryCacheStore(
                cacheDirectory = cacheDirectory,
                json = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            ),
            enumerateProviderFiles = {
                Result.success(
                    ProviderInventory(
                        files = listOf(
                            providerFileRecord(
                                magnetUri = "magnet:?xt=urn:btih:first",
                                path = "shows/Episode.mkv",
                                partLabel = "Part 1"
                            ),
                            providerFileRecord(
                                magnetUri = "magnet:?xt=urn:btih:second",
                                path = "shows/Episode.mkv",
                                partLabel = "Part 2"
                            )
                        )
                    )
                )
            }
        )

        val inventory = service.load(
            SnapshotId("snapshot-1"),
            sourceEntry(
                torrents = listOf(
                    SourceTorrentRef("magnet:?xt=urn:btih:first", "Part 1"),
                    SourceTorrentRef("magnet:?xt=urn:btih:second", "Part 2")
                )
            )
        ).getOrThrow()

        assertEquals(listOf(1, 2), inventory.files.map { it.selectionIntent.occurrenceIndex })
        assertEquals(
            listOf("magnet:?xt=urn:btih:first", "magnet:?xt=urn:btih:second"),
            inventory.files.map { it.selectionIntent.sourceMagnetUri }
        )
    }

    private fun sourceEntry(torrents: List<SourceTorrentRef>) = SourceSnapshotEntry(
        entryId = SourceEntryId("entry-1"),
        displayName = "Shows",
        subfolder = "shows",
        torrents = torrents,
        scope = SourcePathScope("/", false),
        ignoreGlobs = emptyList(),
        renameRule = null,
        unarchivePolicy = null
    )

    private fun providerFileRecord(
        magnetUri: String,
        path: String,
        partLabel: String?
    ) = ProviderFileRecord(
        providerFileId = "unused",
        originalName = path.substringAfterLast('/'),
        path = path,
        sizeBytes = 100L,
        partLabel = partLabel,
        locator = ProviderLocator(
            sourceMagnetUri = magnetUri,
            torrentId = "torrent-1",
            providerFileIds = listOf("unused"),
            selectedProviderFileId = "unused",
            path = path,
            partLabel = partLabel
        )
    )
}
