package com.romulus.mobile.data.downloads

import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.domain.downloads.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRuntimeStoreTest {

    @Test
    fun latestRunCounter_usesLatestRunAndLiveProgressOverlay() {
        val store = QueueRuntimeStore()
        val oldRunTask = task(
            id = "old-1",
            runId = "run-old",
            queueIndex = 0L,
            createdAtEpochMs = 100L,
            state = DownloadState.COMPLETED,
            bytesDownloaded = 1_000L,
            totalBytes = 1_000L
        )
        val activeTask = task(
            id = "new-1",
            runId = "run-new",
            queueIndex = 1L,
            createdAtEpochMs = 200L,
            state = DownloadState.RUNNING,
            bytesDownloaded = 100L,
            totalBytes = 1_000L
        )
        val queuedTask = task(
            id = "new-2",
            runId = "run-new",
            queueIndex = 2L,
            createdAtEpochMs = 201L,
            state = DownloadState.QUEUED,
            bytesDownloaded = 0L,
            totalBytes = 2_000L
        )
        store.syncTasks(listOf(oldRunTask, activeTask, queuedTask))
        store.updateLiveProgress("new-1", bytesDownloaded = 500L, totalBytes = 1_000L)

        val counter = store.latestRunCounter()

        assertEquals(0, counter.completedCount)
        assertEquals(2, counter.totalCount)
        assertEquals(0, counter.failedCount)
        assertEquals(0, counter.cancelledCount)
        assertEquals(500L, counter.downloadedBytes)
        assertEquals(3_000L, counter.totalBytes)
        assertFalse(counter.hasUnknownTotalBytes)
    }

    @Test
    fun upsertTask_terminalStateClearsLiveProgress() {
        val store = QueueRuntimeStore()
        val runningTask = task(
            id = "task-1",
            runId = "run-1",
            queueIndex = 0L,
            createdAtEpochMs = 100L,
            state = DownloadState.RUNNING,
            bytesDownloaded = 0L,
            totalBytes = 1_000L
        )
        store.syncTasks(listOf(runningTask))
        store.updateLiveProgress("task-1", bytesDownloaded = 250L, totalBytes = 1_000L)
        assertTrue(store.observeProgress().value.containsKey("task-1"))

        val completedTask = runningTask.copy(
            state = DownloadState.COMPLETED,
            bytesDownloaded = 1_000L
        )
        store.upsertTask(completedTask)

        assertFalse(store.observeProgress().value.containsKey("task-1"))
    }

    @Test
    fun syncTasks_prunesRemovedLiveProgress() {
        val store = QueueRuntimeStore()
        val first = task(
            id = "task-1",
            runId = "run-1",
            queueIndex = 0L,
            createdAtEpochMs = 100L,
            state = DownloadState.RUNNING,
            bytesDownloaded = 10L,
            totalBytes = 100L
        )
        val second = task(
            id = "task-2",
            runId = "run-1",
            queueIndex = 1L,
            createdAtEpochMs = 101L,
            state = DownloadState.RUNNING,
            bytesDownloaded = 20L,
            totalBytes = 100L
        )

        store.syncTasks(listOf(first, second))
        store.updateLiveProgress("task-1", bytesDownloaded = 15L, totalBytes = 100L)
        store.updateLiveProgress("task-2", bytesDownloaded = 25L, totalBytes = 100L)
        assertEquals(2, store.observeProgress().value.size)

        store.syncTasks(listOf(first))

        assertEquals(1, store.observeProgress().value.size)
        assertTrue(store.observeProgress().value.containsKey("task-1"))
        assertFalse(store.observeProgress().value.containsKey("task-2"))
    }

    @Test
    fun latestRunCounter_usesPersistedRunTotalWhenTerminalRowsAreCleared() {
        val store = QueueRuntimeStore()
        val completed = task(
            id = "task-completed",
            runId = "run-1",
            queueIndex = 0L,
            createdAtEpochMs = 100L,
            state = DownloadState.COMPLETED,
            bytesDownloaded = 100L,
            totalBytes = 100L
        )
        val running = task(
            id = "task-running",
            runId = "run-1",
            queueIndex = 1L,
            createdAtEpochMs = 101L,
            state = DownloadState.RUNNING,
            bytesDownloaded = 10L,
            totalBytes = 100L
        )
        val summary = QueueRunSummary(
            runId = "run-1",
            totalCount = 2
        )
        store.syncTasks(
            tasks = listOf(completed, running),
            runSummary = summary
        )
        assertEquals(2, store.latestRunCounter().totalCount)

        store.syncTasks(
            tasks = listOf(running),
            runSummary = summary
        )

        assertEquals(2, store.latestRunCounter().totalCount)
    }

    @Test
    fun latestRunCounter_usesFailedAndCancelledAwareDenominator() {
        val store = QueueRuntimeStore()
        val completed = task(
            id = "task-completed",
            runId = "run-1",
            queueIndex = 0L,
            createdAtEpochMs = 100L,
            state = DownloadState.COMPLETED,
            bytesDownloaded = 100L,
            totalBytes = 100L
        )
        val failed = task(
            id = "task-failed",
            runId = "run-1",
            queueIndex = 1L,
            createdAtEpochMs = 101L,
            state = DownloadState.FAILED,
            bytesDownloaded = 50L,
            totalBytes = 100L
        )
        val running = task(
            id = "task-running",
            runId = "run-1",
            queueIndex = 2L,
            createdAtEpochMs = 102L,
            state = DownloadState.RUNNING,
            bytesDownloaded = 10L,
            totalBytes = 100L
        )
        store.syncTasks(
            tasks = listOf(completed, failed, running),
            runSummary = QueueRunSummary(
                runId = "run-1",
                totalCount = 3
            )
        )

        val counter = store.latestRunCounter()

        assertEquals(2, counter.totalCount)
        assertEquals(1, counter.completedCount)
        assertEquals(1, counter.failedCount)
        assertEquals(0, counter.cancelledCount)
        assertEquals(110L, counter.downloadedBytes)
        assertEquals(200L, counter.totalBytes)
        assertFalse(counter.hasUnknownTotalBytes)
    }

    private fun task(
        id: String,
        runId: String,
        queueIndex: Long,
        createdAtEpochMs: Long,
        state: DownloadState,
        bytesDownloaded: Long,
        totalBytes: Long?
    ): DownloadTaskEntity {
        return DownloadTaskEntity(
            id = id,
            runId = runId,
            queueIndex = queueIndex,
            snapshotId = "snapshot",
            entryIndex = 0,
            sourceDisplayName = "entry",
            partIndex = 0,
            originalFilename = "file.bin",
            sizeBytes = totalBytes ?: 0L,
            displayFilename = "file.bin",
            subfolder = "folder",
            state = state,
            attemptCount = 1,
            maxAttempts = 3,
            retryAtEpochMs = null,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = createdAtEpochMs,
            lastFailureReason = null,
            magnetUrl = "magnet:?xt=urn:btih:123",
            torrentFileId = 1,
            partName = null,
            outputUri = "content://output/$id",
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            partialExists = bytesDownloaded > 0
        )
    }
}
