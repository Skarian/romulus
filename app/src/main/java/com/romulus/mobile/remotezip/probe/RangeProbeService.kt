package com.romulus.mobile.remotezip.probe

internal data class RemoteZipProbe(val archiveUrl: String, val contentLength: Long)

@Suppress("RedundantSuspendModifier", "UnusedParameter", "UnusedPrivateProperty")
internal class RangeProbeService(private val rangeReader: HttpRangeReader) {
    suspend fun probe(url: String): Result<RemoteZipProbe> = Result.failure(
        UnsupportedOperationException("RangeProbeService is not wired yet")
    )
}
