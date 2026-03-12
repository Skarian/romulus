package com.romulus.mobile.source.browse

import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderInventory
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.snapshot.SourceTorrentRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardBrowseBuilderTest {
    @Test
    fun appliesPathScopeThenIgnoreRulesAndSortsRows() = runTest {
        val builder = StandardBrowseBuilder(
            enumerateProviderFiles = {
                Result.success(
                    ProviderInventory(
                        files = listOf(
                            fileRecord(
                                providerFileId = "2",
                                originalName = "Beta.mkv",
                                path = "/shows/Beta.mkv"
                            ),
                            fileRecord(
                                providerFileId = "1",
                                originalName = "Alpha.txt",
                                path = "/shows/Alpha.txt"
                            ),
                            fileRecord(
                                providerFileId = "3",
                                originalName = "Ignored.srt",
                                path = "/shows/Ignored.srt"
                            ),
                            fileRecord(
                                providerFileId = "4",
                                originalName = "Outside.mkv",
                                path = "/movies/Outside.mkv"
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
                displayName = "Shows",
                subfolder = "shows",
                torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:one", "Part A")),
                normalizedPath = "/shows/",
                ignoreGlobs = listOf("*.srt"),
                renameRule = RenameRule(pattern = "(.*)", replacement = "$1"),
                unarchiveConfigured = true,
                unarchiveDefault = false,
                recursiveConfigured = true,
                recursiveUnarchiveDefault = true
            )
        )

        val loaded = result as BrowseResult.Loaded
        val itemNames = loaded.items.map { it.originalDisplayName }
        assertEquals(BrowseMode.STANDARD, loaded.mode)
        assertEquals(listOf("Alpha.txt", "Beta.mkv"), itemNames)
        assertTrue(loaded.items.all { item -> item.itemId.value.startsWith("entry-1:") })
    }

    @Test
    fun duplicateProviderFileIdsAcrossTorrentsStillProduceDistinctItemIds() = runTest {
        val builder = StandardBrowseBuilder(
            enumerateProviderFiles = {
                Result.success(
                    ProviderInventory(
                        files = listOf(
                            fileRecord(
                                providerFileId = "1",
                                originalName = "PartA.mkv",
                                path = "/shows/PartA.mkv",
                                magnetUri = "magnet:?xt=urn:btih:one"
                            ),
                            fileRecord(
                                providerFileId = "1",
                                originalName = "PartB.mkv",
                                path = "/shows/PartB.mkv",
                                magnetUri = "magnet:?xt=urn:btih:two"
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
                displayName = "Shows",
                subfolder = "shows",
                torrents = listOf(
                    SourceTorrentRef("magnet:?xt=urn:btih:one", "Part A"),
                    SourceTorrentRef("magnet:?xt=urn:btih:two", "Part B")
                ),
                normalizedPath = "/shows/",
                ignoreGlobs = emptyList(),
                renameRule = null,
                unarchiveConfigured = false,
                unarchiveDefault = false,
                recursiveConfigured = false,
                recursiveUnarchiveDefault = false
            )
        ) as BrowseResult.Loaded

        assertEquals(2, result.items.map(SelectableItem::itemId).distinct().size)
    }

    private fun fileRecord(
        providerFileId: String,
        originalName: String,
        path: String,
        magnetUri: String = "magnet:?xt=urn:btih:one"
    ): ProviderFileRecord = ProviderFileRecord(
        providerFileId = providerFileId,
        originalName = originalName,
        path = path,
        sizeBytes = 100L,
        partLabel = "Part A",
        locator = ProviderLocator(
            sourceMagnetUri = magnetUri,
            torrentId = "torrent-1",
            providerFileIds = listOf(providerFileId),
            selectedProviderFileId = providerFileId,
            path = path,
            partLabel = "Part A"
        )
    )
}
