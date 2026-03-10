package com.romulus.mobile.ui.files

import com.romulus.mobile.source.browse.SelectionPolicy
import com.romulus.mobile.source.ingest.RenameRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePreferencesStateTest {
    @Test
    fun normalizedTurnsOffRecursiveUnarchiveWhenUnarchiveIsDisabled() {
        val normalized = FilePreferencesState(
            renameAvailable = true,
            applyRename = true,
            unarchiveAvailable = true,
            unarchiveEnabled = false,
            recursiveUnarchiveAvailable = true,
            recursiveUnarchiveEnabled = true
        ).normalized()

        assertTrue(normalized.applyRename)
        assertFalse(normalized.unarchiveEnabled)
        assertFalse(normalized.recursiveUnarchiveEnabled)
    }

    @Test
    fun fromSelectionPolicyUsesOnlyAdvertisedOptions() {
        val state = FilePreferencesState.fromSelectionPolicy(
            SelectionPolicy(
                renameRule = RenameRule(pattern = "a", replacement = "b"),
                renameAvailable = false,
                unarchiveToggleVisible = true,
                unarchiveDefault = true,
                recursiveToggleVisible = true,
                recursiveUnarchiveDefault = true
            )
        )

        assertFalse(state.applyRename)
        assertTrue(state.unarchiveEnabled)
        assertTrue(state.recursiveUnarchiveEnabled)
    }
}
