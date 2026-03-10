package com.romulus.mobile.remotezip.enumerate

import com.romulus.mobile.remotezip.EnumerateRemoteZipRequest
import com.romulus.mobile.remotezip.EnumeratedRemoteZip
import com.romulus.mobile.remotezip.probe.HttpRangeReader
import com.romulus.mobile.remotezip.probe.RangeProbeService
import com.romulus.mobile.remotezip.probe.RemoteZipProbe
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

@Suppress("RedundantSuspendModifier", "UnusedParameter", "UnusedPrivateProperty")
internal class RemoteZipEnumerator(
    private val probeService: RangeProbeService,
    private val seekableChannelFactory: (RemoteZipProbe) -> RemoteZipSeekableChannel
) {
    suspend fun enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip> =
        Result.failure(UnsupportedOperationException("RemoteZipEnumerator is not wired yet"))
}

internal class RemoteZipSeekableChannel(
    private val probe: RemoteZipProbe,
    private val rangeReader: HttpRangeReader
) : SeekableByteChannel {
    private var open = true
    private var position = 0L

    @Suppress("UnusedPrivateProperty")
    private val unusedRangeReader = rangeReader

    override fun read(dst: ByteBuffer): Int =
        throw UnsupportedOperationException("RemoteZipSeekableChannel is not wired yet")

    override fun write(src: ByteBuffer): Int =
        throw UnsupportedOperationException("RemoteZipSeekableChannel is read-only")

    override fun position(): Long = position

    override fun position(newPosition: Long): SeekableByteChannel {
        position = newPosition
        return this
    }

    override fun size(): Long = probe.contentLength

    override fun truncate(size: Long): SeekableByteChannel =
        throw UnsupportedOperationException("RemoteZipSeekableChannel is read-only")

    override fun isOpen(): Boolean = open

    override fun close() = run {
        open = false
    }
}
