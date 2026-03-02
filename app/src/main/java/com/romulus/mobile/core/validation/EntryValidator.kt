package com.romulus.mobile.core.validation

import com.romulus.mobile.domain.files.GlobIgnoreMatcher
import com.romulus.mobile.domain.source.RenameRule
import com.romulus.mobile.domain.source.SourceEntry
import com.romulus.mobile.domain.source.SourceIssue
import com.romulus.mobile.domain.source.SourceTorrent

class EntryValidator {
    fun validate(
        rawIndex: Int,
        displayName: String?,
        subfolder: String?,
        torrents: List<SourceTorrent>,
        rename: RenameRule?,
        ignoreGlobs: List<String>
    ): Pair<SourceEntry?, SourceIssue?> {
        val cleanDisplayName = displayName?.trim().orEmpty()
        if (cleanDisplayName.isBlank()) {
            return null to SourceIssue(rawIndex, "displayName is required")
        }
        val cleanSubfolder = subfolder?.trim().orEmpty()
        if (cleanSubfolder.isBlank()) {
            return null to SourceIssue(rawIndex, "subfolder is required")
        }
        if (cleanSubfolder.startsWith("/") || cleanSubfolder.contains("..")) {
            return null to SourceIssue(rawIndex, "subfolder must be relative and cannot contain ..")
        }
        if (torrents.isEmpty()) {
            return null to SourceIssue(rawIndex, "torrents must be non-empty")
        }
        torrents.forEachIndexed { torrentIndex, torrent ->
            if (!torrent.url.startsWith("magnet:?")) {
                return null to SourceIssue(
                    rawIndex,
                    "torrents[$torrentIndex].url must be a magnet link"
                )
            }
        }
        if (rename != null) {
            if (rename.pattern.isBlank() || rename.replacement.isBlank()) {
                return null to SourceIssue(rawIndex, "rename.pattern and rename.replacement are both required")
            }
            val regexValid = runCatching { Regex(rename.pattern) }.isSuccess
            if (!regexValid) {
                return null to SourceIssue(rawIndex, "rename.pattern is not a valid regex")
            }
        }
        val globResult = GlobIgnoreMatcher.from(ignoreGlobs)
        if (globResult.isFailure) {
            return null to SourceIssue(rawIndex, "ignore.glob contains an invalid pattern")
        }
        return SourceEntry(
            index = rawIndex,
            displayName = cleanDisplayName,
            subfolder = cleanSubfolder,
            torrents = torrents,
            rename = rename,
            ignoreGlobs = ignoreGlobs
        ) to null
    }
}
