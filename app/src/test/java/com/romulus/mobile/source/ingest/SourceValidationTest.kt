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
                        scope = SourceScopeDocument(path = "/movie.mkv")
                    )
                )
            )
        )

        assertTrue(issues.any { it is SourceValidationIssue.InvalidPath })
    }

    @Test
    fun rejectsNestedFilesForExactZipScope() {
        val issues = validation.validate(
            SourceDocument(
                version = 1,
                entries = listOf(
                    SourceEntryDocument(
                        displayName = "Archive",
                        subfolder = "archive",
                        torrents = listOf(SourceTorrentDocument(url = "magnet:?xt=urn:btih:one")),
                        scope = SourceScopeDocument(
                            path = "/Show/archive.zip",
                            includeNestedFiles = true
                        )
                    )
                )
            )
        )

        assertTrue(issues.any { it is SourceValidationIssue.InvalidScope })
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
                        unarchive = UnarchiveDocument(
                            layout = UnarchiveLayoutDocument(
                                mode = UnarchiveLayoutModeDocument.DEDICATED_FOLDER,
                                rename = RenameRule(pattern = "[", replacement = "folder")
                            )
                        )
                    )
                )
            )
        )

        assertTrue(issues.any { it is SourceValidationIssue.InvalidRenameRule })
    }
}
