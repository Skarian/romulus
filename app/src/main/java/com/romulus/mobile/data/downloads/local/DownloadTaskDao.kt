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
        ORDER BY createdAtEpochMs DESC, entryIndex ASC, partIndex ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE state IN (:states)
        ORDER BY createdAtEpochMs ASC, entryIndex ASC, partIndex ASC, id ASC
        """
    )
    suspend fun findByStates(states: List<DownloadState>): List<DownloadTaskEntity>

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

    @Query("DELETE FROM download_tasks WHERE state IN (:states)")
    suspend fun deleteByStates(states: List<DownloadState>)
}
