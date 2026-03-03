package com.romulus.mobile.worker

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
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
import com.romulus.mobile.domain.downloads.QueueRunCounter

class DownloadNotifier(
    private val context: Context
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        system.createNotificationChannel(
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "Active downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live status while downloads are running"
            }
        )
        system.createNotificationChannel(
            NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Download completion",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Summary when a download run finishes"
            }
        )
    }

    fun foregroundInfo(counter: QueueRunCounter): ForegroundInfo {
        val notification = progressNotification(counter)
        return ForegroundInfo(
            PROGRESS_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    @SuppressLint("MissingPermission")
    fun showProgress(counter: QueueRunCounter) {
        if (!canPostNotifications()) return
        runCatching {
            notificationManager.notify(PROGRESS_NOTIFICATION_ID, progressNotification(counter))
        }
    }

    fun cancelProgress() {
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun showCompletion(counter: QueueRunCounter) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(counter.completionTitle())
            .setContentText(counter.completionMessage())
            .setContentIntent(downloadsPendingIntent())
            .setAutoCancel(true)
            .build()
        runCatching {
            notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
        }
    }

    private fun progressNotification(counter: QueueRunCounter): Notification {
        val percent = counter.progressPercent()
        val indeterminate = percent == null
        return NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloads in progress")
            .setContentText(counter.summaryText())
            .setContentIntent(downloadsPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, indeterminate)
            .build()
    }

    private fun downloadsPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_ROUTE, MainActivity.ROUTE_DOWNLOADS)
        }
        return PendingIntent.getActivity(
            context,
            7003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val PROGRESS_CHANNEL_ID = "romulus_download_progress"
        const val COMPLETION_CHANNEL_ID = "romulus_download_completion"
        const val PROGRESS_NOTIFICATION_ID = 7001
        const val COMPLETION_NOTIFICATION_ID = 7002
    }
}
