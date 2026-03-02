package com.romulus.mobile.data.downloads

import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.domain.downloads.DownloadState
import com.romulus.mobile.domain.downloads.QueueRunCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TaskProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?
)

class QueueRuntimeStore {
    private val lock = Any()
    private val durableTasksById = LinkedHashMap<String, DurableTaskSnapshot>()
    private var latestRunSummary: QueueRunSummary? = null
    private val liveProgressByTaskId = MutableStateFlow<Map<String, TaskProgress>>(emptyMap())

    fun observeProgress(): StateFlow<Map<String, TaskProgress>> = liveProgressByTaskId.asStateFlow()

    fun syncTasks(tasks: List<DownloadTaskEntity>) {
        val summary = synchronized(lock) { latestRunSummary }
        syncTasks(tasks, summary)
    }

    fun syncTasks(tasks: List<DownloadTaskEntity>, runSummary: QueueRunSummary?) {
        synchronized(lock) {
            durableTasksById.clear()
            tasks.forEach { task ->
                durableTasksById[task.id] = DurableTaskSnapshot.fromEntity(task)
            }
            latestRunSummary = runSummary?.normalized()
        }
        val validTaskIds = tasks.mapTo(mutableSetOf()) { it.id }
        liveProgressByTaskId.update { current ->
            current.filterKeys { it in validTaskIds }
        }
    }

    fun upsertTask(task: DownloadTaskEntity) {
        synchronized(lock) {
            durableTasksById[task.id] = DurableTaskSnapshot.fromEntity(task)
        }
        if (task.state.isActive) {
            updateLiveProgress(task.id, task.bytesDownloaded, task.totalBytes)
        } else {
            clearLiveProgress(task.id)
        }
    }

    fun syncLatestRunSummary(runSummary: QueueRunSummary?) {
        synchronized(lock) {
            latestRunSummary = runSummary?.normalized()
        }
    }

    fun updateLiveProgress(taskId: String, bytesDownloaded: Long, totalBytes: Long?) {
        val normalizedBytes = bytesDownloaded.coerceAtLeast(0L)
        val normalizedTotal = totalBytes?.coerceAtLeast(0L)
        liveProgressByTaskId.update { current ->
            val existing = current[taskId]
            if (existing != null &&
                existing.bytesDownloaded == normalizedBytes &&
                existing.totalBytes == normalizedTotal
            ) {
                current
            } else {
                current + (taskId to TaskProgress(normalizedBytes, normalizedTotal))
            }
        }
    }

    fun clearLiveProgress(taskId: String) {
        liveProgressByTaskId.update { current ->
            if (current.containsKey(taskId)) current - taskId else current
        }
    }

    fun latestRunCounter(): QueueRunCounter {
        val (tasksInLatestRun, runSummary, stableTotalCount) = synchronized(lock) {
            val summary = latestRunSummary
            val latestRunId = summary?.runId ?: durableTasksById.values
                .maxByOrNull { it.createdAtEpochMs }
                ?.runId
                ?: return emptyCounter()
            val runTasks = durableTasksById.values.filter { it.runId == latestRunId }
            val summaryForRun = summary?.takeIf { it.runId == latestRunId }
            val stableTotal = maxOf(summaryForRun?.totalCount ?: 0, runTasks.size)
            Triple(runTasks, summaryForRun, stableTotal)
        }
        if (tasksInLatestRun.isEmpty() && runSummary == null) return emptyCounter()

        val liveProgress = liveProgressByTaskId.value
        var completedCount = 0
        var failedCount = 0
        var cancelledCount = 0
        var downloadedBytes = 0L
        var totalBytes = 0L
        var hasUnknownTotalBytes = false
        var hasCountableByteTask = false

        tasksInLatestRun.forEach { task ->
            when (task.state) {
                DownloadState.COMPLETED -> completedCount += 1
                DownloadState.FAILED -> failedCount += 1
                DownloadState.CANCELLED -> cancelledCount += 1
                else -> Unit
            }

            if (task.state == DownloadState.FAILED || task.state == DownloadState.CANCELLED) {
                return@forEach
            }

            val progress = liveProgress[task.id]
            val mergedBytes = maxOf(task.bytesDownloaded, progress?.bytesDownloaded ?: 0L)
            val mergedTotal = progress?.totalBytes ?: task.totalBytes
            hasCountableByteTask = true
            downloadedBytes += mergedBytes.coerceAtLeast(0L)
            if (mergedTotal == null || mergedTotal <= 0L) {
                hasUnknownTotalBytes = true
            } else {
                totalBytes += mergedTotal
            }
        }

        val completedDenominator = (stableTotalCount - cancelledCount - failedCount)
            .coerceAtLeast(completedCount)
            .coerceAtLeast(0)

        return QueueRunCounter(
            completedCount = completedCount,
            totalCount = completedDenominator,
            failedCount = failedCount,
            cancelledCount = cancelledCount,
            downloadedBytes = downloadedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            hasUnknownTotalBytes = !hasCountableByteTask || hasUnknownTotalBytes
        )
    }

    private fun emptyCounter(): QueueRunCounter {
        return QueueRunCounter(
            completedCount = 0,
            totalCount = 0,
            failedCount = 0,
            cancelledCount = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            hasUnknownTotalBytes = true
        )
    }

    private data class DurableTaskSnapshot(
        val id: String,
        val runId: String,
        val createdAtEpochMs: Long,
        val state: DownloadState,
        val bytesDownloaded: Long,
        val totalBytes: Long?
    ) {
        companion object {
            fun fromEntity(task: DownloadTaskEntity): DurableTaskSnapshot {
                return DurableTaskSnapshot(
                    id = task.id,
                    runId = task.runId,
                    createdAtEpochMs = task.createdAtEpochMs,
                    state = task.state,
                    bytesDownloaded = task.bytesDownloaded.coerceAtLeast(0L),
                    totalBytes = task.totalBytes?.coerceAtLeast(0L)
                )
            }
        }
    }
}

private fun QueueRunSummary.normalized(): QueueRunSummary {
    return copy(
        totalCount = totalCount.coerceAtLeast(0)
    )
}
