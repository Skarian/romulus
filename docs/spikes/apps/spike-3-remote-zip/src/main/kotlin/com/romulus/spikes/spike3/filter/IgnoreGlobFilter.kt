package com.romulus.spikes.spike3.filter

import com.romulus.spikes.spike3.errors.FailureCodes
import com.romulus.spikes.spike3.errors.Spike3FailureException
import com.romulus.spikes.spike3.model.EntryIdentity
import com.romulus.spikes.spike3.model.EnumeratedEntry
import com.romulus.spikes.spike3.model.FilteredEntries
import com.romulus.spikes.spike3.model.FailureStage
import java.nio.file.FileSystems
import java.nio.file.Path

object IgnoreGlobFilter {
    fun apply(
        entries: List<EnumeratedEntry>,
        ignoreGlobs: List<String>,
        selectedEntries: List<EntryIdentity>,
    ): FilteredEntries {
        val matchers = ignoreGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val files = entries.filterNot { it.isDirectory }
        val ignored = files.filter { entry ->
            matchers.any { matcher -> matcher.matches(Path.of(entry.identity.entryPath)) }
        }
        val ignoredIdentities = ignored.map { it.identity }.toSet()
        val visible = files.filterNot { it.identity in ignoredIdentities }
        val visibleByIdentity = visible.associateBy { it.identity }
        val selected = selectedEntries.map { identity ->
            visibleByIdentity[identity] ?: throw Spike3FailureException(
                stage = FailureStage.FILTER,
                errorCode = FailureCodes.FILTER_SELECTION_MISMATCH,
                message = "Selected entry $identity is not present in the visible candidate set",
            )
        }
        return FilteredEntries(
            ignoredEntries = ignored,
            visibleEntries = visible,
            selectedEntries = selected,
        )
    }
}
