package com.romulus.mobile.data.downloads

import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.domain.downloads.DownloadState
import com.romulus.mobile.domain.downloads.QueueRunCounter
import kotlinx.coroutines.flow.Flow

data class QueuedDownload(
    val snapshotId: String,
    val entryIndex: Int,
    val sourceDisplayName: String,
    val partIndex: Int,
    val originalFilename: String,
    val sizeBytes: Long,
    val displayFilename: String,
    val subfolder: String,
    val magnetUrl: String,
    val torrentFileId: Int,
    val partName: String?
)

interface QueueRepository {
    fun observeTasks(): Flow<List<DownloadTaskEntity>>

    suspend fun enqueue(items: List<QueuedDownload>): List<DownloadTaskEntity>

    suspend fun findTask(id: String): DownloadTaskEntity?

    suspend fun findActiveTasks(): List<DownloadTaskEntity>

    suspend fun findRunnableTasks(nowEpochMs: Long, limit: Int): List<DownloadTaskEntity>

    suspend fun updateTask(task: DownloadTaskEntity)

    suspend fun updateTaskProgress(task: DownloadTaskEntity)

    suspend fun clearTerminalHistory()

    suspend fun activeCount(): Int

    suspend fun latestRunCounter(): QueueRunCounter

    suspend fun closeRunIfNoActive()
}
