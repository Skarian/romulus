package com.romulus.mobile.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.romulus.mobile.data.downloads.TaskProgressTracker
import com.romulus.mobile.data.files.SafFileStore
import com.romulus.mobile.data.downloads.QueuedDownload
import com.romulus.mobile.data.downloads.QueueRepository
import com.romulus.mobile.domain.downloads.DownloadState

class QueueController(
    private val context: Context,
    private val queueRepository: QueueRepository,
    private val safFileStore: SafFileStore,
    private val taskProgressTracker: TaskProgressTracker
) {

    suspend fun enqueue(items: List<QueuedDownload>) {
        queueRepository.enqueue(items)
        schedule()
    }

    suspend fun retry(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        taskProgressTracker.remove(taskId)
        queueRepository.updateTask(
            task.copy(
                state = DownloadState.QUEUED,
                attemptCount = 0,
                retryAtEpochMs = null,
                lastFailureReason = null
            )
        )
        schedule()
    }

    suspend fun restart(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        val deleted = safFileStore.deleteIfExists(task.outputUri)
        if (!deleted) return
        taskProgressTracker.remove(taskId)
        queueRepository.updateTask(
            task.copy(
                state = DownloadState.QUEUED,
                attemptCount = 0,
                retryAtEpochMs = null,
                lastFailureReason = null,
                bytesDownloaded = 0,
                partialExists = false,
                outputUri = null
            )
        )
        schedule()
    }

    suspend fun cancel(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        if (!task.state.isActive) return
        queueRepository.updateTask(
            task.copy(
                state = DownloadState.CANCELLED,
                retryAtEpochMs = null
            )
        )
    }

    suspend fun pause(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        if (task.state != DownloadState.RUNNING && task.state != DownloadState.RESOLVING_LINK) return
        queueRepository.updateTask(
            task.copy(
                state = DownloadState.PAUSED
            )
        )
    }

    suspend fun resume(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        if (task.state != DownloadState.PAUSED) return
        queueRepository.updateTask(
            task.copy(
                state = DownloadState.QUEUED
            )
        )
        schedule()
    }

    suspend fun clearCompleted() {
        queueRepository.clearTerminalHistory()
    }

    suspend fun deletePartial(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        val deleted = safFileStore.deleteIfExists(task.outputUri)
        if (!deleted) return
        taskProgressTracker.remove(taskId)
        queueRepository.updateTask(
            task.copy(
                partialExists = false
            )
        )
    }

    fun schedule() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<QueueOrchestratorWorker>().build()
            )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "romulus-queue-orchestrator"
    }
}
