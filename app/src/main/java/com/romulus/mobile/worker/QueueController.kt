package com.romulus.mobile.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.romulus.mobile.data.downloads.QueueCommandBus
import com.romulus.mobile.data.downloads.QueueRuntimeStore
import com.romulus.mobile.data.downloads.QueueTaskCommand
import com.romulus.mobile.data.downloads.QueuedDownload
import com.romulus.mobile.data.downloads.QueueRepository
import com.romulus.mobile.data.files.SafFileStore
import com.romulus.mobile.domain.downloads.DownloadState

class QueueController(
    private val context: Context,
    private val queueRepository: QueueRepository,
    private val safFileStore: SafFileStore,
    private val queueRuntimeStore: QueueRuntimeStore,
    private val queueCommandBus: QueueCommandBus
) {

    suspend fun enqueue(items: List<QueuedDownload>) {
        val created = queueRepository.enqueue(items)
        created.forEach { task ->
            queueRuntimeStore.upsertTask(task)
            queueRuntimeStore.updateLiveProgress(task.id, task.bytesDownloaded, task.totalBytes)
        }
        refreshRunSummary()
        schedule()
    }

    suspend fun retry(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        queueRuntimeStore.clearLiveProgress(taskId)
        queueCommandBus.clear(taskId)
        val updated = task.copy(
            state = DownloadState.QUEUED,
            attemptCount = 0,
            retryAtEpochMs = null,
            lastFailureReason = null
        )
        queueRepository.updateTask(updated)
        queueRuntimeStore.upsertTask(updated)
        refreshRunSummary()
        schedule()
    }

    suspend fun restart(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        val deleted = safFileStore.deleteIfExists(task.outputUri)
        if (!deleted) return
        queueRuntimeStore.clearLiveProgress(taskId)
        queueCommandBus.clear(taskId)
        val updated = task.copy(
            state = DownloadState.QUEUED,
            attemptCount = 0,
            retryAtEpochMs = null,
            lastFailureReason = null,
            bytesDownloaded = 0,
            partialExists = false,
            outputUri = null
        )
        queueRepository.updateTask(updated)
        queueRuntimeStore.upsertTask(updated)
        refreshRunSummary()
        schedule()
    }

    suspend fun cancel(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        if (!task.state.isActive) return
        if (task.state == DownloadState.RUNNING || task.state == DownloadState.RESOLVING_LINK) {
            queueCommandBus.issue(taskId, QueueTaskCommand.CANCEL)
        }
        val updated = task.copy(
            state = DownloadState.CANCELLED,
            retryAtEpochMs = null
        )
        queueRepository.updateTask(updated)
        queueRuntimeStore.upsertTask(updated)
        queueRuntimeStore.clearLiveProgress(taskId)
        refreshRunSummary()
    }

    suspend fun pause(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        if (task.state != DownloadState.RUNNING && task.state != DownloadState.RESOLVING_LINK) return
        queueCommandBus.issue(taskId, QueueTaskCommand.PAUSE)
        val updated = task.copy(
            state = DownloadState.PAUSED
        )
        queueRepository.updateTask(updated)
        queueRuntimeStore.upsertTask(updated)
        refreshRunSummary()
    }

    suspend fun resume(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        if (task.state != DownloadState.PAUSED) return
        queueCommandBus.clear(taskId)
        val updated = task.copy(
            state = DownloadState.QUEUED
        )
        queueRepository.updateTask(updated)
        queueRuntimeStore.upsertTask(updated)
        refreshRunSummary()
        schedule()
    }

    suspend fun deletePartial(taskId: String) {
        val task = queueRepository.findTask(taskId) ?: return
        val deleted = safFileStore.deleteIfExists(task.outputUri)
        if (!deleted) return
        queueRuntimeStore.clearLiveProgress(taskId)
        val updated = task.copy(
            partialExists = false
        )
        queueRepository.updateTask(updated)
        queueRuntimeStore.upsertTask(updated)
        refreshRunSummary()
    }

    fun schedule() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<QueueOrchestratorWorker>().build()
            )
    }

    private suspend fun refreshRunSummary() {
        queueRuntimeStore.syncLatestRunSummary(queueRepository.findLatestRunSummary())
    }

    companion object {
        const val UNIQUE_WORK_NAME = "romulus-queue-orchestrator"
    }
}
