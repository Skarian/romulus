@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class WorkManagerWorkerLauncher(private val context: Context, private val clock: Clock) :
    WorkerLauncher {
    override suspend fun launchNow(): Result<Unit> = runCatching {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadsRuntimeWorker>()
                .addTag(WORK_TAG)
                .build()
        )
    }

    override suspend fun launchAt(instant: Instant): Result<Unit> = runCatching {
        val delay = Duration.between(clock.instant(), instant).coerceAtLeast(Duration.ZERO)
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DownloadsRuntimeWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()
        )
    }

    private companion object {
        const val IMMEDIATE_WORK_NAME = "downloads-runtime-immediate"
        const val RETRY_WORK_NAME = "downloads-runtime-retry"
        const val WORK_TAG = "downloads-runtime"
    }
}
