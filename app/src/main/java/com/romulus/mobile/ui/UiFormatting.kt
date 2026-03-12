package com.romulus.mobile.ui

import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

internal fun Long.formatByteCount(): String {
    val safe = coerceAtLeast(0L)
    val kb = BYTES_PER_KILOBYTE
    val mb = kb * BYTES_PER_KILOBYTE
    val gb = mb * BYTES_PER_KILOBYTE
    return when {
        safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.1f KB", safe / kb)
        else -> "$safe B"
    }
}

internal fun Long?.formatByteCountOrUnknown(): String = this?.formatByteCount() ?: "Unknown size"

internal fun Instant.formatTimestamp(): String =
    DateFormat.getDateTimeInstance().format(Date.from(this))

private const val BYTES_PER_KILOBYTE = 1024.0
