package com.romulus.mobile.data.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TaskProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?
)

class TaskProgressTracker {
    private val progressByTaskId = MutableStateFlow<Map<String, TaskProgress>>(emptyMap())

    fun observe(): StateFlow<Map<String, TaskProgress>> = progressByTaskId.asStateFlow()

    fun update(taskId: String, bytesDownloaded: Long, totalBytes: Long?) {
        val normalizedBytes = bytesDownloaded.coerceAtLeast(0L)
        progressByTaskId.update { current ->
            val existing = current[taskId]
            if (existing != null &&
                existing.bytesDownloaded == normalizedBytes &&
                existing.totalBytes == totalBytes
            ) {
                current
            } else {
                current + (taskId to TaskProgress(normalizedBytes, totalBytes))
            }
        }
    }

    fun remove(taskId: String) {
        progressByTaskId.update { current ->
            if (current.containsKey(taskId)) current - taskId else current
        }
    }

    fun pruneToTaskIds(taskIds: Set<String>) {
        progressByTaskId.update { current ->
            current.filterKeys { it in taskIds }
        }
    }
}
