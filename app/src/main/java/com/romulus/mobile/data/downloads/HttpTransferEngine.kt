package com.romulus.mobile.data.downloads

import com.romulus.mobile.data.files.SafFileStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HttpTransferEngine(
    private val okHttpClient: OkHttpClient,
    private val safFileStore: SafFileStore
) : TransferEngine {
    override suspend fun transfer(
        downloadUrl: String,
        outputUri: String,
        startByte: Long,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
        resolveDirective: suspend () -> TransferDirective
    ): TransferResult {
        val requestedStartByte = startByte.coerceAtLeast(0L)
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .get()
        if (requestedStartByte > 0L) {
            requestBuilder.header("Range", "bytes=$requestedStartByte-")
        }
        val request = requestBuilder.build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 416 && requestedStartByte > 0L) {
                    onProgress(requestedStartByte, requestedStartByte)
                    return TransferResult.Success
                }
                if (!response.isSuccessful) {
                    val retryable = response.code in setOf(408, 425, 429) || response.code >= 500
                    return TransferResult.Failure(
                        reason = "HTTP ${response.code}",
                        retryable = retryable,
                        partialExists = safFileStore.fileExists(outputUri)
                    )
                }

                val body = response.body ?: return TransferResult.Failure(
                    reason = "Empty response body",
                    retryable = true,
                    partialExists = safFileStore.fileExists(outputUri)
                )

                val supportsResume = requestedStartByte > 0L && response.code == 206
                val initialBytes = if (supportsResume) requestedStartByte else 0L
                val output = safFileStore.openOutput(outputUri, append = supportsResume)
                    ?: return TransferResult.Failure(
                        reason = "Cannot open destination file",
                        retryable = false,
                        partialExists = safFileStore.fileExists(outputUri)
                    )

                output.use { stream ->
                    val input = body.byteStream()
                    val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
                    var totalRead = initialBytes
                    val contentLength = body.contentLength().takeIf { it > 0L }
                    val totalBytes = when {
                        supportsResume && contentLength != null -> initialBytes + contentLength
                        else -> contentLength
                    }
                    if (initialBytes > 0L) {
                        onProgress(totalRead, totalBytes)
                    }
                    while (true) {
                        when (resolveDirective()) {
                            TransferDirective.PAUSE -> return TransferResult.Paused
                            TransferDirective.CANCEL -> return TransferResult.Cancelled
                            TransferDirective.CONTINUE -> Unit
                        }
                        if (!currentCoroutineContext().isActive) {
                            return TransferResult.Cancelled
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        when (resolveDirective()) {
                            TransferDirective.PAUSE -> return TransferResult.Paused
                            TransferDirective.CANCEL -> return TransferResult.Cancelled
                            TransferDirective.CONTINUE -> Unit
                        }
                        stream.write(buffer, 0, read)
                        totalRead += read
                        onProgress(totalRead, totalBytes)
                    }
                    stream.flush()
                }
            }
            TransferResult.Success
        }.getOrElse { throwable ->
            val message = throwable.message ?: "Transfer failed"
            val retryable = throwable is IOException
            TransferResult.Failure(
                reason = message,
                retryable = retryable,
                partialExists = safFileStore.fileExists(outputUri)
            )
        }
    }
}

private const val TRANSFER_BUFFER_SIZE = 64 * 1024
