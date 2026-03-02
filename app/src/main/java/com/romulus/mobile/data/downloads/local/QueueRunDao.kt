package com.romulus.mobile.data.downloads.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QueueRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: QueueRunEntity)

    @Query("SELECT * FROM queue_runs WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun findOpenRun(): QueueRunEntity?

    @Query("SELECT * FROM queue_runs ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun findLatestRun(): QueueRunEntity?
}
