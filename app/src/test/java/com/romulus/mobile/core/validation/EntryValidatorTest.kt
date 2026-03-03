package com.romulus.mobile.core.validation

import com.romulus.mobile.domain.source.RenameRule
import com.romulus.mobile.domain.source.SourceTorrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EntryValidatorTest {
    private val validator = EntryValidator()

    @Test
    fun rejectsNonMagnetTorrents() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = "/",
            torrents = listOf(SourceTorrent(0, "https://example.com/file.torrent", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNull(entry)
        assertNotNull(issue)
    }

    @Test
    fun acceptsValidEntry() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder/sub",
            path = "series/season-01",
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", "Part 1")),
            rename = RenameRule("^(.*)$", "$1"),
            ignoreGlobs = listOf("*.nfo")
        )

        assertNotNull(entry)
        assertNull(issue)
        assertEquals("Example", entry?.displayName)
        assertEquals("/series/season-01", entry?.path)
    }

    @Test
    fun missingPathDefaultsToRoot() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = null,
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNotNull(entry)
        assertNull(issue)
        assertEquals("/", entry?.path)
    }

    @Test
    fun blankPathDefaultsToRoot() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = "   ",
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNotNull(entry)
        assertNull(issue)
        assertEquals("/", entry?.path)
    }

    @Test
    fun rejectsTraversalPath() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = "../season-01",
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNull(entry)
        assertNotNull(issue)
        assertEquals("path must be relative to torrent root and cannot contain ..", issue?.message)
    }

    @Test
    fun rejectsBackslashPath() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = "Season\\01",
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNull(entry)
        assertNotNull(issue)
        assertEquals("path must use forward slashes", issue?.message)
    }

    @Test
    fun rootDotPathNormalizesToRoot() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = ".",
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNotNull(entry)
        assertNull(issue)
        assertEquals("/", entry?.path)
    }

    @Test
    fun nestedPathNormalizesWithLeadingSlash() {
        val (entry, issue) = validator.validate(
            rawIndex = 0,
            displayName = "Example",
            subfolder = "folder",
            path = "Series/Season 01",
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", null)),
            rename = null,
            ignoreGlobs = emptyList()
        )

        assertNotNull(entry)
        assertNull(issue)
        assertEquals("/Series/Season 01", entry?.path)
    }
}
