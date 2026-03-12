package com.romulus.mobile.downloads.attempts

import java.io.Closeable
import java.io.InputStream

internal data class DownloadStream(
    val inputStream: InputStream,
    val totalBytes: Long?,
    val resumeAccepted: Boolean,
    private val closeable: Closeable
) : Closeable {
    override fun close() {
        closeable.close()
    }
}

internal interface DownloadTransport {
    suspend fun open(url: String, resumeByteOffset: Long): Result<DownloadStream>
}
