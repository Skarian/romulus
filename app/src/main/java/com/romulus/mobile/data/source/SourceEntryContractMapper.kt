package com.romulus.mobile.data.source

import com.romulus.mobile.core.validation.EntryValidator
import com.romulus.mobile.domain.source.RenameRule
import com.romulus.mobile.domain.source.SourceEntry
import com.romulus.mobile.domain.source.SourceIssue
import com.romulus.mobile.domain.source.SourceTorrent

class SourceEntryContractMapper(
    private val validator: EntryValidator = EntryValidator()
) {
    fun map(entries: List<SourceEntryDto>): SourceEntryContractMappingResult {
        val issues = mutableListOf<SourceIssue>()
        val validEntries = entries.mapIndexedNotNull { index, dto ->
            val torrents = dto.torrents.mapIndexed { partIndex, torrentDto ->
                SourceTorrent(
                    partIndex = partIndex,
                    url = torrentDto.url.trim(),
                    partName = torrentDto.partName?.trim()?.takeIf { it.isNotBlank() }
                )
            }
            val rename = dto.rename?.let {
                RenameRule(
                    pattern = it.pattern.orEmpty(),
                    replacement = it.replacement.orEmpty()
                )
            }
            val ignoreGlobs = dto.ignore?.globPatterns.orEmpty()
            val (entry, issue) = validator.validate(
                rawIndex = index,
                displayName = dto.displayName,
                subfolder = dto.subfolder,
                path = dto.path,
                torrents = torrents,
                rename = rename,
                ignoreGlobs = ignoreGlobs
            )
            if (issue != null) {
                issues += issue
            }
            entry
        }
        return SourceEntryContractMappingResult(
            entries = validEntries,
            issues = issues
        )
    }
}

data class SourceEntryContractMappingResult(
    val entries: List<SourceEntry>,
    val issues: List<SourceIssue>
)
