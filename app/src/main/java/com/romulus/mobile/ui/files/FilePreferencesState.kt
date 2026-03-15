package com.romulus.mobile.ui.files

import com.romulus.mobile.source.browse.SelectionPolicy
import com.romulus.mobile.source.snapshot.ExtractionLayoutMode
import com.romulus.mobile.source.snapshot.ExtractionLayoutPolicy

data class FilePreferencesState(
    val renameAvailable: Boolean,
    val applyRename: Boolean,
    val unarchivePolicy: ExtractionLayoutPolicy?,
    val unarchiveEnabled: Boolean,
    val recursiveUnarchiveEnabled: Boolean
) {
    val unarchiveAvailable: Boolean
        get() = unarchivePolicy != null

    val recursiveUnarchiveAvailable: Boolean
        get() = unarchivePolicy != null

    fun normalized(): FilePreferencesState = copy(
        applyRename = renameAvailable && applyRename,
        unarchiveEnabled = unarchiveAvailable && unarchiveEnabled,
        recursiveUnarchiveEnabled = unarchiveAvailable &&
            unarchiveEnabled &&
            recursiveUnarchiveEnabled
    )

    companion object {
        fun fromSelectionPolicy(policy: SelectionPolicy): FilePreferencesState =
            FilePreferencesState(
                renameAvailable = policy.renameAvailable,
                applyRename = policy.renameAvailable,
                unarchivePolicy = policy.unarchivePolicy?.layout,
                unarchiveEnabled = policy.unarchivePolicy != null,
                recursiveUnarchiveEnabled = policy.unarchivePolicy?.recursiveDefault == true
            ).normalized()

        fun disabled(): FilePreferencesState = FilePreferencesState(
            renameAvailable = false,
            applyRename = false,
            unarchivePolicy = null,
            unarchiveEnabled = false,
            recursiveUnarchiveEnabled = false
        )

        fun flatLayout(): ExtractionLayoutPolicy = ExtractionLayoutPolicy(
            mode = ExtractionLayoutMode.FLAT
        )
    }
}
