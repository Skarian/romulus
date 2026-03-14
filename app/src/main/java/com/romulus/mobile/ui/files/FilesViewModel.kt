@file:Suppress("TooManyFunctions")

package com.romulus.mobile.ui.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.downloads.queue.NamingIntent
import com.romulus.mobile.downloads.queue.QueueExecutionContext
import com.romulus.mobile.downloads.queue.QueueTaskInput
import com.romulus.mobile.downloads.queue.SourceQueueMetadata
import com.romulus.mobile.downloads.queue.StorageTargetContext
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.browse.BrowseFailure
import com.romulus.mobile.source.browse.BrowseMode
import com.romulus.mobile.source.browse.BrowseRequest
import com.romulus.mobile.source.browse.BrowseResult
import com.romulus.mobile.source.browse.SelectableItem
import com.romulus.mobile.source.browse.SelectableItemId
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FilesRouteArgs(
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId,
    val entryDisplayName: String
)

data class FilesUiState(
    val entryDisplayName: String,
    val mode: FilesMode,
    val isResolving: Boolean,
    val preparing: FilesPreparingState?,
    val rows: List<SelectableRowModel>,
    val selectedIds: Set<SelectableItemId>,
    val preferences: FilePreferencesState,
    val searchQuery: String,
    val resolverError: String?,
    val listAnchor: Int
)

enum class FilesMode {
    STANDARD,
    ARCHIVE_SELECTION
}

data class SelectableRowModel(
    val itemId: SelectableItemId,
    val originalDisplayName: String,
    val sizeBytes: Long?,
    val partLabel: String?,
    val providerFileId: String?
)

data class FilesPreparingState(
    val statusLabel: String?,
    val progressPercent: Double?,
    val timeoutAtEpochMillis: Long
)

class FilesViewModel(
    private val routeArgs: FilesRouteArgs,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val diagnosticsFacade: DiagnosticsFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private data class FilesLocalState(
        val selectedIds: Set<SelectableItemId>,
        val preferences: FilePreferencesState,
        val searchQuery: String,
        val resolverError: String?,
        val listAnchor: Int
    )

    private data class FilesResolvedState(
        val entryDisplayName: String,
        val mode: FilesMode,
        val isResolving: Boolean,
        val preparing: FilesPreparingState?,
        val items: List<SelectableItem>
    )

    private val resolvedItems = MutableStateFlow<List<SelectableItem>>(emptyList())
    private val entryDisplayName = MutableStateFlow(routeArgs.entryDisplayName)
    private val mode = MutableStateFlow(FilesMode.STANDARD)
    private val isResolving = MutableStateFlow(false)
    private val preparing = MutableStateFlow<FilesPreparingState?>(null)
    private val selectedIds = MutableStateFlow<Set<SelectableItemId>>(emptySet())
    private val preferences = MutableStateFlow(FilePreferencesState.disabled())
    private val searchQuery = MutableStateFlow("")
    private val resolverError = MutableStateFlow<String?>(null)
    private val listAnchor = MutableStateFlow(0)
    private var preparingPollJob: Job? = null

    private val localState = combine(
        selectedIds,
        preferences,
        searchQuery,
        resolverError,
        listAnchor
    ) { ids, filePreferences, query, error, anchor ->
        FilesLocalState(
            selectedIds = ids,
            preferences = filePreferences,
            searchQuery = query,
            resolverError = error,
            listAnchor = anchor
        )
    }

    private val resolvedState = combine(
        entryDisplayName,
        mode,
        isResolving,
        preparing,
        resolvedItems
    ) { currentEntryDisplayName, browseMode, resolving, preparingState, items ->
        FilesResolvedState(
            entryDisplayName = currentEntryDisplayName,
            mode = browseMode,
            isResolving = resolving,
            preparing = preparingState,
            items = items
        )
    }

    val state: StateFlow<FilesUiState> = combine(
        resolvedState,
        localState
    ) { resolved, local ->
        buildState(
            entryDisplayName = resolved.entryDisplayName,
            browseMode = resolved.mode,
            isResolving = resolved.isResolving,
            preparing = resolved.preparing,
            items = resolved.items,
            selectedIds = local.selectedIds.intersect(
                resolved.items.map { item -> item.itemId }.toSet()
            ),
            filePreferences = local.preferences,
            searchQuery = local.searchQuery,
            resolverError = local.resolverError,
            listAnchor = local.listAnchor
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = buildState(
            entryDisplayName = entryDisplayName.value,
            browseMode = mode.value,
            isResolving = isResolving.value,
            preparing = preparing.value,
            items = resolvedItems.value,
            selectedIds = selectedIds.value,
            filePreferences = preferences.value,
            searchQuery = searchQuery.value,
            resolverError = resolverError.value,
            listAnchor = listAnchor.value
        )
    )

    init {
        savedStateHandle.keys()
        retryResolve()
    }

    @Suppress("LongMethod")
    fun retryResolve() {
        preparingPollJob?.cancel()
        isResolving.value = true
        resolverError.value = null
        viewModelScope.launch {
            when (
                val result = sourceFacade.browse(
                    BrowseRequest(
                        snapshotId = routeArgs.snapshotId,
                        entryId = routeArgs.entryId
                    )
                )
            ) {
                is BrowseResult.Loaded -> {
                    resolvedItems.value = result.items
                    val firstItem = result.items.firstOrNull()
                    entryDisplayName.value = firstItem?.sourceContext?.entryDisplayName
                        ?: routeArgs.entryDisplayName
                    mode.value = when (result.mode) {
                        BrowseMode.STANDARD -> FilesMode.STANDARD
                        BrowseMode.ARCHIVE_SELECTION -> FilesMode.ARCHIVE_SELECTION
                    }
                    preparing.value = null
                    selectedIds.value = emptySet()
                    val selectionPolicy = firstItem?.selectionPolicy
                    preferences.value =
                        selectionPolicy?.let(FilePreferencesState::fromSelectionPolicy)
                            ?: FilePreferencesState.disabled()
                    resolverError.value = null
                    isResolving.value = false
                }

                is BrowseResult.Preparing -> {
                    resolvedItems.value = emptyList()
                    entryDisplayName.value = routeArgs.entryDisplayName
                    mode.value = when (result.mode) {
                        BrowseMode.STANDARD -> FilesMode.STANDARD
                        BrowseMode.ARCHIVE_SELECTION -> FilesMode.ARCHIVE_SELECTION
                    }
                    preparing.value = FilesPreparingState(
                        statusLabel = result.statusLabel,
                        progressPercent = result.progressPercent,
                        timeoutAtEpochMillis = result.timeoutAtEpochMillis
                    )
                    selectedIds.value = emptySet()
                    preferences.value = FilePreferencesState.disabled()
                    resolverError.value = null
                    isResolving.value = false
                    preparingPollJob = viewModelScope.launch {
                        delay(PREPARING_POLL_INTERVAL_MILLIS)
                        preparingPollJob = null
                        retryResolve()
                    }
                }

                is BrowseResult.Failed -> {
                    resolvedItems.value = emptyList()
                    entryDisplayName.value = routeArgs.entryDisplayName
                    mode.value = FilesMode.STANDARD
                    preparing.value = null
                    selectedIds.value = emptySet()
                    preferences.value = FilePreferencesState.disabled()
                    resolverError.value = result.failure.toMessage()
                    isResolving.value = false
                }
            }
        }
    }

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun toggleSelection(itemId: SelectableItemId) {
        selectedIds.value = selectedIds.value.toMutableSet().also { ids ->
            if (!ids.add(itemId)) {
                ids.remove(itemId)
            }
        }
        viewModelScope.launch {
            diagnosticsFacade.record(
                domain = currentFilesDomain(),
                event = "selection-changed",
                outcome = "observed",
                snapshotId = routeArgs.snapshotId.value,
                context = mapOf(
                    "entryId" to routeArgs.entryId.value,
                    "selectedCount" to selectedIds.value.size.toString()
                )
            )
        }
    }

    fun selectAllVisible() {
        val visibleIds = currentVisibleRows().map(SelectableRowModel::itemId)
        selectedIds.value = selectedIds.value + visibleIds
        viewModelScope.launch {
            diagnosticsFacade.record(
                domain = currentFilesDomain(),
                event = "select-all-visible",
                outcome = "observed",
                snapshotId = routeArgs.snapshotId.value,
                context = mapOf(
                    "entryId" to routeArgs.entryId.value,
                    "selectedCount" to selectedIds.value.size.toString()
                )
            )
        }
    }

    fun deselectVisible() {
        val visibleIds = currentVisibleRows().map(SelectableRowModel::itemId).toSet()
        selectedIds.value = selectedIds.value - visibleIds
        viewModelScope.launch {
            diagnosticsFacade.record(
                domain = currentFilesDomain(),
                event = "select-none-visible",
                outcome = "observed",
                snapshotId = routeArgs.snapshotId.value,
                context = mapOf(
                    "entryId" to routeArgs.entryId.value,
                    "selectedCount" to selectedIds.value.size.toString()
                )
            )
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun updatePreferences(preferences: FilePreferencesState) {
        this.preferences.value = preferences.normalized()
    }

    suspend fun queueSelected(): EnqueueResult {
        val itemsById = resolvedItems.value.associateBy { it.itemId }
        val selected = selectedIds.value.mapNotNull(itemsById::get)
        if (selected.isEmpty()) {
            return EnqueueResult.Rejected("No files selected")
        }

        val normalizedPreferences = preferences.value.normalized()
        val inputs = selected.map { item -> item.toQueueTaskInput(normalizedPreferences) }
        val result = downloadsFacade.enqueue(inputs)
        diagnosticsFacade.record(
            domain = currentFilesDomain(),
            event = "queue-selected",
            outcome = when (result) {
                is EnqueueResult.Enqueued,
                is EnqueueResult.EnqueuedPendingDispatch -> "succeeded"
                is EnqueueResult.Rejected -> "rejected"
                is EnqueueResult.Failed -> "failed"
            },
            snapshotId = routeArgs.snapshotId.value,
            context = buildMap {
                put("entryId", routeArgs.entryId.value)
                put("selectedCount", selected.size.toString())
                put("applyRename", normalizedPreferences.applyRename.toString())
                put("unarchive", normalizedPreferences.unarchiveEnabled.toString())
                put(
                    "recursiveUnarchive",
                    normalizedPreferences.recursiveUnarchiveEnabled.toString()
                )
                when (result) {
                    is EnqueueResult.Rejected -> put("message", result.message)
                    is EnqueueResult.Failed -> put("message", result.message)
                    is EnqueueResult.EnqueuedPendingDispatch -> put("message", result.message)
                    is EnqueueResult.Enqueued -> Unit
                }
            }
        )
        return result
    }

    @Suppress("LongParameterList")
    private fun buildState(
        entryDisplayName: String,
        browseMode: FilesMode,
        isResolving: Boolean,
        preparing: FilesPreparingState?,
        items: List<SelectableItem>,
        selectedIds: Set<SelectableItemId>,
        filePreferences: FilePreferencesState,
        searchQuery: String,
        resolverError: String?,
        listAnchor: Int
    ): FilesUiState {
        val filteredItems = items
            .filter { item ->
                searchQuery.isBlank() ||
                    item.originalDisplayName.contains(searchQuery, ignoreCase = true)
            }
        val rows = filteredItems.map { item -> item.toRowModel() }

        return FilesUiState(
            entryDisplayName = entryDisplayName,
            mode = browseMode,
            isResolving = isResolving,
            preparing = preparing,
            rows = rows,
            selectedIds = selectedIds,
            preferences = filePreferences.normalized(),
            searchQuery = searchQuery,
            resolverError = resolverError,
            listAnchor = listAnchor
        )
    }

    private fun currentVisibleRows(): List<SelectableRowModel> {
        val query = searchQuery.value
        val visibleItems = resolvedItems.value.filter { item ->
            query.isBlank() ||
                item.originalDisplayName.contains(query, ignoreCase = true)
        }
        return visibleItems.map { item ->
            item.toRowModel()
        }
    }

    private fun currentFilesDomain(): DiagnosticDomain = when (mode.value) {
        FilesMode.STANDARD -> DiagnosticDomain.FILES
        FilesMode.ARCHIVE_SELECTION -> DiagnosticDomain.ARCHIVE_SELECTION
    }

    private fun BrowseFailure.toMessage(): String = when (this) {
        is BrowseFailure.MissingEntry -> "Source entry ${entryId.value} is missing."
        is BrowseFailure.StandardResolver -> message
        is BrowseFailure.ArchiveResolver -> message
        is BrowseFailure.ArchiveEnumeration -> message
    }

    private fun SelectableItem.toRowModel(): SelectableRowModel = SelectableRowModel(
        itemId = itemId,
        originalDisplayName = originalDisplayName,
        sizeBytes = sizeBytes,
        partLabel = sourceContext.partLabel,
        providerFileId = sourceContext.providerFileId
    )

    private fun SelectableItem.toQueueTaskInput(preferences: FilePreferencesState): QueueTaskInput =
        QueueTaskInput(
            snapshotId = snapshotId,
            entryId = entryId,
            selectedItemId = itemId,
            originalDisplayName = originalDisplayName,
            originalSizeBytes = sizeBytes,
            sourceMetadata = SourceQueueMetadata(
                entryDisplayName = sourceContext.entryDisplayName,
                partLabel = sourceContext.partLabel,
                providerFileId = sourceContext.providerFileId
            ),
            namingIntent = NamingIntent(
                applyRename = preferences.renameAvailable && preferences.applyRename,
                renameRule = if (preferences.renameAvailable && preferences.applyRename) {
                    selectionPolicy.renameRule
                } else {
                    null
                }
            ),
            unarchiveIntent = preferences.unarchiveAvailable && preferences.unarchiveEnabled,
            recursiveUnarchiveIntent = preferences.recursiveUnarchiveAvailable &&
                preferences.recursiveUnarchiveEnabled,
            storageTarget = StorageTargetContext(subfolder = sourceContext.outputSubfolder),
            executionContext = when (this) {
                is SelectableItem.StandardFile -> QueueExecutionContext.StandardFile(
                    selectionIntent = selectionIntent
                )

                is SelectableItem.ArchiveEntry -> QueueExecutionContext.ArchiveEntry(
                    preparationKey = preparationKey,
                    archiveEntryIdentity = archiveEntryIdentity
                )
            }
        )

    override fun onCleared() {
        preparingPollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val PREPARING_POLL_INTERVAL_MILLIS = 2_000L
    }
}
