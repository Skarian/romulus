package com.romulus.mobile.remotezip.copy

import com.romulus.mobile.remotezip.CopySelectedEntryRequest
import com.romulus.mobile.remotezip.enumerate.RemoteZipArchiveSession
import com.romulus.mobile.remotezip.enumerate.RemoteZipSeekableChannel
import com.romulus.mobile.remotezip.probe.RangeProbeService
import com.romulus.mobile.remotezip.probe.RemoteZipProbe

internal class SelectedEntryCopier(
    private val probeService: RangeProbeService,
    private val seekableChannelFactory: (RemoteZipProbe) -> RemoteZipSeekableChannel
) {
    suspend fun copy(request: CopySelectedEntryRequest): Result<Unit> {
        val probe = probeService.probe(request.archiveUrl).getOrElse { failure ->
            return Result.failure(failure)
        }
        return RemoteZipArchiveSession(
            seekableChannel = seekableChannelFactory(probe)
        ).use { session ->
            session.copySelectedEntry(request)
        }
    }
}
