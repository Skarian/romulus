@file:Suppress(
    "ChainMethodContinuation",
    "ClassSignature",
    "InjectDispatcher",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads.attempts

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OkHttpDownloadTransport(private val client: OkHttpClient) : DownloadTransport {
    override suspend fun open(url: String, resumeByteOffset: Long): Result<DownloadStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (resumeByteOffset > 0L) {
                            header("Range", "bytes=$resumeByteOffset-")
                        }
                    }
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw IOException("Download request failed with HTTP ${response.code}")
                }
                val responseBody = response.body ?: run {
                    response.close()
                    throw IOException("Download response body is empty")
                }
                val contentLength = responseBody.contentLength()
                val totalBytes = if (contentLength >= 0L) {
                    if (resumeByteOffset > 0L && response.code == HTTP_PARTIAL_CONTENT) {
                        contentLength + resumeByteOffset
                    } else {
                        contentLength
                    }
                } else {
                    null
                }
                DownloadStream(
                    inputStream = responseBody.byteStream(),
                    totalBytes = totalBytes,
                    resumeAccepted = resumeByteOffset == 0L || response.code == HTTP_PARTIAL_CONTENT,
                    closeable = response
                )
            }
        }

    private companion object {
        const val HTTP_PARTIAL_CONTENT = 206
    }
}
