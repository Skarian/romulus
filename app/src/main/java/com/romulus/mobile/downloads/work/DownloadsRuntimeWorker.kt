@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.romulus.mobile.app.RomulusApplication
import kotlin.coroutines.cancellation.CancellationException

internal class DownloadsRuntimeWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        setForeground(createDownloadWorkerForegroundInfo(applicationContext))
        val application = applicationContext as RomulusApplication
        application.appGraph.downloadsFacade.runWorkerUntilDrained().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (_: Exception) {
        Result.retry()
    }
}
