package com.romulus.mobile.remotezip

import com.romulus.mobile.remotezip.copy.SelectedEntryCopier
import com.romulus.mobile.remotezip.enumerate.RemoteZipEnumerator
import com.romulus.mobile.remotezip.enumerate.RemoteZipSeekableChannel
import com.romulus.mobile.remotezip.probe.OkHttpHttpRangeReader
import com.romulus.mobile.remotezip.probe.RangeProbeService
import okhttp3.OkHttpClient

class RemoteZipFacade internal constructor(
    private val enumerator: RemoteZipEnumerator?,
    private val copier: SelectedEntryCopier?
) {
    constructor() : this(
        enumerator = null,
        copier = null
    )

    suspend fun enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip> =
        enumerator?.enumerate(request)
            ?: Result.failure(UnsupportedOperationException("RemoteZipFacade is not wired yet"))

    suspend fun copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit> =
        copier?.copy(request)
            ?: Result.failure(UnsupportedOperationException("RemoteZipFacade is not wired yet"))

    companion object {
        fun create(): RemoteZipFacade {
            val rangeReader = OkHttpHttpRangeReader(OkHttpClient.Builder().build())
            val probeService = RangeProbeService(rangeReader)
            val enumerator = RemoteZipEnumerator(
                probeService = probeService,
                seekableChannelFactory = { probe ->
                    RemoteZipSeekableChannel(
                        probe = probe,
                        rangeReader = rangeReader
                    )
                }
            )
            return RemoteZipFacade(
                enumerator = enumerator,
                copier = SelectedEntryCopier(
                    probeService = probeService,
                    seekableChannelFactory = { probe ->
                        RemoteZipSeekableChannel(
                            probe = probe,
                            rangeReader = rangeReader
                        )
                    }
                )
            )
        }
    }
}
