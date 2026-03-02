package com.romulus.mobile.data.downloads.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.romulus.mobile.domain.downloads.DownloadState

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["runId"]),
        Index(value = ["snapshotId", "entryIndex", "partIndex", "originalFilename", "sizeBytes"])
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val runId: String,
    val snapshotId: String,
    val entryIndex: Int,
    val sourceDisplayName: String,
    val partIndex: Int,
    val originalFilename: String,
    val sizeBytes: Long,
    val displayFilename: String,
    val subfolder: String,
    val state: DownloadState,
    val attemptCount: Int,
    val maxAttempts: Int,
    val retryAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastFailureReason: String?,
    val magnetUrl: String,
    val torrentFileId: Int,
    val partName: String?,
    val outputUri: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val partialExists: Boolean
)
