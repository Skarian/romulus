package com.romulus.mobile.source.ingest

import org.junit.Assert.assertTrue
import org.junit.Test

class SourceValidationTest {
    private val validation = SourceValidation()

    @Test
    fun rejectsExactNonZipPath() {
        val issues = validation.validate(
            SourceDocument(
                version = 1,
                entries = listOf(
                    SourceEntryDocument(
                        displayName = "Movies",
                        subfolder = "movies",
                        torrents = listOf(SourceTorrentDocument(url = "magnet:?xt=urn:btih:one")),
                        path = "/movie.mkv"
                    )
                )
            )
        )

        assertTrue(issues.any { it is SourceValidationIssue.InvalidPath })
    }

    @Test
    fun rejectsInvalidRenameRegex() {
        val issues = validation.validate(
            SourceDocument(
                version = 1,
                entries = listOf(
                    SourceEntryDocument(
                        displayName = "Movies",
                        subfolder = "movies",
                        torrents = listOf(SourceTorrentDocument(url = "magnet:?xt=urn:btih:one")),
                        rename = RenameRule(pattern = "[", replacement = "fixed")
                    )
                )
            )
        )

        assertTrue(issues.any { it is SourceValidationIssue.InvalidRenameRule })
    }

    @Test
    fun rejectsRecursiveUnarchiveWithoutUnarchive() {
        val issues = validation.validate(
            SourceDocument(
                version = 1,
                entries = listOf(
                    SourceEntryDocument(
                        displayName = "Movies",
                        subfolder = "movies",
                        torrents = listOf(SourceTorrentDocument(url = "magnet:?xt=urn:btih:one")),
                        recursiveUnarchive = true
                    )
                )
            )
        )

        assertTrue(issues.any { it is SourceValidationIssue.InvalidRecursiveUnarchive })
    }
}
