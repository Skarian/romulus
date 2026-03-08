package com.romulus.spikes.spike3.zip

import com.romulus.spikes.spike3.http.RangeHttpClient
import com.romulus.spikes.spike3.model.FailureStage
import okhttp3.HttpUrl
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import kotlin.math.max
import kotlin.math.min

class RemoteSeekableByteChannel(
    private val caseId: String,
    private val archiveUrl: HttpUrl,
    private val archiveSize: Long,
    private val rangeHttpClient: RangeHttpClient,
    private val tailPrefetchBytes: Int = DEFAULT_TAIL_PREFETCH_BYTES,
    private val readPrefetchBytes: Int = DEFAULT_READ_PREFETCH_BYTES,
) : SeekableByteChannel {
    private var currentPosition = 0L
    private var open = true
    private var currentStage = FailureStage.ZIP_ENUMERATION
    private var tailWindow: ByteWindow? = null
    private var lastWindow: ByteWindow? = null

    override fun isOpen(): Boolean = open

    override fun position(): Long = currentPosition

    override fun position(newPosition: Long): SeekableByteChannel {
        checkOpen()
        require(newPosition >= 0) { "newPosition must be non-negative" }
        currentPosition = newPosition
        return this
    }

    override fun read(dst: ByteBuffer): Int {
        checkOpen()
        if (!dst.hasRemaining()) {
            return 0
        }
        if (currentPosition >= archiveSize) {
            return -1
        }

        var totalRead = 0
        while (dst.hasRemaining() && currentPosition < archiveSize) {
            val window = resolveWindow(currentPosition, dst.remaining()) ?: break
            val offsetInWindow = (currentPosition - window.start).toInt()
            val available = min(dst.remaining(), window.bytes.size - offsetInWindow)
            dst.put(window.bytes, offsetInWindow, available)
            currentPosition += available
            totalRead += available
        }
        return if (totalRead == 0) -1 else totalRead
    }

    override fun size(): Long = archiveSize

    override fun truncate(size: Long): SeekableByteChannel {
        throw NonWritableChannelException()
    }

    override fun write(src: ByteBuffer): Int {
        throw NonWritableChannelException()
    }

    override fun close() {
        open = false
    }

    fun setStage(stage: FailureStage) {
        currentStage = stage
    }

    private fun resolveWindow(position: Long, requestedLength: Int): ByteWindow? {
        if (position >= archiveSize) {
            return null
        }
        val requestedEndExclusive = min(archiveSize, position + requestedLength.toLong())
        tailWindow?.takeIf { it.contains(position, requestedEndExclusive) }?.let { return it }
        lastWindow?.takeIf { it.contains(position, requestedEndExclusive) }?.let { return it }
        val tailStart = max(0, archiveSize - tailPrefetchBytes.toLong())
        if (position >= tailStart) {
            return loadTailWindow(tailStart)
        }
        return loadReadWindow(position, requestedLength)
    }

    private fun loadTailWindow(start: Long): ByteWindow {
        val requestedEndInclusive = start + tailPrefetchBytes - 1L
        val result = rangeHttpClient.read(caseId, currentStage, archiveUrl, start, requestedEndInclusive)
        return ByteWindow(result.actualStart, result.bytes).also { tailWindow = it }
    }

    private fun loadReadWindow(start: Long, requestedLength: Int): ByteWindow {
        val windowLength = max(requestedLength, readPrefetchBytes)
        val requestedEndInclusive = start + windowLength - 1L
        val result = rangeHttpClient.read(caseId, currentStage, archiveUrl, start, requestedEndInclusive)
        return ByteWindow(result.actualStart, result.bytes).also { lastWindow = it }
    }

    private fun checkOpen() {
        check(open) { "Channel is closed" }
    }

    private data class ByteWindow(
        val start: Long,
        val bytes: ByteArray,
    ) {
        val endExclusive: Long = start + bytes.size

        fun contains(requestStart: Long, requestEndExclusive: Long): Boolean {
            return requestStart >= start && requestEndExclusive <= endExclusive
        }
    }

    private companion object {
        const val DEFAULT_TAIL_PREFETCH_BYTES = 131072
        const val DEFAULT_READ_PREFETCH_BYTES = 65536
    }
}
