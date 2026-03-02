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
            torrents = listOf(SourceTorrent(0, "magnet:?xt=urn:btih:abc", "Part 1")),
            rename = RenameRule("^(.*)$", "$1"),
            ignoreGlobs = listOf("*.nfo")
        )

        assertNotNull(entry)
        assertNull(issue)
        assertEquals("Example", entry?.displayName)
    }
}
