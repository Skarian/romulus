package com.romulus.mobile.data.downloads

import com.romulus.mobile.core.time.ClockProvider
import com.romulus.mobile.data.downloads.local.DownloadTaskDao
import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.data.downloads.local.QueueRunDao
import com.romulus.mobile.data.downloads.local.QueueRunEntity
import com.romulus.mobile.domain.downloads.DownloadState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomQueueRepository(
    private val taskDao: DownloadTaskDao,
    private val runDao: QueueRunDao,
    private val clockProvider: ClockProvider
) : QueueRepository {

    override fun observeTasks(): Flow<List<DownloadTaskEntity>> = taskDao.observeAll()

    override suspend fun findAllTasks(): List<DownloadTaskEntity> = taskDao.findAll()

    override suspend fun enqueue(items: List<QueuedDownload>): List<DownloadTaskEntity> {
        if (items.isEmpty()) return emptyList()
        val now = clockProvider.nowEpochMillis()
        val initialQueueIndex = (taskDao.findMaxQueueIndex() ?: -1L) + 1L
        val run = runDao.findOpenRun() ?: QueueRunEntity(
            runId = UUID.randomUUID().toString(),
            startedAtEpochMs = now,
            endedAtEpochMs = null,
            totalCount = 0,
            successCount = 0,
            failedCount = 0,
            cancelledCount = 0
        )
        val newRun = run.copy(totalCount = run.totalCount + items.size)
        runDao.upsert(newRun)

        val created = items.mapIndexed { index, item ->
            DownloadTaskEntity(
                id = UUID.randomUUID().toString(),
                runId = newRun.runId,
                queueIndex = initialQueueIndex + index,
                snapshotId = item.snapshotId,
                entryIndex = item.entryIndex,
                sourceDisplayName = item.sourceDisplayName,
                partIndex = item.partIndex,
                originalFilename = item.originalFilename,
                sizeBytes = item.sizeBytes,
                displayFilename = item.displayFilename,
                subfolder = item.subfolder,
                state = DownloadState.QUEUED,
                attemptCount = 0,
                maxAttempts = MAX_ATTEMPTS,
                retryAtEpochMs = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                lastFailureReason = null,
                magnetUrl = item.magnetUrl,
                torrentFileId = item.torrentFileId,
                partName = item.partName,
                outputUri = null,
                bytesDownloaded = 0,
                totalBytes = item.sizeBytes,
                partialExists = false
            )
        }
        taskDao.upsertAll(created)
        return created
    }

    override suspend fun findTask(id: String): DownloadTaskEntity? = taskDao.findById(id)

    override suspend fun findLatestRunSummary(): QueueRunSummary? {
        return runDao.findLatestRun()?.toSummary()
    }

    override suspend fun findActiveTasks(): List<DownloadTaskEntity> {
        return taskDao.findByStates(ACTIVE_STATES)
    }

    override suspend fun findRunnableTasks(nowEpochMs: Long, limit: Int): List<DownloadTaskEntity> {
        val queued = taskDao.findByStates(listOf(DownloadState.QUEUED, DownloadState.RETRYING))
        val retryScheduled = taskDao.findByStates(listOf(DownloadState.RETRY_SCHEDULED))
            .filter { (it.retryAtEpochMs ?: nowEpochMs) <= nowEpochMs }
            .map { it.copy(state = DownloadState.RETRYING) }
        return (queued + retryScheduled)
            .sortedWith(
                compareBy<DownloadTaskEntity>(
                    { it.queueIndex },
                    { it.id }
                )
            )
            .take(limit)
    }

    override suspend fun updateTask(task: DownloadTaskEntity) {
        taskDao.upsert(task)
        updateRunCounters(task.runId)
    }

    override suspend fun updateTaskCheckpoint(
        taskId: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        updatedAtEpochMs: Long
    ) {
        taskDao.updateCheckpoint(
            taskId = taskId,
            bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
            totalBytes = totalBytes?.coerceAtLeast(0L),
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    override suspend fun activeCount(): Int {
        return taskDao.countByStates(ACTIVE_STATES)
    }

    override suspend fun closeRunIfNoActive() {
        if (activeCount() > 0) return
        val run = runDao.findOpenRun() ?: return
        runDao.upsert(run.copy(endedAtEpochMs = clockProvider.nowEpochMillis()))
    }

    private suspend fun updateRunCounters(runId: String) {
        val run = runDao.findById(runId) ?: return
        val tasks = taskDao.findByRunId(runId)
        val totalCount = maxOf(run.totalCount, tasks.size)
        val endedAt = if (tasks.none { it.state.isActive }) clockProvider.nowEpochMillis() else null

        runDao.upsert(
            run.copy(
                endedAtEpochMs = endedAt,
                totalCount = totalCount
            )
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val ACTIVE_STATES = listOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING_LINK,
            DownloadState.RUNNING,
            DownloadState.PAUSED,
            DownloadState.RETRY_SCHEDULED,
            DownloadState.RETRYING
        )
    }
}

private fun QueueRunEntity.toSummary(): QueueRunSummary {
    return QueueRunSummary(
        runId = runId,
        totalCount = totalCount
    )
}
