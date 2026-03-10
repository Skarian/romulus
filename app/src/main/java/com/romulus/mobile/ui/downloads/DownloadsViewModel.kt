@file:Suppress("ClassSignature", "MaximumLineLength")

package com.romulus.mobile.ui.downloads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.downloads.queue.DownloadsProjection
import com.romulus.mobile.downloads.queue.QueueActionCommand
import com.romulus.mobile.downloads.queue.TaskId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val projection: DownloadsProjection,
    val detailTaskId: TaskId?,
    val clearHistoryDialog: ClearHistoryDialogState?
)

data class ClearHistoryDialogState(val includeFailed: Boolean, val errorMessage: String? = null)

class DownloadsViewModel(
    private val downloadsFacade: DownloadsFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val detailTaskId = MutableStateFlow<TaskId?>(null)
    private val clearHistoryDialog = MutableStateFlow<ClearHistoryDialogState?>(null)

    init {
        savedStateHandle.keys()
    }

    val state: StateFlow<DownloadsUiState> = combine(
        downloadsFacade.observeDownloadsProjection(),
        detailTaskId,
        clearHistoryDialog
    ) { projection, detailId, clearDialog ->
        DownloadsUiState(
            projection = projection,
            detailTaskId = detailId,
            clearHistoryDialog = clearDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DownloadsUiState(
            projection = downloadsFacade.observeDownloadsProjection().value,
            detailTaskId = detailTaskId.value,
            clearHistoryDialog = clearHistoryDialog.value
        )
    )

    fun performAction(command: QueueActionCommand) {
        viewModelScope.launch {
            downloadsFacade.performAction(command)
        }
    }

    fun openDetails(taskId: TaskId) {
        detailTaskId.value = taskId
    }

    fun dismissDetails() {
        detailTaskId.value = null
    }

    fun openClearHistory() {
        clearHistoryDialog.value = ClearHistoryDialogState(includeFailed = false)
    }

    fun dismissClearHistory() {
        clearHistoryDialog.value = null
    }

    fun confirmClearHistory(includeFailed: Boolean) {
        viewModelScope.launch {
            downloadsFacade.clearHistory(includeFailed).fold(
                onSuccess = {
                    clearHistoryDialog.value = null
                },
                onFailure = { throwable ->
                    clearHistoryDialog.value = ClearHistoryDialogState(
                        includeFailed = includeFailed,
                        errorMessage = throwable.message ?: "Clear history failed"
                    )
                }
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
