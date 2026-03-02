package com.romulus.mobile.data.downloads.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue_runs")
data class QueueRunEntity(
    @PrimaryKey
    val runId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val cancelledCount: Int
)
