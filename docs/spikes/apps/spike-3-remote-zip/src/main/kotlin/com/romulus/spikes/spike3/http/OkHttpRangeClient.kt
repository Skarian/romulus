package com.romulus.spikes.spike3.http

import com.romulus.spikes.spike3.errors.FailureCodes
import com.romulus.spikes.spike3.errors.Spike3FailureException
import com.romulus.spikes.spike3.model.FailureStage
import com.romulus.spikes.spike3.model.HttpTraceEvent
import com.romulus.spikes.spike3.model.RangeReadResult
import com.romulus.spikes.spike3.model.RemoteArchiveInfo
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.Duration
import java.time.Instant

interface RangeHttpClient {
    fun probe(caseId: String, url: HttpUrl): RemoteArchiveInfo
    fun read(caseId: String, stage: FailureStage, url: HttpUrl, start: Long, endInclusive: Long): RangeReadResult
}

class OkHttpRangeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build(),
    private val recorder: HttpTraceRecorder,
) : RangeHttpClient {
    override fun probe(caseId: String, url: HttpUrl): RemoteArchiveInfo {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()
        val startedAt = Instant.now()
        var failureMessage: String? = null
        var responseCode: Int? = null
        var contentLength: Long? = null
        var contentRange: String? = null

        try {
            client.newCall(request).execute().use { response ->
                responseCode = response.code
                contentLength = response.header("Content-Length")?.toLongOrNull()
                contentRange = response.header("Content-Range")
                if (!response.isSuccessful) {
                    throw Spike3FailureException(
                        stage = FailureStage.PROBE,
                        errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                        message = "HEAD probe failed for $url with HTTP ${response.code}",
                    )
                }
                val archiveLength = contentLength ?: throw Spike3FailureException(
                    stage = FailureStage.PROBE,
                    errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                    message = "HEAD probe for $url did not include Content-Length",
                )
                return RemoteArchiveInfo(
                    contentLength = archiveLength,
                    acceptsRanges = response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true,
                    rangeModeHint = response.header("X-Spike-Range-Mode"),
                )
            }
        } catch (failure: Exception) {
            failureMessage = failure.message
            if (failure is Spike3FailureException) {
                throw failure
            }
            throw Spike3FailureException(
                stage = FailureStage.PROBE,
                errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                message = "HEAD probe failed for $url: ${failure.message}",
                cause = failure,
            )
        } finally {
            recorder.record(
                HttpTraceEvent(
                    timestamp = startedAt,
                    caseId = caseId,
                    stage = FailureStage.PROBE,
                    method = "HEAD",
                    path = url.encodedPath,
                    requestRange = null,
                    responseCode = responseCode,
                    contentLength = contentLength,
                    contentRange = contentRange,
                    elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis(),
                    failureMessage = failureMessage,
                ),
            )
        }
    }

    override fun read(caseId: String, stage: FailureStage, url: HttpUrl, start: Long, endInclusive: Long): RangeReadResult {
        require(start >= 0) { "start must be non-negative" }
        require(endInclusive >= start) { "endInclusive must be >= start" }
        val requestedRange = "bytes=$start-$endInclusive"
        val request = Request.Builder()
            .url(url)
            .header("Range", requestedRange)
            .get()
            .build()
        val startedAt = Instant.now()
        var failureMessage: String? = null
        var responseCode: Int? = null
        var contentLength: Long? = null
        var contentRange: String? = null

        try {
            client.newCall(request).execute().use { response ->
                responseCode = response.code
                contentLength = response.header("Content-Length")?.toLongOrNull()
                contentRange = response.header("Content-Range")
                return when (response.code) {
                    206 -> handlePartialContent(stage, url, start, endInclusive, response)
                    200 -> throw Spike3FailureException(
                        stage = stage,
                        errorCode = FailureCodes.FULL_BODY_RESPONSE,
                        message = "GET $url returned 200 for $requestedRange instead of 206",
                    )
                    416 -> throw Spike3FailureException(
                        stage = stage,
                        errorCode = FailureCodes.RANGE_WINDOW_REJECTED,
                        message = "GET $url rejected range $requestedRange with HTTP 416",
                    )
                    else -> throw Spike3FailureException(
                        stage = stage,
                        errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                        message = "GET $url returned HTTP ${response.code} for $requestedRange",
                    )
                }
            }
        } catch (failure: Exception) {
            failureMessage = failure.message
            if (failure is Spike3FailureException) {
                throw failure
            }
            throw Spike3FailureException(
                stage = stage,
                errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                message = "GET $url failed for $requestedRange: ${failure.message}",
                cause = failure,
            )
        } finally {
            recorder.record(
                HttpTraceEvent(
                    timestamp = startedAt,
                    caseId = caseId,
                    stage = stage,
                    method = "GET",
                    path = url.encodedPath,
                    requestRange = requestedRange,
                    responseCode = responseCode,
                    contentLength = contentLength,
                    contentRange = contentRange,
                    elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis(),
                    failureMessage = failureMessage,
                ),
            )
        }
    }

    private fun handlePartialContent(
        stage: FailureStage,
        url: HttpUrl,
        requestedStart: Long,
        requestedEndInclusive: Long,
        response: Response,
    ): RangeReadResult {
        val parsedRange = parseContentRange(response.header("Content-Range")) ?: throw Spike3FailureException(
            stage = stage,
            errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
            message = "GET $url omitted Content-Range for 206 response",
        )
        if (parsedRange.first != requestedStart) {
            throw Spike3FailureException(
                stage = stage,
                errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                message = "GET $url returned unexpected Content-Range start ${parsedRange.first} for requested $requestedStart-$requestedEndInclusive",
            )
        }
        val body = response.body?.bytes() ?: ByteArray(0)
        val expectedLength = parsedRange.second - parsedRange.first + 1
        if (body.size.toLong() != expectedLength) {
            throw Spike3FailureException(
                stage = stage,
                errorCode = FailureCodes.INVALID_HTTP_RESPONSE,
                message = "GET $url returned ${body.size} bytes for Content-Range ${parsedRange.first}-${parsedRange.second}",
            )
        }
        return RangeReadResult(
            requestedStart = requestedStart,
            requestedEndInclusive = requestedEndInclusive,
            actualStart = parsedRange.first,
            actualEndInclusive = parsedRange.second,
            bytes = body,
        )
    }

    private fun parseContentRange(header: String?): Pair<Long, Long>? {
        if (header == null) {
            return null
        }
        val match = CONTENT_RANGE_REGEX.matchEntire(header) ?: return null
        val start = match.groupValues[1].toLong()
        val end = match.groupValues[2].toLong()
        return start to end
    }

    private companion object {
        val CONTENT_RANGE_REGEX = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
