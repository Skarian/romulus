package com.romulus.mobile.source.browse

import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.ExtractionLayoutMode
import com.romulus.mobile.source.snapshot.ExtractionLayoutPolicy
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.snapshot.SourcePathScope
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import com.romulus.mobile.source.snapshot.SourceTorrentRef
import com.romulus.mobile.source.snapshot.UnarchivePolicy
import com.romulus.mobile.source.torrentmeta.TorrentFileSelectionIntent
import com.romulus.mobile.source.torrentmeta.TorrentMetadataFileRecord
import com.romulus.mobile.source.torrentmeta.TorrentMetadataInventory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardBrowseBuilderTest {
    @Test
    fun appliesPathScopeThenIgnoreRulesAndSortsRows() = runTest {
        val builder = StandardBrowseBuilder(
            enumerateTorrentMetadata = { _, _ ->
                Result.success(
                    TorrentMetadataInventory(
                        files = listOf(
                            fileRecord(
                                originalName = "Beta.mkv",
                                path = "/shows/Beta.mkv"
                            ),
                            fileRecord(
                                originalName = "Alpha.txt",
                                path = "/shows/Alpha.txt"
                            ),
                            fileRecord(
                                originalName = "Ignored.srt",
                                path = "/shows/Ignored.srt"
                            ),
                            fileRecord(
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
                scope = SourcePathScope(normalizedPath = "/shows/", includeNestedFiles = false),
                ignoreGlobs = listOf("*.srt"),
                renameRule = RenameRule(pattern = "(.*)", replacement = "$1"),
                unarchivePolicy = UnarchivePolicy(
                    recursiveDefault = true,
                    layout = ExtractionLayoutPolicy(mode = ExtractionLayoutMode.FLAT)
                )
            )
        )

        val loaded = result as BrowseResult.Loaded
        val itemNames = loaded.items.map { it.originalDisplayName }
        assertEquals(BrowseMode.STANDARD, loaded.mode)
        assertEquals(listOf("Alpha.txt", "Beta.mkv"), itemNames)
        assertTrue(loaded.items.all { item -> item.itemId.value.startsWith("entry-1:") })
    }

    @Test
    fun duplicateSelectionIntentsAcrossTorrentsStillProduceDistinctItemIds() = runTest {
        val builder = StandardBrowseBuilder(
            enumerateTorrentMetadata = { _, _ ->
                Result.success(
                    TorrentMetadataInventory(
                        files = listOf(
                            fileRecord(
                                originalName = "PartA.mkv",
                                path = "/shows/PartA.mkv",
                                magnetUri = "magnet:?xt=urn:btih:one"
                            ),
                            fileRecord(
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
                scope = SourcePathScope(normalizedPath = "/shows/", includeNestedFiles = false),
                ignoreGlobs = emptyList(),
                renameRule = null,
                unarchivePolicy = null
            )
        ) as BrowseResult.Loaded

        assertEquals(2, result.items.map(SelectableItem::itemId).distinct().size)
    }

    @Test
    fun shallowRootScopeKeepsOnlyTopLevelFiles() = runTest {
        val builder = StandardBrowseBuilder(
            enumerateTorrentMetadata = { _, _ ->
                Result.success(
                    TorrentMetadataInventory(
                        files = listOf(
                            fileRecord(originalName = "Top.mkv", path = "/Top.mkv"),
                            fileRecord(originalName = "Nested.mkv", path = "/shows/Nested.mkv")
                        )
                    )
                )
            }
        )

        val result = builder.build(
            snapshotId = SnapshotId("snapshot-1"),
            entry = sourceEntry(SourcePathScope(normalizedPath = "/", includeNestedFiles = false))
        ) as BrowseResult.Loaded

        assertEquals(listOf("Top.mkv"), result.items.map(SelectableItem::originalDisplayName))
    }

    @Test
    fun nestedDirectoryScopeIncludesDescendants() = runTest {
        val builder = StandardBrowseBuilder(
            enumerateTorrentMetadata = { _, _ ->
                Result.success(
                    TorrentMetadataInventory(
                        files = listOf(
                            fileRecord(originalName = "Direct.mkv", path = "/shows/Direct.mkv"),
                            fileRecord(
                                originalName = "Nested.mkv",
                                path = "/shows/season1/Nested.mkv"
                            ),
                            fileRecord(originalName = "Outside.mkv", path = "/movies/Outside.mkv")
                        )
                    )
                )
            }
        )

        val result = builder.build(
            snapshotId = SnapshotId("snapshot-1"),
            entry = sourceEntry(
                SourcePathScope(normalizedPath = "/shows/", includeNestedFiles = true)
            )
        ) as BrowseResult.Loaded

        assertEquals(
            listOf("Direct.mkv", "Nested.mkv"),
            result.items.map(SelectableItem::originalDisplayName)
        )
    }

    private fun sourceEntry(scope: SourcePathScope): SourceSnapshotEntry = SourceSnapshotEntry(
        entryId = SourceEntryId("entry-1"),
        displayName = "Shows",
        subfolder = "shows",
        torrents = listOf(SourceTorrentRef("magnet:?xt=urn:btih:one", "Part A")),
        scope = scope,
        ignoreGlobs = emptyList(),
        renameRule = null,
        unarchivePolicy = null
    )

    private fun fileRecord(
        originalName: String,
        path: String,
        magnetUri: String = "magnet:?xt=urn:btih:one"
    ): TorrentMetadataFileRecord = TorrentMetadataFileRecord(
        originalName = originalName,
        path = path,
        sizeBytes = 100L,
        partLabel = "Part A",
        selectionIntent = TorrentFileSelectionIntent(
            sourceMagnetUri = magnetUri,
            normalizedPath = path.removePrefix("/"),
            sizeBytes = 100L,
            occurrenceIndex = 1
        )
    )
}
