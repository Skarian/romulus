@file:Suppress(
    "ArgumentListWrapping",
    "BinaryExpressionWrapping",
    "ChainMethodContinuation",
    "ClassSignature",
    "FunctionSignature",
    "MagicNumber",
    "MaxLineLength",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.romulus.mobile.MainActivity
import com.romulus.mobile.downloads.queue.DownloadRowViewState
import com.romulus.mobile.downloads.queue.DownloadsProjection
import com.romulus.mobile.downloads.queue.QueuePresentationState
import java.util.Locale
import kotlin.math.roundToInt

internal data class ProgressNotificationSnapshot(
    val title: String,
    val text: String,
    val progressPercent: Int?,
    val indeterminate: Boolean
)

internal data class CompletionNotificationSnapshot(
    val title: String,
    val text: String
)

internal interface NotificationApi {
    fun showProgress(snapshot: ProgressNotificationSnapshot)

    fun clearProgress()

    fun showCompletion(snapshot: CompletionNotificationSnapshot)
}

internal class QueueNotificationPresenter(private val notificationApi: NotificationApi) {
    private var hadActiveWork: Boolean = false
    private var lastProgressSnapshot: ProgressNotificationSnapshot? = null

    fun present(projection: DownloadsProjection) {
        if (projection.activeDownloads) {
            val snapshot = projection.toProgressSnapshot()
            if (snapshot != lastProgressSnapshot) {
                notificationApi.showProgress(snapshot)
                lastProgressSnapshot = snapshot
            }
            hadActiveWork = true
            return
        }
        lastProgressSnapshot = null
        if (hadActiveWork) {
            notificationApi.clearProgress()
            projection.toCompletionSnapshot()?.let(notificationApi::showCompletion)
        }
        hadActiveWork = false
    }
}

internal class AndroidNotificationApi(private val context: Context) : NotificationApi {
    private val notificationManager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    override fun showProgress(snapshot: ProgressNotificationSnapshot) {
        ensureChannels()
        if (!canPostNotifications()) {
            return
        }
        runCatching {
            notificationManager.notify(
                DownloadsNotificationIds.PROGRESS_NOTIFICATION_ID,
                NotificationCompat.Builder(context, DownloadsNotificationIds.PROGRESS_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(snapshot.title)
                    .setContentText(snapshot.text)
                    .setContentIntent(downloadsPendingIntent())
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(
                        DownloadsNotificationIds.FULL_PROGRESS,
                        snapshot.progressPercent ?: 0,
                        snapshot.indeterminate
                    )
                    .build()
            )
        }
    }

    override fun clearProgress() {
        notificationManager.cancel(DownloadsNotificationIds.PROGRESS_NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    override fun showCompletion(snapshot: CompletionNotificationSnapshot) {
        ensureChannels()
        if (!canPostNotifications()) {
            return
        }
        runCatching {
            notificationManager.notify(
                DownloadsNotificationIds.COMPLETION_NOTIFICATION_ID,
                NotificationCompat.Builder(context, DownloadsNotificationIds.COMPLETION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(snapshot.title)
                    .setContentText(snapshot.text)
                    .setContentIntent(downloadsPendingIntent())
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                DownloadsNotificationIds.PROGRESS_CHANNEL_ID,
                "Active downloads",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DownloadsNotificationIds.COMPLETION_CHANNEL_ID,
                "Download completion",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun downloadsPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_ROUTE, MainActivity.ROUTE_DOWNLOADS)
        }
        return PendingIntent.getActivity(
            context,
            DownloadsNotificationIds.REQUEST_CODE_DOWNLOADS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal object DownloadsNotificationIds {
    const val PROGRESS_CHANNEL_ID = "romulus_download_progress"
    const val COMPLETION_CHANNEL_ID = "romulus_download_completion"
    const val PROGRESS_NOTIFICATION_ID = 7001
    const val COMPLETION_NOTIFICATION_ID = 7002
    const val REQUEST_CODE_DOWNLOADS = 7003
    const val FULL_PROGRESS = 100
}

internal fun createDownloadWorkerForegroundInfo(context: Context): ForegroundInfo {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            DownloadsNotificationIds.PROGRESS_CHANNEL_ID,
            "Active downloads",
            NotificationManager.IMPORTANCE_LOW
        )
    )
    return ForegroundInfo(
        DownloadsNotificationIds.PROGRESS_NOTIFICATION_ID,
        NotificationCompat.Builder(context, DownloadsNotificationIds.PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloads in progress")
            .setContentText("Preparing download runtime")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(DownloadsNotificationIds.FULL_PROGRESS, 0, true)
            .build(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )
}

private fun DownloadsProjection.toProgressSnapshot(): ProgressNotificationSnapshot {
    val runningRows = rows.filter { row -> row.state == QueuePresentationState.RUNNING }
    if (runningRows.isNotEmpty()) {
        val transferRows = rows.filter { row ->
            row.state == QueuePresentationState.RUNNING || row.state == QueuePresentationState.PAUSED
        }.mapNotNull(DownloadRowViewState::details)
            .mapNotNull { details -> details.transfer }
        val downloadedBytes = transferRows.sumOf { transfer -> transfer.downloadedBytes }
        val allTotalsKnown = transferRows.isNotEmpty() && transferRows.all { transfer ->
            transfer.totalBytes != null && transfer.totalBytes > 0L
        }
        val totalBytes = if (allTotalsKnown) {
            transferRows.sumOf { transfer -> checkNotNull(transfer.totalBytes) }
        } else {
            null
        }
        val progressText = if (totalBytes != null && totalBytes > 0L) {
            val progressPercent = ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
            "$progressPercent% complete | " +
                "${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)} | " +
                "${summary.completed}/${summary.total}" +
                failedSuffix(summary.failed)
        } else {
            "${formatBytes(downloadedBytes)} | " +
                "${summary.completed}/${summary.total}" +
                failedSuffix(summary.failed)
        }
        return ProgressNotificationSnapshot(
            title = "Downloads in progress",
            text = progressText,
            progressPercent = if (totalBytes != null && totalBytes > 0L) {
                ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).roundToInt()
                    .coerceIn(0, 100)
            } else {
                null
            },
            indeterminate = totalBytes == null || totalBytes <= 0L
        )
    }
    return ProgressNotificationSnapshot(
        title = "Downloads in progress",
        text = shortStatusText(rows),
        progressPercent = null,
        indeterminate = true
    )
}

private fun DownloadsProjection.toCompletionSnapshot(): CompletionNotificationSnapshot? {
    if (summary.total <= 0) {
        return null
    }
    val title = if (summary.failed == 0 && summary.cancelled == 0) {
        "Downloads complete"
    } else {
        "Downloads finished"
    }
    val parts = buildList {
        add("${summary.completed}/${summary.total} complete")
        if (summary.failed > 0) {
            add("${summary.failed} failed")
        }
        if (summary.cancelled > 0) {
            add("${summary.cancelled} cancelled")
        }
    }
    return CompletionNotificationSnapshot(
        title = title,
        text = parts.joinToString(" | ")
    )
}

private fun shortStatusText(rows: List<DownloadRowViewState>): String = when {
    rows.any { row -> row.state == QueuePresentationState.PREPARING } -> "Preparing downloads"
    rows.any { row -> row.state == QueuePresentationState.RESOLVING } -> "Resolving downloads"
    rows.any { row -> row.state == QueuePresentationState.RETRY_SCHEDULED } -> "Retry scheduled"
    rows.any { row -> row.state == QueuePresentationState.QUEUED } -> "Queued downloads"
    rows.any { row -> row.state == QueuePresentationState.PAUSED } -> "Downloads paused"
    else -> "Downloads in progress"
}

private fun failedSuffix(failed: Int): String =
    if (failed > 0) {
        " | $failed failed"
    } else {
        ""
    }

private fun formatBytes(bytes: Long): String {
    val absolute = bytes.toDouble()
    return when {
        absolute >= 1024.0 * 1024.0 * 1024.0 ->
            String.format(
                Locale.US,
                "%.1f GB",
                absolute / (1024.0 * 1024.0 * 1024.0)
            )

        absolute >= 1024.0 * 1024.0 ->
            String.format(Locale.US, "%.1f MB", absolute / (1024.0 * 1024.0))

        absolute >= 1024.0 ->
            String.format(Locale.US, "%.1f KB", absolute / 1024.0)

        else -> "$bytes B"
    }
}
