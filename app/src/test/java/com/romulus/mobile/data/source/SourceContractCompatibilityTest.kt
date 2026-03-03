package com.romulus.mobile.data.source

import com.romulus.mobile.domain.source.SourceSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceContractCompatibilityTest {
    private val mapper = SourceEntryContractMapper()

    @Test
    fun explicitPathIsNormalized() {
        val mapping = mapper.map(
            entries = listOf(
                SourceEntryDto(
                    displayName = "Season One",
                    subfolder = "shows/season-one",
                    path = "Series/Season 01",
                    torrents = listOf(
                        SourceTorrentDto(url = "magnet:?xt=urn:btih:abc")
                    )
                )
            )
        )

        assertEquals(1, mapping.entries.size)
        assertTrue(mapping.issues.isEmpty())
        assertEquals("/Series/Season 01", mapping.entries.first().path)
    }

    @Test
    fun missingAndBlankPathsDefaultToRoot() {
        val mapping = mapper.map(
            entries = listOf(
                SourceEntryDto(
                    displayName = "No Path",
                    subfolder = "shows/no-path",
                    torrents = listOf(SourceTorrentDto(url = "magnet:?xt=urn:btih:abc"))
                ),
                SourceEntryDto(
                    displayName = "Blank Path",
                    subfolder = "shows/blank-path",
                    path = "  ",
                    torrents = listOf(SourceTorrentDto(url = "magnet:?xt=urn:btih:def"))
                )
            )
        )

        assertTrue(mapping.issues.isEmpty())
        assertEquals(listOf("/", "/"), mapping.entries.map { it.path })
    }

    @Test
    fun invalidPathReturnsEntryIssue() {
        val mapping = mapper.map(
            entries = listOf(
                SourceEntryDto(
                    displayName = "Broken Path",
                    subfolder = "shows/broken",
                    path = "../Season 01",
                    torrents = listOf(SourceTorrentDto(url = "magnet:?xt=urn:btih:abc"))
                )
            )
        )

        assertTrue(mapping.entries.isEmpty())
        assertEquals(1, mapping.issues.size)
        assertEquals(0, mapping.issues.first().entryIndex)
        assertEquals("path must be relative to torrent root and cannot contain ..", mapping.issues.first().message)
    }

    @Test
    fun malformedRulesStayEntryLocal() {
        val mapping = mapper.map(
            entries = listOf(
                SourceEntryDto(
                    displayName = "Bad Rename",
                    subfolder = "shows/bad",
                    torrents = listOf(SourceTorrentDto(url = "magnet:?xt=urn:btih:abc")),
                    rename = RenameDto(pattern = "[", replacement = "x")
                ),
                SourceEntryDto(
                    displayName = "Good Entry",
                    subfolder = "shows/good",
                    torrents = listOf(SourceTorrentDto(url = "magnet:?xt=urn:btih:def")),
                    ignore = IgnoreDto(globPatterns = listOf("*.nfo"))
                )
            )
        )

        assertEquals(1, mapping.entries.size)
        assertEquals("Good Entry", mapping.entries.first().displayName)
        assertEquals(1, mapping.issues.size)
        assertEquals(0, mapping.issues.first().entryIndex)
        assertEquals("rename.pattern is not a valid regex", mapping.issues.first().message)
    }

    @Test
    fun legacySnapshotWithoutPathStillDecodes() {
        val legacySnapshotJson = """
            {
              "snapshotId": "snapshot-legacy",
              "sourceMode": "FILE",
              "sourceValue": "file:///legacy.json",
              "generatedAtEpochMs": 1234,
              "entries": [
                {
                  "index": 0,
                  "displayName": "Legacy Entry",
                  "subfolder": "legacy/sub",
                  "torrents": [
                    {
                      "partIndex": 0,
                      "url": "magnet:?xt=urn:btih:abc",
                      "partName": null
                    }
                  ],
                  "rename": null,
                  "ignoreGlobs": []
                }
              ],
              "issues": [],
              "stale": false
            }
        """.trimIndent()

        val snapshot = Json.decodeFromString(SourceSnapshot.serializer(), legacySnapshotJson)

        assertEquals("/", snapshot.entries.first().path)
    }
}
