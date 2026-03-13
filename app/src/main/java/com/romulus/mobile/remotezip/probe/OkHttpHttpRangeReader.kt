@file:Suppress("ChainMethodContinuation")

package com.romulus.mobile.remotezip.probe

import com.romulus.mobile.remotezip.RemoteZipFailureException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class OkHttpHttpRangeReader(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HttpRangeReader {
    override suspend fun head(url: String): Result<RangeReadResult> = withContext(ioDispatcher) {
        runCatchingRemoteZipFailure {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RemoteZipFailureException(
                        stage = PROBE_STAGE,
                        code = "INVALID_HTTP_RESPONSE",
                        message = "HEAD probe failed for $url with HTTP ${response.code}"
                    )
                }
                RangeReadResult(
                    statusCode = response.code,
                    acceptsRanges = response.header("Accept-Ranges")
                        ?.equals("bytes", ignoreCase = true) == true,
                    contentRange = response.header("Content-Range"),
                    body = ByteArray(0),
                    contentLength = response.header("Content-Length")?.toLongOrNull()
                )
            }
        }
    }

    override suspend fun readRange(
        url: String,
        startInclusive: Long,
        endInclusive: Long
    ): Result<RangeReadResult> = withContext(ioDispatcher) {
        runCatchingRemoteZipFailure {
            require(startInclusive >= 0L) { "startInclusive must be non-negative" }
            require(endInclusive >= startInclusive) {
                "endInclusive must be greater than or equal to startInclusive"
            }
            val requestedRange = "bytes=$startInclusive-$endInclusive"
            val request = Request.Builder()
                .url(url)
                .header("Range", requestedRange)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HTTP_PARTIAL_CONTENT -> validatedPartialRange(
                        url = url,
                        requestedStart = startInclusive,
                        requestedRange = requestedRange,
                        response = response
                    )

                    HTTP_OK -> throw RemoteZipFailureException(
                        stage = ZIP_ENUMERATION_STAGE,
                        code = "FULL_BODY_RESPONSE",
                        message = "GET $url returned 200 for $requestedRange " +
                            "instead of 206"
                    )

                    HTTP_RANGE_NOT_SATISFIABLE -> throw RemoteZipFailureException(
                        stage = ZIP_ENUMERATION_STAGE,
                        code = "RANGE_WINDOW_REJECTED",
                        message = "GET $url rejected range $requestedRange with HTTP 416"
                    )

                    else -> throw RemoteZipFailureException(
                        stage = ZIP_ENUMERATION_STAGE,
                        code = "INVALID_HTTP_RESPONSE",
                        message = "GET $url returned HTTP ${response.code} for $requestedRange"
                    )
                }
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun validatedPartialRange(
        url: String,
        requestedStart: Long,
        requestedRange: String,
        response: Response
    ): RangeReadResult {
        val contentRange = response.header("Content-Range")
        val parsedRange = parseContentRange(contentRange) ?: throw RemoteZipFailureException(
            stage = ZIP_ENUMERATION_STAGE,
            code = "MISSING_CONTENT_RANGE",
            message = "GET $url omitted Content-Range for $requestedRange"
        )
        if (parsedRange.start != requestedStart) {
            throw RemoteZipFailureException(
                stage = ZIP_ENUMERATION_STAGE,
                code = "UNEXPECTED_CONTENT_RANGE_START",
                message = "GET $url returned Content-Range starting at " +
                    "${parsedRange.start} for $requestedRange"
            )
        }
        val expectedLength = parsedRange.endInclusive - parsedRange.start + 1L
        val body = response.body
            ?.byteStream()
            ?.readExactBytes(expectedLength)
            ?: ByteArray(0)
        if (body.size.toLong() != expectedLength) {
            throw RemoteZipFailureException(
                stage = ZIP_ENUMERATION_STAGE,
                code = "INVALID_BODY_LENGTH",
                message = "GET $url returned ${body.size} bytes for Content-Range " +
                    "${parsedRange.start}-${parsedRange.endInclusive}"
            )
        }
        return RangeReadResult(
            statusCode = response.code,
            acceptsRanges = response.header("Accept-Ranges")
                ?.equals("bytes", ignoreCase = true) == true,
            contentRange = contentRange,
            body = body,
            contentLength = response.header("Content-Length")?.toLongOrNull()
        )
    }

    private fun InputStream.readExactBytes(expectedLength: Long): ByteArray {
        if (expectedLength < 0L || expectedLength > Int.MAX_VALUE.toLong()) {
            throw RemoteZipFailureException(
                stage = ZIP_ENUMERATION_STAGE,
                code = "INVALID_BODY_LENGTH",
                message = "Remote ZIP range length $expectedLength is not supported"
            )
        }
        val expectedLengthInt = expectedLength.toInt()
        val bytes = ByteArray(expectedLengthInt)
        var offset = 0
        while (offset < expectedLengthInt) {
            val read = read(bytes, offset, expectedLengthInt - offset)
            if (read < 0) {
                break
            }
            offset += read
        }
        return if (offset == expectedLengthInt) {
            bytes
        } else {
            bytes.copyOf(offset)
        }
    }

    private data class ParsedContentRange(
        val start: Long,
        val endInclusive: Long,
        val totalLength: Long?
    )

    @Suppress("ReturnCount")
    private fun parseContentRange(header: String?): ParsedContentRange? {
        if (header == null) {
            return null
        }
        val match = CONTENT_RANGE_REGEX.matchEntire(header) ?: return null
        return ParsedContentRange(
            start = match.groupValues[1].toLong(),
            endInclusive = match.groupValues[2].toLong(),
            totalLength = match.groupValues[TOTAL_LENGTH_GROUP_INDEX]
                .takeUnless { it == "*" }
                ?.toLong()
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> runCatchingRemoteZipFailure(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (failure: RemoteZipFailureException) {
        Result.failure(failure)
    } catch (failure: Throwable) {
        Result.failure(
            RemoteZipFailureException(
                stage = ZIP_ENUMERATION_STAGE,
                code = "INVALID_HTTP_RESPONSE",
                message = failure.message ?: "Remote ZIP request failed",
                cause = failure
            )
        )
    }

    private companion object {
        const val PROBE_STAGE = "PROBE"
        const val ZIP_ENUMERATION_STAGE = "ZIP_ENUMERATION"
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val TOTAL_LENGTH_GROUP_INDEX = 3
        val CONTENT_RANGE_REGEX = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
