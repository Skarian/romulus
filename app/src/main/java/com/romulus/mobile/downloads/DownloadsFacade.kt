package com.romulus.mobile.downloads

import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.downloads.config.DownloadSettingsReadiness
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.queue.DownloadsProjection
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.downloads.queue.QueueActionCommand
import com.romulus.mobile.downloads.queue.QueueSummary
import com.romulus.mobile.downloads.queue.QueueTaskInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("RedundantSuspendModifier", "UnusedParameter")
class DownloadsFacade {
    private val settingsState = MutableStateFlow(
        DownloadSettingsState(
            outputDirectoryUri = null,
            maxConcurrency = 1
        )
    )
    private val settingsReadiness = MutableStateFlow(
        DownloadSettingsReadiness(
            isUsable = false,
            brokenReason = "DownloadsFacade is not wired yet"
        )
    )
    private val downloadsProjection = MutableStateFlow(
        DownloadsProjection(
            summary = QueueSummary(
                completed = 0,
                total = 0,
                failed = 0,
                cancelled = 0
            ),
            rows = emptyList(),
            activeDownloads = false
        )
    )
    private val activeDownloadsFlag = MutableStateFlow(false)

    fun observeSettings(): StateFlow<DownloadSettingsState> = settingsState.asStateFlow()

    fun observeSettingsReadiness(): StateFlow<DownloadSettingsReadiness> =
        settingsReadiness.asStateFlow()

    suspend fun readSettingsReadiness(): DownloadSettingsReadiness = settingsReadiness.value

    suspend fun updateSettings(draft: DownloadSettingsDraft): Result<Unit> =
        Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    suspend fun enqueue(inputs: List<QueueTaskInput>): EnqueueResult =
        EnqueueResult.Failed("DownloadsFacade is not wired yet")

    fun observeDownloadsProjection(): StateFlow<DownloadsProjection> =
        downloadsProjection.asStateFlow()

    fun observeActiveDownloadsFlag(): StateFlow<Boolean> = activeDownloadsFlag.asStateFlow()

    suspend fun performAction(command: QueueActionCommand): Result<Unit> =
        Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    suspend fun clearHistory(includeFailed: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    suspend fun requestAppLaunchRecovery(): Result<Unit> =
        Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))
}
