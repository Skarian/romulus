package com.romulus.mobile.domain.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRunCounterTest {
    @Test
    fun summaryUsesByteProgressAndIncludesFailedCountWhenNonZero() {
        val successOnly = QueueRunCounter(
            completedCount = 2,
            totalCount = 4,
            failedCount = 0,
            cancelledCount = 0,
            downloadedBytes = 512,
            totalBytes = 1024,
            hasUnknownTotalBytes = false
        )
        val withFailures = QueueRunCounter(
            completedCount = 2,
            totalCount = 4,
            failedCount = 1,
            cancelledCount = 0,
            downloadedBytes = 512,
            totalBytes = 1024,
            hasUnknownTotalBytes = false
        )

        assertEquals("50% by bytes (512 B/1.0 KB), 2/4 completed", successOnly.summaryText())
        assertEquals("50% by bytes (512 B/1.0 KB), 2/4 completed, 1 failed", withFailures.summaryText())
    }

    @Test
    fun summaryFallsBackToDownloadedBytesWhenTotalIsUnknown() {
        val counter = QueueRunCounter(
            completedCount = 0,
            totalCount = 2,
            failedCount = 0,
            cancelledCount = 0,
            downloadedBytes = 2048,
            totalBytes = 0,
            hasUnknownTotalBytes = true
        )

        assertEquals("2.0 KB downloaded, 0/2 completed", counter.summaryText())
    }

    @Test
    fun completionMessageUsesCancellationBranch() {
        val counter = QueueRunCounter(
            completedCount = 1,
            totalCount = 2,
            failedCount = 0,
            cancelledCount = 1,
            downloadedBytes = 1024,
            totalBytes = 1024,
            hasUnknownTotalBytes = false
        )
        assertEquals("Download run finished with cancellations", counter.completionMessage())
    }
}
