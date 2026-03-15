package com.romulus.mobile.remotezip.probe

import com.romulus.mobile.remotezip.RemoteZipFailureException

internal data class RemoteZipProbe(val archiveUrl: String, val contentLength: Long)

internal class RangeProbeService(private val rangeReader: HttpRangeReader) {
    @Suppress("ReturnCount")
    suspend fun probe(url: String): Result<RemoteZipProbe> {
        val head = rangeReader.head(url).getOrElse { failure ->
            return Result.failure(failure)
        }
        if (!head.acceptsRanges) {
            return Result.failure(
                RemoteZipFailureException(
                    stage = PROBE_STAGE,
                    code = "RANGE_NOT_SUPPORTED",
                    message = "Remote archive $url does not advertise byte-range support"
                )
            )
        }
        val initialRange = rangeReader.readRange(url, 0L, 0L).getOrElse { failure ->
            return Result.failure(failure)
        }
        val contentLength = head.contentLength
            ?: parseTotalLength(initialRange.contentRange)
            ?: return Result.failure(
                RemoteZipFailureException(
                    stage = PROBE_STAGE,
                    code = "MISSING_CONTENT_LENGTH",
                    message = "Remote archive $url did not provide a usable content length"
                )
            )
        if (contentLength <= 0L) {
            return Result.failure(
                RemoteZipFailureException(
                    stage = PROBE_STAGE,
                    code = "INVALID_CONTENT_LENGTH",
                    message = "Remote archive $url reported invalid content length $contentLength"
                )
            )
        }
        return Result.success(RemoteZipProbe(url, contentLength))
    }

    @Suppress("ReturnCount")
    private fun parseTotalLength(contentRange: String?): Long? {
        if (contentRange == null) {
            return null
        }
        val match = CONTENT_RANGE_REGEX.matchEntire(contentRange) ?: return null
        return match.groupValues[TOTAL_LENGTH_GROUP_INDEX]
            .takeUnless { it == "*" }
            ?.toLong()
    }

    private companion object {
        const val PROBE_STAGE = "PROBE"
        const val TOTAL_LENGTH_GROUP_INDEX = 3
        val CONTENT_RANGE_REGEX = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
