package com.romulus.mobile.domain.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRunCounterTest {
    @Test
    fun summaryUsesPercentBytesAndIncludesFailedCountWhenNonZero() {
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

        assertEquals("50% complete | 512 B/1.0 KB | 2/4 files complete", successOnly.summaryText())
        assertEquals("50% complete | 512 B/1.0 KB | 2/4 files complete, 1 failed", withFailures.summaryText())
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

        assertEquals("2.0 KB downloaded | 0/2 files complete", counter.summaryText())
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
        assertEquals("Downloads finished: 1 complete, 1 cancelled", counter.completionMessage())
    }

    @Test
    fun completionMessageUsesFailedAndCancelledBranch() {
        val counter = QueueRunCounter(
            completedCount = 1,
            totalCount = 3,
            failedCount = 1,
            cancelledCount = 1,
            downloadedBytes = 1024,
            totalBytes = 2048,
            hasUnknownTotalBytes = false
        )
        assertEquals(
            "Downloads finished: 1 complete, 1 failed, 1 cancelled",
            counter.completionMessage()
        )
    }

    @Test
    fun completionTitleUsesSuccessAndFinishedBranches() {
        val success = QueueRunCounter(
            completedCount = 2,
            totalCount = 2,
            failedCount = 0,
            cancelledCount = 0,
            downloadedBytes = 4096,
            totalBytes = 4096,
            hasUnknownTotalBytes = false
        )
        val nonSuccess = QueueRunCounter(
            completedCount = 1,
            totalCount = 2,
            failedCount = 1,
            cancelledCount = 0,
            downloadedBytes = 2048,
            totalBytes = 4096,
            hasUnknownTotalBytes = false
        )

        assertEquals("Downloads complete", success.completionTitle())
        assertEquals("Downloads finished", nonSuccess.completionTitle())
    }
}
