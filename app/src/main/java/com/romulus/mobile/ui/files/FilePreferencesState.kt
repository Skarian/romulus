package com.romulus.mobile.ui.files

import com.romulus.mobile.source.browse.SelectionPolicy

data class FilePreferencesState(
    val renameAvailable: Boolean,
    val applyRename: Boolean,
    val unarchiveAvailable: Boolean,
    val unarchiveEnabled: Boolean,
    val recursiveUnarchiveAvailable: Boolean,
    val recursiveUnarchiveEnabled: Boolean
) {
    fun normalized(): FilePreferencesState = copy(
        applyRename = renameAvailable && applyRename,
        unarchiveEnabled = unarchiveAvailable && unarchiveEnabled,
        recursiveUnarchiveEnabled = recursiveUnarchiveAvailable &&
            unarchiveAvailable &&
            unarchiveEnabled &&
            recursiveUnarchiveEnabled
    )

    companion object {
        fun fromSelectionPolicy(policy: SelectionPolicy): FilePreferencesState =
            FilePreferencesState(
                renameAvailable = policy.renameAvailable,
                applyRename = policy.renameAvailable,
                unarchiveAvailable = policy.unarchiveToggleVisible,
                unarchiveEnabled = policy.unarchiveDefault,
                recursiveUnarchiveAvailable = policy.recursiveToggleVisible,
                recursiveUnarchiveEnabled = policy.unarchiveDefault &&
                    policy.recursiveUnarchiveDefault
            ).normalized()

        fun disabled(): FilePreferencesState = FilePreferencesState(
            renameAvailable = false,
            applyRename = false,
            unarchiveAvailable = false,
            unarchiveEnabled = false,
            recursiveUnarchiveAvailable = false,
            recursiveUnarchiveEnabled = false
        )
    }
}
