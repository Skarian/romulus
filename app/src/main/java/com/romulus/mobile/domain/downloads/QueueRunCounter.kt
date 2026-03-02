package com.romulus.mobile.domain.downloads

import java.util.Locale

data class QueueRunCounter(
    val completedCount: Int,
    val totalCount: Int,
    val failedCount: Int,
    val cancelledCount: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val hasUnknownTotalBytes: Boolean
) {
    fun summaryText(): String {
        val countSummary = buildString {
            append("$completedCount/$totalCount completed")
            if (failedCount > 0) {
                append(", $failedCount failed")
            }
        }
        if (hasUnknownTotalBytes || totalBytes <= 0L) {
            return "${formatBytes(downloadedBytes)} downloaded, $countSummary"
        }
        val percent = progressPercent() ?: 0
        return buildString {
            append("$percent% by bytes (${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)}), ")
            append(countSummary)
        }
    }

    fun completionMessage(): String {
        return when {
            failedCount > 0 -> "Download run finished with failures"
            cancelledCount > 0 -> "Download run finished with cancellations"
            else -> "Download run finished successfully"
        }
    }

    fun progressPercent(): Int? {
        if (hasUnknownTotalBytes || totalBytes <= 0L) return null
        return ((downloadedBytes.coerceAtLeast(0L) * 100L) / totalBytes)
            .coerceIn(0L, 100L)
            .toInt()
    }

    private fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
            safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
            safe >= kb -> String.format(Locale.US, "%.1f KB", safe / kb)
            else -> "$safe B"
        }
    }
}
