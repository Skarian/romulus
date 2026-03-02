package com.romulus.mobile.data.downloads.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.romulus.mobile.domain.downloads.DownloadState
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query(
        """
        SELECT * FROM download_tasks
        ORDER BY queueIndex ASC, id ASC
        """
    )
    suspend fun findAll(): List<DownloadTaskEntity>

    @Query(
        """
        SELECT * FROM download_tasks
        ORDER BY queueIndex ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE state IN (:states)
        ORDER BY queueIndex ASC, id ASC
        """
    )
    suspend fun findByStates(states: List<DownloadState>): List<DownloadTaskEntity>

    @Query("SELECT MAX(queueIndex) FROM download_tasks")
    suspend fun findMaxQueueIndex(): Long?

    @Query("SELECT COUNT(*) FROM download_tasks WHERE state IN (:states)")
    suspend fun countByStates(states: List<DownloadState>): Int

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun findById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE runId = :runId")
    suspend fun findByRunId(runId: String): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<DownloadTaskEntity>)

    @Query(
        """
        UPDATE download_tasks
        SET bytesDownloaded = CASE
            WHEN bytesDownloaded > :bytesDownloaded THEN bytesDownloaded
            ELSE :bytesDownloaded
        END,
            totalBytes = COALESCE(:totalBytes, totalBytes),
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :taskId
        """
    )
    suspend fun updateCheckpoint(
        taskId: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        updatedAtEpochMs: Long
    )

}
