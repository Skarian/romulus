package com.romulus.mobile.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romulus.mobile.app.startup.BrokenSetting
import com.romulus.mobile.app.startup.ShellReadiness
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.snapshot.HomeSourceState
import com.romulus.mobile.source.snapshot.HomeSourceWarning
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceRefreshResult
import com.romulus.mobile.source.snapshot.SourceRefreshTrigger
import com.romulus.mobile.ui.files.FilesRouteArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeMode {
    data class InvalidSettings(val broken: Set<BrokenSetting>) : HomeMode

    data class SourceLoadError(val message: String, val retryVisible: Boolean) : HomeMode

    data class Content(
        val snapshotId: SnapshotId,
        val rows: List<HomeRowModel>,
        val warning: HomeContentWarning?
    ) : HomeMode
}

sealed interface HomeContentWarning {
    data class LatestRefreshFailed(val message: String) : HomeContentWarning

    data class MissingSnapshotFallback(val message: String) : HomeContentWarning
}

data class HomeUiState(
    val mode: HomeMode,
    val searchQuery: String,
    val searchDialogOpen: Boolean,
    val refreshVisible: Boolean
)

sealed interface HomeEffect {
    data class RefreshFeedback(val message: String, val success: Boolean) : HomeEffect
}

data class HomeRowModel(
    val displayName: String,
    val folderContext: String,
    val routeArgs: FilesRouteArgs
)

class HomeViewModel(
    private val shellReadiness: StateFlow<ShellReadiness>,
    private val sourceFacade: SourceFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val searchDialogOpen = MutableStateFlow(false)
    private val mutableEffects = MutableSharedFlow<HomeEffect>()
    val effects: Flow<HomeEffect> = mutableEffects.asSharedFlow()

    init {
        savedStateHandle.keys()
        viewModelScope.launch {
            sourceFacade.revalidateReadiness()
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        shellReadiness,
        sourceFacade.observeHomeState(),
        searchQuery,
        searchDialogOpen
    ) { readiness, homeState, query, dialogOpen ->
        buildState(
            readiness = readiness,
            homeState = homeState,
            searchQuery = query,
            searchDialogOpen = dialogOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = buildState(
            readiness = shellReadiness.value,
            homeState = sourceFacade.observeHomeState().value,
            searchQuery = searchQuery.value,
            searchDialogOpen = searchDialogOpen.value
        )
    )

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun openSearch() {
        searchDialogOpen.value = true
    }

    fun closeSearch() {
        searchDialogOpen.value = false
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    fun refresh() {
        viewModelScope.launch {
            val result = sourceFacade.refresh(SourceRefreshTrigger.HomeManualRefresh)
            val effect = when (result) {
                is SourceRefreshResult.Replaced -> HomeEffect.RefreshFeedback(
                    message = "Source refreshed.",
                    success = true
                )

                is SourceRefreshResult.RetainedPrior -> HomeEffect.RefreshFeedback(
                    message = result.message,
                    success = false
                )

                is SourceRefreshResult.FailedWithoutSnapshot -> HomeEffect.RefreshFeedback(
                    message = result.message,
                    success = false
                )
            }
            mutableEffects.emit(effect)
        }
    }

    private fun buildState(
        readiness: ShellReadiness,
        homeState: HomeSourceState,
        searchQuery: String,
        searchDialogOpen: Boolean
    ): HomeUiState {
        if (readiness.brokenSettings.isNotEmpty()) {
            return HomeUiState(
                mode = HomeMode.InvalidSettings(readiness.brokenSettings),
                searchQuery = searchQuery,
                searchDialogOpen = searchDialogOpen,
                refreshVisible = false
            )
        }

        return when (homeState) {
            is HomeSourceState.SourceLoadError -> HomeUiState(
                mode = HomeMode.SourceLoadError(
                    message = homeState.message,
                    retryVisible = homeState.refreshAvailable
                ),
                searchQuery = searchQuery,
                searchDialogOpen = searchDialogOpen,
                refreshVisible = homeState.refreshAvailable
            )

            is HomeSourceState.Content -> {
                val candidateRows = homeState.rows
                    .map { row ->
                        HomeRowModel(
                            displayName = row.displayName,
                            folderContext = row.folderContext,
                            routeArgs = FilesRouteArgs(
                                snapshotId = homeState.snapshotId,
                                entryId = row.entryId
                            )
                        )
                    }
                val rows = candidateRows.filter { row ->
                    searchQuery.isBlank() ||
                        row.displayName.contains(searchQuery, ignoreCase = true) ||
                        row.folderContext.contains(searchQuery, ignoreCase = true)
                }
                HomeUiState(
                    mode = HomeMode.Content(
                        snapshotId = homeState.snapshotId,
                        rows = rows,
                        warning = when (val warning = homeState.warning) {
                            null -> null
                            is HomeSourceWarning.LatestRefreshFailed ->
                                HomeContentWarning.LatestRefreshFailed(warning.message)

                            is HomeSourceWarning.MissingSnapshotFallback ->
                                HomeContentWarning.MissingSnapshotFallback(warning.message)
                        }
                    ),
                    searchQuery = searchQuery,
                    searchDialogOpen = searchDialogOpen,
                    refreshVisible = homeState.refreshAvailable
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
