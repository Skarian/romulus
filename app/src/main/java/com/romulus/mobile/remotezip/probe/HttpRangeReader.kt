package com.romulus.mobile.remotezip.probe

internal data class RangeReadResult(
    val statusCode: Int,
    val contentRange: String?,
    val body: ByteArray,
    val contentLength: Long?
)

internal interface HttpRangeReader {
    suspend fun head(url: String): Result<RangeReadResult>

    suspend fun readRange(
        url: String,
        startInclusive: Long,
        endInclusive: Long
    ): Result<RangeReadResult>
}
