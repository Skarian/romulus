package com.romulus.mobile.feature.files

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romulus.mobile.domain.files.FileOption
import com.romulus.mobile.domain.source.SourceEntry
import kotlinx.coroutines.launch

data class FilesResolutionState(
    val isLoading: Boolean = false,
    val files: List<FileOption> = emptyList(),
    val error: String? = null
)

class FilesScreenViewModel : ViewModel() {
    private var activeSnapshotId: String? = null
    private val stateByEntryKey = mutableStateMapOf<String, FilesResolutionState>()

    fun stateFor(key: String): FilesResolutionState {
        return stateByEntryKey[key] ?: FilesResolutionState()
    }

    fun updateActiveSnapshot(snapshotId: String) {
        if (activeSnapshotId == snapshotId) return
        val keepPrefix = "$snapshotId:"
        val staleKeys = stateByEntryKey.keys.filterNot { it.startsWith(keepPrefix) }
        staleKeys.forEach { staleKey ->
            stateByEntryKey.remove(staleKey)
        }
        activeSnapshotId = snapshotId
    }

    fun ensureLoaded(
        key: String,
        entry: SourceEntry,
        resolver: suspend (SourceEntry) -> Result<List<FileOption>>
    ) {
        val current = stateByEntryKey[key]
        if (current != null && (current.isLoading || current.files.isNotEmpty() || !current.error.isNullOrBlank())) {
            return
        }
        stateByEntryKey[key] = FilesResolutionState(isLoading = true)
        viewModelScope.launch {
            val result = resolver(entry)
            stateByEntryKey[key] = result.fold(
                onSuccess = { files -> FilesResolutionState(files = files) },
                onFailure = { throwable ->
                    FilesResolutionState(
                        isLoading = false,
                        error = throwable.message ?: "Unable to resolve file list"
                    )
                }
            )
        }
    }
}
