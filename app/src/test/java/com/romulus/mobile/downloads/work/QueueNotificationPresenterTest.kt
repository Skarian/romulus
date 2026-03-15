package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.RecordingNotificationApi
import com.romulus.mobile.downloads.queue.DownloadRowDetailsViewState
import com.romulus.mobile.downloads.queue.DownloadRowViewState
import com.romulus.mobile.downloads.queue.DownloadsProjection
import com.romulus.mobile.downloads.queue.PreparingRowDetailsViewState
import com.romulus.mobile.downloads.queue.QueueActionKind
import com.romulus.mobile.downloads.queue.QueuePresentationState
import com.romulus.mobile.downloads.queue.QueueSummary
import com.romulus.mobile.downloads.queue.TaskId
import com.romulus.mobile.downloads.queue.TransferRowDetailsViewState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueNotificationPresenterTest {
    @Test
    fun runningProjectionUsesByteFirstNotificationText() {
        val api = RecordingNotificationApi()
        val presenter = QueueNotificationPresenter(api)

        presenter.present(
            DownloadsProjection(
                summary = QueueSummary(
                    completed = 1,
                    total = 4,
                    failed = 1,
                    cancelled = 0
                ),
                rows = listOf(
                    downloadRow(
                        taskId = "task-1",
                        state = QueuePresentationState.RUNNING,
                        transfer = TransferRowDetailsViewState(
                            downloadedBytes = 512,
                            totalBytes = 1024,
                            progressPercentLabel = "50%"
                        )
                    )
                ),
                activeDownloads = true
            )
        )

        assertEquals(1, api.progressSnapshots.size)
        assertEquals(
            "50% complete | 512 B/1.0 KB | 1/4 | 1 failed",
            api.progressSnapshots.single().text
        )
        assertEquals(50, api.progressSnapshots.single().progressPercent)
    }

    @Test
    fun idleProjectionAfterActiveWorkClearsProgressAndPostsCompletion() {
        val api = RecordingNotificationApi()
        val presenter = QueueNotificationPresenter(api)

        presenter.present(
            DownloadsProjection(
                summary = QueueSummary(
                    completed = 0,
                    total = 2,
                    failed = 0,
                    cancelled = 0
                ),
                rows = listOf(
                    downloadRow(
                        taskId = "task-1",
                        state = QueuePresentationState.PREPARING,
                        preparing = PreparingRowDetailsViewState(
                            enteredAt = Instant.parse("2026-03-10T18:00:00Z"),
                            timeoutAt = Instant.parse("2026-03-11T18:00:00Z"),
                            lastProviderStatus = "Queued",
                            lastProviderProgress = 12.0
                        )
                    )
                ),
                activeDownloads = true
            )
        )
        presenter.present(
            DownloadsProjection(
                summary = QueueSummary(
                    completed = 1,
                    total = 2,
                    failed = 1,
                    cancelled = 0
                ),
                rows = emptyList(),
                activeDownloads = false
            )
        )

        assertEquals("Preparing downloads", api.progressSnapshots.single().text)
        assertEquals(1, api.clearCount)
        assertEquals("Downloads finished", api.completionSnapshots.single().title)
        assertEquals("1/2 complete | 1 failed", api.completionSnapshots.single().text)
    }

    private fun downloadRow(
        taskId: String,
        state: QueuePresentationState,
        preparing: PreparingRowDetailsViewState? = null,
        transfer: TransferRowDetailsViewState? = null
    ): DownloadRowViewState = DownloadRowViewState(
        taskId = TaskId(taskId),
        originalDisplayName = "Episode.mkv",
        state = state,
        stateLabel = state.name,
        allowedActions = emptySet<QueueActionKind>(),
        progressLabel = transfer?.progressPercentLabel,
        createdAt = Instant.parse("2026-03-10T18:00:00Z"),
        details = DownloadRowDetailsViewState(
            sourceEntry = "Entry",
            outputSubfolder = "shows",
            outputSummary = "Episode.mkv",
            currentState = state.name,
            updatedAt = Instant.parse("2026-03-10T18:00:00Z"),
            originalSizeBytes = 1024,
            failureReason = null,
            partLabel = null,
            attemptCount = 1,
            preparing = preparing,
            transfer = transfer
        )
    )
}
