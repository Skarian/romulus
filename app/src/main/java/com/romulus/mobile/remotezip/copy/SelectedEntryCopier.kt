package com.romulus.mobile.remotezip.copy

import com.romulus.mobile.remotezip.CopySelectedEntryRequest
import com.romulus.mobile.remotezip.enumerate.RemoteZipEnumerator
import com.romulus.mobile.remotezip.probe.HttpRangeReader
import com.romulus.mobile.remotezip.probe.RangeProbeService

@Suppress("RedundantSuspendModifier", "UnusedParameter", "UnusedPrivateProperty")
internal class SelectedEntryCopier(
    private val probeService: RangeProbeService,
    private val enumerator: RemoteZipEnumerator,
    private val rangeReader: HttpRangeReader
) {
    suspend fun copy(request: CopySelectedEntryRequest): Result<Unit> = Result.failure(
        UnsupportedOperationException("SelectedEntryCopier is not wired yet")
    )
}
