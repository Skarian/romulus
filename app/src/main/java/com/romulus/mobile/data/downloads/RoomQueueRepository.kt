package com.romulus.mobile.data.downloads

import com.romulus.mobile.core.time.ClockProvider
import com.romulus.mobile.data.downloads.local.DownloadTaskDao
import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.data.downloads.local.QueueRunDao
import com.romulus.mobile.data.downloads.local.QueueRunEntity
import com.romulus.mobile.domain.downloads.DownloadState
import com.romulus.mobile.domain.downloads.QueueRunCounter
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomQueueRepository(
    private val taskDao: DownloadTaskDao,
    private val runDao: QueueRunDao,
    private val clockProvider: ClockProvider
) : QueueRepository {

    override fun observeTasks(): Flow<List<DownloadTaskEntity>> = taskDao.observeAll()

    override suspend fun enqueue(items: List<QueuedDownload>): List<DownloadTaskEntity> {
        if (items.isEmpty()) return emptyList()
        val now = clockProvider.nowEpochMillis()
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

        val created = items.map { item ->
            DownloadTaskEntity(
                id = UUID.randomUUID().toString(),
                runId = newRun.runId,
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
                    { it.createdAtEpochMs },
                    { it.entryIndex },
                    { it.partIndex },
                    { it.id }
                )
            )
            .take(limit)
    }

    override suspend fun updateTask(task: DownloadTaskEntity) {
        taskDao.upsert(task)
        updateRunCounters(task.runId)
    }

    override suspend fun clearTerminalHistory() {
        taskDao.deleteByStates(listOf(DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED))
        runDao.findLatestRun()?.let { updateRunCounters(it.runId) }
    }

    override suspend fun activeCount(): Int {
        return taskDao.countByStates(ACTIVE_STATES)
    }

    override suspend fun latestRunCounter(): QueueRunCounter {
        val run = runDao.findLatestRun() ?: return QueueRunCounter(
            completedCount = 0,
            totalCount = 0,
            failedCount = 0,
            cancelledCount = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            hasUnknownTotalBytes = true
        )
        val tasks = taskDao.findByRunId(run.runId)
        val downloadedBytes = tasks.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
        val knownTotals = tasks.mapNotNull { it.totalBytes?.takeIf { size -> size > 0L } }
        val totalBytes = knownTotals.sum()
        val hasUnknownTotalBytes = tasks.any { it.totalBytes == null || it.totalBytes <= 0L }
        return QueueRunCounter(
            completedCount = run.successCount,
            totalCount = run.totalCount,
            failedCount = run.failedCount,
            cancelledCount = run.cancelledCount,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            hasUnknownTotalBytes = hasUnknownTotalBytes
        )
    }

    override suspend fun closeRunIfNoActive() {
        if (activeCount() > 0) return
        val run = runDao.findOpenRun() ?: return
        runDao.upsert(run.copy(endedAtEpochMs = clockProvider.nowEpochMillis()))
    }

    private suspend fun updateRunCounters(runId: String) {
        val run = runDao.findLatestRun()?.takeIf { it.runId == runId } ?: return
        val tasks = taskDao.findByStates(
            listOf(
                DownloadState.QUEUED,
                DownloadState.RESOLVING_LINK,
                DownloadState.RUNNING,
                DownloadState.PAUSED,
                DownloadState.RETRY_SCHEDULED,
                DownloadState.RETRYING,
                DownloadState.COMPLETED,
                DownloadState.FAILED,
                DownloadState.CANCELLED
            )
        ).filter { it.runId == runId }

        val successCount = tasks.count { it.state == DownloadState.COMPLETED }
        val failedCount = tasks.count { it.state == DownloadState.FAILED }
        val cancelledCount = tasks.count { it.state == DownloadState.CANCELLED }
        val totalCount = maxOf(run.totalCount, tasks.size)

        val endedAt = if (tasks.none { it.state.isActive }) clockProvider.nowEpochMillis() else null

        runDao.upsert(
            run.copy(
                endedAtEpochMs = endedAt,
                totalCount = totalCount,
                successCount = successCount,
                failedCount = failedCount,
                cancelledCount = cancelledCount
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
