@file:Suppress(
    "ChainMethodContinuation",
    "CyclomaticComplexMethod",
    "LongMethod",
    "NestedBlockDepth",
    "ReturnCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions"
)

package com.romulus.mobile.remotezip.enumerate

import com.romulus.mobile.remotezip.ArchiveEntryDescriptor
import com.romulus.mobile.remotezip.ArchiveEntryIdentity
import com.romulus.mobile.remotezip.CopySelectedEntryRequest
import com.romulus.mobile.remotezip.EnumerateRemoteZipRequest
import com.romulus.mobile.remotezip.EnumeratedRemoteZip
import com.romulus.mobile.remotezip.RemoteZipFailureException
import com.romulus.mobile.remotezip.probe.HttpRangeReader
import com.romulus.mobile.remotezip.probe.RangeProbeService
import com.romulus.mobile.remotezip.probe.RemoteZipProbe
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.util.Enumeration
import java.util.zip.ZipException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile

internal class RemoteZipEnumerator(
    private val probeService: RangeProbeService,
    private val seekableChannelFactory: (RemoteZipProbe) -> RemoteZipSeekableChannel
) {
    suspend fun enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip> {
        val probe = probeService.probe(request.archiveUrl).getOrElse { failure ->
            return Result.failure(failure)
        }
        return runCatching {
            RemoteZipArchiveSession(
                seekableChannel = seekableChannelFactory(probe)
            ).use { session ->
                EnumeratedRemoteZip(
                    entries = session.enumerateEntries()
                        .filterNot(ArchiveEntryRecord::isDirectory)
                        .map(ArchiveEntryRecord::descriptor)
                )
            }
        }
    }
}

internal class RemoteZipSeekableChannel(
    private val probe: RemoteZipProbe,
    private val rangeReader: HttpRangeReader,
    private val tailPrefetchBytes: Int = 131_072,
    private val readPrefetchBytes: Int = 65_536
) : SeekableByteChannel {
    private var open = true
    private var position = 0L
    private var tailWindow: ByteWindow? = null
    private var lastWindow: ByteWindow? = null

    override fun read(dst: ByteBuffer): Int {
        check(open) { "Channel is closed" }
        if (!dst.hasRemaining()) {
            return 0
        }
        if (position >= probe.contentLength) {
            return -1
        }

        var totalRead = 0
        while (dst.hasRemaining() && position < probe.contentLength) {
            val window = resolveWindow(position, dst.remaining()) ?: break
            val offsetInWindow = (position - window.start).toInt()
            val available = min(dst.remaining(), window.bytes.size - offsetInWindow)
            dst.put(window.bytes, offsetInWindow, available)
            position += available
            totalRead += available
        }
        return if (totalRead == 0) -1 else totalRead
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    override fun position(): Long = position

    override fun position(newPosition: Long): SeekableByteChannel {
        check(open) { "Channel is closed" }
        require(newPosition >= 0L) { "newPosition must be non-negative" }
        position = newPosition
        return this
    }

    override fun size(): Long = probe.contentLength

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = open

    override fun close() {
        open = false
    }

    private fun resolveWindow(position: Long, requestedLength: Int): ByteWindow? {
        if (position >= probe.contentLength) {
            return null
        }
        val requestedEndExclusive = min(probe.contentLength, position + requestedLength.toLong())
        tailWindow?.takeIf { it.contains(position, requestedEndExclusive) }?.let { return it }
        lastWindow?.takeIf { it.contains(position, requestedEndExclusive) }?.let { return it }
        val tailStart = max(0L, probe.contentLength - tailPrefetchBytes.toLong())
        return if (position >= tailStart) {
            loadTailWindow(tailStart)
        } else {
            loadReadWindow(position, requestedLength)
        }
    }

    private fun loadTailWindow(start: Long): ByteWindow {
        val endInclusive = min(
            probe.contentLength - 1L,
            start + tailPrefetchBytes.toLong() - 1L
        )
        val result = runBlocking {
            rangeReader.readRange(probe.archiveUrl, start, endInclusive)
        }.getOrElse { failure ->
            throw failure
        }
        return ByteWindow(start = start, bytes = result.body).also { tailWindow = it }
    }

    private fun loadReadWindow(start: Long, requestedLength: Int): ByteWindow {
        val windowLength = max(requestedLength, readPrefetchBytes)
        val endInclusive = min(
            probe.contentLength - 1L,
            start + windowLength.toLong() - 1L
        )
        val result = runBlocking {
            rangeReader.readRange(probe.archiveUrl, start, endInclusive)
        }.getOrElse { failure ->
            throw failure
        }
        return ByteWindow(start = start, bytes = result.body).also { lastWindow = it }
    }

    private data class ByteWindow(val start: Long, val bytes: ByteArray) {
        private val endExclusive = start + bytes.size

        fun contains(requestStart: Long, requestEndExclusive: Long): Boolean =
            requestStart >= start && requestEndExclusive <= endExclusive
    }
}

internal class RemoteZipArchiveSession(private val seekableChannel: RemoteZipSeekableChannel) :
    AutoCloseable {
    private val zipFile = openZipFile()
    private val entriesByIdentity = linkedMapOf<ArchiveEntryIdentity, ZipArchiveEntry>()
    private var enumeratedEntries: List<ArchiveEntryRecord>? = null

    fun enumerateEntries(): List<ArchiveEntryRecord> {
        enumeratedEntries?.let { return it }
        val entries = try {
            zipFile.entries.toList().map { entry ->
                val identity = entry.toIdentity()
                entriesByIdentity[identity] = entry
                ArchiveEntryRecord(
                    descriptor = ArchiveEntryDescriptor(
                        identity = identity,
                        entryPath = normalizeEntryPath(entry.name),
                        sizeBytes = max(0L, entry.size)
                    ),
                    isDirectory = entry.isDirectory
                )
            }
        } catch (failure: Exception) {
            throw mapZipFailure(stage = ZIP_ENUMERATION_STAGE, failure = failure)
        }
        enumeratedEntries = entries
        return entries
    }

    suspend fun copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit> = try {
        val destinationFile = prepareDestination(request)
        val archiveEntry = findSelectedEntry(request)
        validateArchiveEntryReadability(archiveEntry)
        val entrySize = max(0L, archiveEntry.size)
        validateResumeState(
            destinationFile = destinationFile,
            resumeByteOffset = request.resumeByteOffset,
            entrySize = entrySize
        )
        if (request.resumeByteOffset != entrySize) {
            copyEntryBytes(
                request = request,
                destinationFile = destinationFile,
                archiveEntry = archiveEntry
            )
        }
        Result.success(Unit)
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (failure: Exception) {
        Result.failure(mapZipFailure(stage = DOWNLOAD_STAGE, failure = failure))
    }

    private fun prepareDestination(request: CopySelectedEntryRequest): File {
        val destinationFile = request.destination.toFile()
        destinationFile.parentFile?.mkdirs()
        if (request.resumeByteOffset == 0L && destinationFile.exists()) {
            check(destinationFile.delete()) {
                "Destination artifact could not be cleared for a fresh archive-entry copy"
            }
        }
        return destinationFile
    }

    private fun findSelectedEntry(request: CopySelectedEntryRequest): ZipArchiveEntry =
        entriesByIdentity[request.identity]
            ?: enumerateEntries()
                .firstOrNull { record -> record.descriptor.identity == request.identity }
                ?.descriptor
                ?.identity
                ?.let(entriesByIdentity::get)
            ?: throw RemoteZipFailureException(
                stage = DOWNLOAD_STAGE,
                code = "SELECTED_ENTRY_MISSING",
                message = "Selected archive entry ${request.identity.normalizedPath} " +
                    "is no longer present"
            )

    private fun validateArchiveEntryReadability(archiveEntry: ZipArchiveEntry) {
        if (!zipFile.canReadEntryData(archiveEntry)) {
            throw RemoteZipFailureException(
                stage = DOWNLOAD_STAGE,
                code = if (archiveEntry.generalPurposeBit.usesEncryption()) {
                    "UNSUPPORTED_ENCRYPTION"
                } else {
                    "UNSUPPORTED_ZIP_FEATURE"
                },
                message = "Archive entry ${archiveEntry.name} cannot be read from the remote ZIP"
            )
        }
    }

    private fun validateResumeState(
        destinationFile: File,
        resumeByteOffset: Long,
        entrySize: Long
    ) {
        if (resumeByteOffset > entrySize) {
            throw RemoteZipFailureException(
                stage = DOWNLOAD_STAGE,
                code = "RESUME_OFFSET_INVALID",
                message = "Selected archive entry is shorter than the saved resume offset"
            )
        }
        if (resumeByteOffset > 0L) {
            check(destinationFile.exists()) {
                "Destination artifact is missing for resumed archive-entry copy"
            }
            check(destinationFile.length() == resumeByteOffset) {
                "Destination artifact length does not match the saved resume offset"
            }
        }
    }

    private suspend fun copyEntryBytes(
        request: CopySelectedEntryRequest,
        destinationFile: File,
        archiveEntry: ZipArchiveEntry
    ) {
        try {
            zipFile.getInputStream(archiveEntry).use { input ->
                skipExactly(input, request.resumeByteOffset)
                FileOutputStream(destinationFile, request.resumeByteOffset > 0L).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copiedBytes = request.resumeByteOffset
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) {
                            break
                        }
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead.toLong()
                        request.onProgress(copiedBytes)
                    }
                    output.flush()
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (failure: Exception) {
            throw mapZipFailure(stage = DOWNLOAD_STAGE, failure = failure)
        }
    }

    override fun close() {
        zipFile.close()
        seekableChannel.close()
    }

    private fun openZipFile(): ZipFile = try {
        @Suppress("DEPRECATION")
        ZipFile.builder()
            .setSeekableByteChannel(seekableChannel)
            .setIgnoreLocalFileHeader(true)
            .get()
    } catch (failure: Exception) {
        throw mapZipFailure(stage = ZIP_ENUMERATION_STAGE, failure = failure)
    }

    private fun skipExactly(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val discardBuffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val read = input.read(
                discardBuffer,
                0,
                min(discardBuffer.size.toLong(), remaining).toInt()
            )
            if (read < 0) {
                throw RemoteZipFailureException(
                    stage = DOWNLOAD_STAGE,
                    code = "RESUME_OFFSET_INVALID",
                    message = "Selected archive entry is shorter than the saved resume offset"
                )
            }
            remaining -= read.toLong()
        }
    }

    private fun Enumeration<ZipArchiveEntry>.toList(): List<ZipArchiveEntry> {
        val items = mutableListOf<ZipArchiveEntry>()
        while (hasMoreElements()) {
            items += nextElement()
        }
        return items
    }

    private fun ZipArchiveEntry.toIdentity(): ArchiveEntryIdentity = ArchiveEntryIdentity(
        localHeaderOffset = localHeaderOffset,
        compressedSize = max(0L, compressedSize),
        uncompressedSize = max(0L, size),
        crc32 = crc.takeIf { it >= 0L } ?: -1L,
        normalizedPath = normalizeEntryPath(name)
    )

    private fun normalizeEntryPath(path: String): String = path
        .replace('\\', '/')
        .trimStart('/')

    private fun mapZipFailure(stage: String, failure: Exception): RemoteZipFailureException =
        when (failure) {
            is RemoteZipFailureException -> failure
            is ArchiveException,
            is ZipException,
            is IOException -> RemoteZipFailureException(
                stage = stage,
                code = "INVALID_ZIP",
                message = failure.message ?: "Remote ZIP metadata could not be parsed",
                cause = failure
            )

            else -> RemoteZipFailureException(
                stage = stage,
                code = "INVALID_ZIP",
                message = failure.message ?: "Remote ZIP operation failed",
                cause = failure
            )
        }

    private companion object {
        const val ZIP_ENUMERATION_STAGE = "ZIP_ENUMERATION"
        const val DOWNLOAD_STAGE = "DOWNLOAD"
        const val BUFFER_SIZE = 64 * 1024
    }
}

internal data class ArchiveEntryRecord(
    val descriptor: ArchiveEntryDescriptor,
    val isDirectory: Boolean
)
