package com.romulus.mobile.worker

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.romulus.mobile.app.RomulusApplication
import com.romulus.mobile.data.downloads.TransferDirective
import com.romulus.mobile.data.downloads.TransferResult
import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.data.realdebrid.FileRematchException
import com.romulus.mobile.data.realdebrid.TorrentTerminalStateException
import com.romulus.mobile.domain.downloads.DownloadState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

class QueueOrchestratorWorker(
    appContext: android.content.Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val appContainer = (applicationContext as RomulusApplication).appContainer
    private val queueRepository = appContainer.queueRepository
    private val settingsRepository = appContainer.settingsRepository
    private val realDebridClient = appContainer.realDebridClient
    private val safFileStore = appContainer.safFileStore
    private val transferEngine = appContainer.transferEngine
    private val taskProgressTracker = appContainer.taskProgressTracker
    private val clockProvider = appContainer.clockProvider
    private val notifier = DownloadNotifier(applicationContext)

    override suspend fun doWork(): Result {
        notifier.ensureChannels()
        setForeground(notifier.foregroundInfo(queueRepository.latestRunCounter()))
        recoverInterruptedTasks()

        while (true) {
            val settings = settingsRepository.settings.first()
            val maxConcurrency = settings.maxConcurrency.coerceIn(1, 100)
            val runnable = queueRepository.findRunnableTasks(clockProvider.nowEpochMillis(), maxConcurrency)
            val activeCount = queueRepository.activeCount()
            if (runnable.isEmpty()) {
                if (activeCount == 0) {
                    queueRepository.closeRunIfNoActive()
                    val counter = queueRepository.latestRunCounter()
                    if (counter.totalCount > 0) {
                        notifier.cancelProgress()
                        notifier.showCompletion(counter)
                    }
                    return Result.success()
                }
                notifier.showProgress(queueRepository.latestRunCounter())
                delay(1_000L)
                continue
            }

            coroutineScope {
                runnable.map { task ->
                    async {
                        processTask(task.id)
                    }
                }.awaitAll()
            }
            notifier.showProgress(queueRepository.latestRunCounter())
            delay(300)
        }
    }

    private suspend fun processTask(taskId: String) {
        val current = transitionToResolving(taskId) ?: return
        if (!isActionable(current)) return

        val apiKey = settingsRepository.readApiKey()
        if (apiKey.isNullOrBlank()) {
            failWithAuthRequired()
            return
        }

        val settings = settingsRepository.settings.first()
        val directoryUri = settings.downloadDirectoryUri
        if (directoryUri.isNullOrBlank()) {
            failTask(
                taskId = taskId,
                reason = "Download directory missing",
                retryable = false,
                partialExists = false
            )
            return
        }

        val unrestrictedLink = runCatching {
            realDebridClient.resolveFreshUnrestrictedLink(
                apiKey = apiKey,
                magnetUrl = current.magnetUrl,
                originalFilename = current.originalFilename,
                sizeBytes = current.sizeBytes
            )
        }.getOrElse { throwable ->
            if (throwable is HttpException && (throwable.code() == 401 || throwable.code() == 403)) {
                failWithAuthRequired()
                return
            }
            val reason = when (throwable) {
                is com.romulus.mobile.data.realdebrid.TorrentNotReadyException -> "Torrent not ready"
                is TorrentTerminalStateException -> throwable.message ?: "Torrent cannot be downloaded"
                is FileRematchException -> throwable.message ?: "Selected file no longer matches source entry"
                else -> throwable.message ?: "Unable to resolve download link"
            }
            val retryable = when (throwable) {
                is FileRematchException -> false
                is TorrentTerminalStateException -> false
                is HttpException -> throwable.code() !in listOf(401, 403)
                else -> true
            }
            val latest = queueRepository.findTask(taskId)
            failTask(taskId, reason, retryable, partialExists = latest?.partialExists == true)
            return
        }

        val beforeRun = queueRepository.findTask(taskId) ?: return
        if (!isActionable(beforeRun)) return

        val outputUri = beforeRun.outputUri ?: safFileStore.createOutputFile(
            treeUri = directoryUri,
            subfolder = beforeRun.subfolder,
            preferredFileName = beforeRun.displayFilename
        )?.toString()

        if (outputUri.isNullOrBlank()) {
            failTask(
                taskId = taskId,
                reason = "Cannot create destination file",
                retryable = false,
                partialExists = false
            )
            return
        }

        val runTask = queueRepository.findTask(taskId) ?: return
        if (!isActionable(runTask)) return
        val resumeFromBytes = runTask.bytesDownloaded.coerceAtLeast(0L)
        val resolvedTotalBytes = unrestrictedLink.fileSize ?: runTask.totalBytes
        queueRepository.updateTask(
            runTask.copy(
                state = DownloadState.RUNNING,
                updatedAtEpochMs = clockProvider.nowEpochMillis(),
                outputUri = outputUri,
                totalBytes = resolvedTotalBytes
            )
        )

        var latestProgressBytes = resumeFromBytes
        var latestProgressTotalBytes = resolvedTotalBytes
        var lastProgressUiEpochMs = 0L
        var lastProgressPersistEpochMs = 0L
        var lastProgressNotificationEpochMs = 0L
        var lastDirectiveCheckEpochMs = 0L
        var latestDirective = TransferDirective.CONTINUE
        taskProgressTracker.update(taskId, latestProgressBytes, latestProgressTotalBytes)

        val transferResult = transferEngine.transfer(
            downloadUrl = unrestrictedLink.downloadUrl,
            outputUri = outputUri,
            startByte = resumeFromBytes,
            onProgress = { bytesDownloaded, totalBytes ->
                latestProgressBytes = bytesDownloaded.coerceAtLeast(0L)
                latestProgressTotalBytes = totalBytes ?: latestProgressTotalBytes
                val nowEpochMs = clockProvider.nowEpochMillis()
                val reachedTerminalBytes = latestProgressTotalBytes?.let { knownTotal ->
                    knownTotal > 0L && latestProgressBytes >= knownTotal
                } == true

                if (reachedTerminalBytes || nowEpochMs - lastProgressUiEpochMs >= PROGRESS_UI_THROTTLE_MS) {
                    taskProgressTracker.update(taskId, latestProgressBytes, latestProgressTotalBytes)
                    lastProgressUiEpochMs = nowEpochMs
                }

                if (reachedTerminalBytes || nowEpochMs - lastProgressPersistEpochMs >= PROGRESS_PERSIST_THROTTLE_MS) {
                    val persisted = persistProgress(
                        taskId = taskId,
                        bytesDownloaded = latestProgressBytes,
                        totalBytes = latestProgressTotalBytes,
                        markRunning = true
                    )
                    if (persisted && (reachedTerminalBytes || nowEpochMs - lastProgressNotificationEpochMs >= PROGRESS_NOTIFY_THROTTLE_MS)) {
                        notifier.showProgress(queueRepository.latestRunCounter())
                        lastProgressNotificationEpochMs = nowEpochMs
                    }
                    lastProgressPersistEpochMs = nowEpochMs
                }
            },
            resolveDirective = {
                val nowEpochMs = clockProvider.nowEpochMillis()
                if (nowEpochMs - lastDirectiveCheckEpochMs >= DIRECTIVE_POLL_THROTTLE_MS ||
                    latestDirective != TransferDirective.CONTINUE
                ) {
                    val taskWithState = queueRepository.findTask(taskId) ?: return@transfer TransferDirective.CANCEL
                    latestDirective = when {
                        taskWithState.state == DownloadState.PAUSED -> TransferDirective.PAUSE
                        taskWithState.state == DownloadState.CANCELLED -> TransferDirective.CANCEL
                        !taskWithState.state.isActive -> TransferDirective.CANCEL
                        else -> TransferDirective.CONTINUE
                    }
                    lastDirectiveCheckEpochMs = nowEpochMs
                }
                latestDirective
            }
        )

        persistProgress(
            taskId = taskId,
            bytesDownloaded = latestProgressBytes,
            totalBytes = latestProgressTotalBytes,
            markRunning = false
        )
        taskProgressTracker.remove(taskId)

        when (transferResult) {
            TransferResult.Success -> {
                val latest = queueRepository.findTask(taskId) ?: return
                if (latest.state == DownloadState.CANCELLED || latest.state == DownloadState.PAUSED) {
                    return
                }
                queueRepository.updateTask(
                    latest.copy(
                        state = DownloadState.COMPLETED,
                        updatedAtEpochMs = clockProvider.nowEpochMillis(),
                        bytesDownloaded = maxOf(latest.bytesDownloaded, latestProgressBytes),
                        totalBytes = latestProgressTotalBytes ?: latest.totalBytes,
                        partialExists = false,
                        lastFailureReason = null
                    )
                )
            }

            is TransferResult.Failure -> {
                failTask(
                    taskId = taskId,
                    reason = transferResult.reason,
                    retryable = transferResult.retryable,
                    partialExists = transferResult.partialExists || latestProgressBytes > 0
                )
            }

            TransferResult.Paused -> {
                markPaused(
                    taskId = taskId,
                    bytesDownloaded = latestProgressBytes,
                    totalBytes = latestProgressTotalBytes
                )
            }

            TransferResult.Cancelled -> {
                markCancelled(
                    taskId = taskId,
                    bytesDownloaded = latestProgressBytes,
                    totalBytes = latestProgressTotalBytes
                )
            }
        }
    }

    private suspend fun persistProgress(
        taskId: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        markRunning: Boolean
    ): Boolean {
        val latest = queueRepository.findTask(taskId) ?: return false
        if (markRunning && !isActionable(latest)) return false
        val normalizedBytes = maxOf(latest.bytesDownloaded, bytesDownloaded.coerceAtLeast(0L))
        val normalizedTotal = totalBytes ?: latest.totalBytes
        val nextState = if (markRunning) DownloadState.RUNNING else latest.state

        if (
            latest.state == nextState &&
            latest.bytesDownloaded == normalizedBytes &&
            latest.totalBytes == normalizedTotal
        ) {
            return true
        }

        queueRepository.updateTask(
            latest.copy(
                state = nextState,
                updatedAtEpochMs = clockProvider.nowEpochMillis(),
                bytesDownloaded = normalizedBytes,
                totalBytes = normalizedTotal
            )
        )
        return true
    }

    private suspend fun failWithAuthRequired() {
        val activeTasks = queueRepository.findActiveTasks()
        activeTasks.forEach { task ->
            taskProgressTracker.remove(task.id)
            queueRepository.updateTask(
                task.copy(
                    state = DownloadState.FAILED,
                    updatedAtEpochMs = clockProvider.nowEpochMillis(),
                    lastFailureReason = "Auth required",
                    retryAtEpochMs = null
                )
            )
        }
    }

    private suspend fun failTask(
        taskId: String,
        reason: String,
        retryable: Boolean,
        partialExists: Boolean
    ) {
        taskProgressTracker.remove(taskId)
        val current = queueRepository.findTask(taskId) ?: return
        if (current.state == DownloadState.CANCELLED || current.state == DownloadState.PAUSED) return

        val attempts = current.attemptCount
        val canRetry = retryable && attempts < current.maxAttempts
        if (canRetry) {
            val delayMs = when (attempts) {
                1 -> 0L
                2 -> 15_000L
                else -> 60_000L
            } + (0L..2_000L).random()
            val retryAt = clockProvider.nowEpochMillis() + delayMs
            val retryState = if (delayMs == 0L) DownloadState.RETRYING else DownloadState.RETRY_SCHEDULED
            queueRepository.updateTask(
                current.copy(
                    state = retryState,
                    updatedAtEpochMs = clockProvider.nowEpochMillis(),
                    retryAtEpochMs = retryAt,
                    lastFailureReason = reason,
                    partialExists = partialExists
                )
            )
            return
        }

        queueRepository.updateTask(
            current.copy(
                state = DownloadState.FAILED,
                updatedAtEpochMs = clockProvider.nowEpochMillis(),
                retryAtEpochMs = null,
                lastFailureReason = reason,
                partialExists = partialExists
            )
        )
    }

    private suspend fun markPaused(taskId: String, bytesDownloaded: Long, totalBytes: Long?) {
        taskProgressTracker.remove(taskId)
        val latest = queueRepository.findTask(taskId) ?: return
        queueRepository.updateTask(
            latest.copy(
                state = DownloadState.PAUSED,
                updatedAtEpochMs = clockProvider.nowEpochMillis(),
                bytesDownloaded = maxOf(latest.bytesDownloaded, bytesDownloaded),
                totalBytes = totalBytes ?: latest.totalBytes,
                partialExists = latest.partialExists || latest.bytesDownloaded > 0 || bytesDownloaded > 0,
                lastFailureReason = null
            )
        )
    }

    private suspend fun markCancelled(taskId: String, bytesDownloaded: Long, totalBytes: Long?) {
        taskProgressTracker.remove(taskId)
        val latest = queueRepository.findTask(taskId) ?: return
        queueRepository.updateTask(
            latest.copy(
                state = DownloadState.CANCELLED,
                updatedAtEpochMs = clockProvider.nowEpochMillis(),
                bytesDownloaded = maxOf(latest.bytesDownloaded, bytesDownloaded),
                totalBytes = totalBytes ?: latest.totalBytes,
                partialExists = latest.partialExists || latest.bytesDownloaded > 0 || bytesDownloaded > 0,
                lastFailureReason = null
            )
        )
    }

    private suspend fun transitionToResolving(taskId: String): DownloadTaskEntity? {
        taskProgressTracker.remove(taskId)
        val latest = queueRepository.findTask(taskId) ?: return null
        if (!isActionable(latest)) return null
        val nextAttempt = if (latest.state == DownloadState.RETRYING || latest.attemptCount == 0) {
            latest.attemptCount + 1
        } else {
            latest.attemptCount
        }

        val next = latest.copy(
            state = DownloadState.RESOLVING_LINK,
            attemptCount = nextAttempt,
            updatedAtEpochMs = clockProvider.nowEpochMillis(),
            retryAtEpochMs = null,
            lastFailureReason = null
        )
        queueRepository.updateTask(next)
        return next
    }

    private suspend fun recoverInterruptedTasks() {
        val tasks = queueRepository.findActiveTasks()
        tasks.forEach { task ->
            val recoveredState = when (task.state) {
                DownloadState.RUNNING,
                DownloadState.RESOLVING_LINK -> DownloadState.QUEUED

                else -> task.state
            }
            if (recoveredState != task.state) {
                taskProgressTracker.remove(task.id)
                queueRepository.updateTask(
                    task.copy(
                        state = recoveredState,
                        retryAtEpochMs = null,
                        updatedAtEpochMs = clockProvider.nowEpochMillis(),
                        partialExists = task.partialExists || task.bytesDownloaded > 0
                    )
                )
            }
        }
    }

    private fun isActionable(task: DownloadTaskEntity): Boolean {
        return task.state.isActive && task.state != DownloadState.PAUSED && task.state != DownloadState.CANCELLED
    }

    companion object {
        private const val PROGRESS_UI_THROTTLE_MS = 120L
        private const val PROGRESS_PERSIST_THROTTLE_MS = 1_500L
        private const val PROGRESS_NOTIFY_THROTTLE_MS = 750L
        private const val DIRECTIVE_POLL_THROTTLE_MS = 150L
    }
}
