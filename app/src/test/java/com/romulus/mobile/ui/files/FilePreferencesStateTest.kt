package com.romulus.mobile.ui.files

import com.romulus.mobile.source.browse.SelectionPolicy
import com.romulus.mobile.source.ingest.RenameRule
import com.romulus.mobile.source.snapshot.ExtractionLayoutMode
import com.romulus.mobile.source.snapshot.ExtractionLayoutPolicy
import com.romulus.mobile.source.snapshot.UnarchivePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePreferencesStateTest {
    @Test
    fun normalizedTurnsOffRecursiveUnarchiveWhenUnarchiveIsDisabled() {
        val normalized = FilePreferencesState(
            renameAvailable = true,
            applyRename = true,
            unarchivePolicy = ExtractionLayoutPolicy(mode = ExtractionLayoutMode.FLAT),
            unarchiveEnabled = false,
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
                unarchivePolicy = UnarchivePolicy(
                    recursiveDefault = true,
                    layout = ExtractionLayoutPolicy(mode = ExtractionLayoutMode.FLAT)
                )
            )
        )

        assertFalse(state.applyRename)
        assertTrue(state.unarchiveEnabled)
        assertTrue(state.recursiveUnarchiveEnabled)
    }
}
