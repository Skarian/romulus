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
        val countSummary = countSummary()
        if (hasUnknownTotalBytes || totalBytes <= 0L) {
            return "${formatBytes(downloadedBytes)} downloaded | $countSummary"
        }
        val percent = progressPercent() ?: 0
        return buildString {
            append("$percent% complete | ${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)} | ")
            append(countSummary)
        }
    }

    fun completionTitle(): String {
        return if (failedCount == 0 && cancelledCount == 0) {
            "Downloads complete"
        } else {
            "Downloads finished"
        }
    }

    fun completionMessage(): String {
        return when {
            failedCount > 0 && cancelledCount > 0 ->
                "Downloads finished: $completedCount complete, $failedCount failed, $cancelledCount cancelled"
            failedCount > 0 ->
                "Downloads finished: $completedCount complete, $failedCount failed"
            cancelledCount > 0 ->
                "Downloads finished: $completedCount complete, $cancelledCount cancelled"
            else -> "All downloads complete"
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

    private fun countSummary(): String {
        val fileLabel = if (totalCount == 1) "file" else "files"
        return buildString {
            append("$completedCount/$totalCount $fileLabel complete")
            if (failedCount > 0) {
                append(", $failedCount failed")
            }
        }
    }
}
