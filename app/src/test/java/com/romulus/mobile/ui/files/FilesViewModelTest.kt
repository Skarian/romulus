package com.romulus.mobile.ui.files

import androidx.lifecycle.SavedStateHandle
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.browse.SelectableItem
import com.romulus.mobile.source.browse.SelectableItemId
import com.romulus.mobile.source.browse.SelectableItemSourceContext
import com.romulus.mobile.source.browse.SelectionPolicy
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import com.romulus.mobile.source.torrentmeta.TorrentFileSelectionIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    @Test
    fun selectAllAndNoneOnlyChangeVisibleRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = FilesViewModel(
                routeArgs = FilesRouteArgs(
                    snapshotId = SnapshotId("snapshot"),
                    entryId = SourceEntryId("entry")
                ),
                sourceFacade = SourceFacade(),
                downloadsFacade = DownloadsFacade(),
                savedStateHandle = SavedStateHandle()
            )
            advanceUntilIdle()

            val hiddenId = SelectableItemId("hidden")
            val visibleId = SelectableItemId("visible")
            setMutableStateFlowValue(
                viewModel,
                "resolvedItems",
                listOf(
                    selectableItem(itemId = hiddenId, name = "Hidden file"),
                    selectableItem(itemId = visibleId, name = "Visible file")
                )
            )
            setMutableStateFlowValue(viewModel, "selectedIds", setOf(hiddenId))
            viewModel.updateSearchQuery("Visible")
            advanceUntilIdle()

            viewModel.selectAllVisible()
            assertEquals(
                setOf(hiddenId, visibleId),
                readMutableStateFlowValue<Set<SelectableItemId>>(viewModel, "selectedIds")
            )

            viewModel.deselectVisible()
            assertEquals(
                setOf(hiddenId),
                readMutableStateFlowValue<Set<SelectableItemId>>(viewModel, "selectedIds")
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun browseFailureResetsModeAndPreferences() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = FilesViewModel(
                routeArgs = FilesRouteArgs(
                    snapshotId = SnapshotId("snapshot"),
                    entryId = SourceEntryId("entry")
                ),
                sourceFacade = SourceFacade(),
                downloadsFacade = DownloadsFacade(),
                savedStateHandle = SavedStateHandle()
            )
            advanceUntilIdle()

            setMutableStateFlowValue(viewModel, "mode", FilesMode.ARCHIVE_SELECTION)
            setMutableStateFlowValue(
                viewModel,
                "preferences",
                FilePreferencesState(
                    renameAvailable = true,
                    applyRename = true,
                    unarchiveAvailable = true,
                    unarchiveEnabled = true,
                    recursiveUnarchiveAvailable = true,
                    recursiveUnarchiveEnabled = true
                )
            )

            viewModel.retryResolve()
            advanceUntilIdle()

            assertEquals(FilesMode.STANDARD, readMutableStateFlowValue(viewModel, "mode"))
            assertEquals(
                FilePreferencesState.disabled(),
                readMutableStateFlowValue(viewModel, "preferences")
            )
            assertEquals(
                "SourceFacade is not wired yet",
                readMutableStateFlowValue<String?>(viewModel, "resolverError")
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun selectableItem(itemId: SelectableItemId, name: String): SelectableItem.StandardFile =
        SelectableItem.StandardFile(
            itemId = itemId,
            snapshotId = SnapshotId("snapshot"),
            entryId = SourceEntryId("entry"),
            originalDisplayName = name,
            sizeBytes = 1024L,
            selectionPolicy = SelectionPolicy(
                renameRule = null,
                renameAvailable = false,
                unarchiveToggleVisible = false,
                unarchiveDefault = false,
                recursiveToggleVisible = false,
                recursiveUnarchiveDefault = false
            ),
            sourceContext = SelectableItemSourceContext(
                entryDisplayName = "Entry",
                outputSubfolder = "folder",
                partLabel = null,
                providerFileId = "provider-file"
            ),
            selectionIntent = TorrentFileSelectionIntent(
                sourceMagnetUri = "magnet:?xt=urn:btih:test",
                normalizedPath = name,
                sizeBytes = 1024L,
                occurrenceIndex = 1
            )
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> setMutableStateFlowValue(instance: Any, fieldName: String, value: T) {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val flow = field.get(instance) as MutableStateFlow<T>
        flow.value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readMutableStateFlowValue(instance: Any, fieldName: String): T {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val flow = field.get(instance) as MutableStateFlow<T>
        return flow.value
    }
}
