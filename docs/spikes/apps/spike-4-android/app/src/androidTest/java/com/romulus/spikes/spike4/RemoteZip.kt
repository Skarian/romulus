package com.romulus.spikes.spike4

import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.time.Duration
import java.time.Instant
import java.util.Enumeration
import java.util.zip.ZipException
import kotlin.math.max
import kotlin.math.min

interface RangeHttpClient {
    fun probe(caseId: String, url: HttpUrl): RemoteArchiveInfo
    fun read(caseId: String, stage: Spike4Stage, url: HttpUrl, start: Long, endInclusive: Long): RangeReadResult
}

class HttpTraceRecorder {
    private val events = mutableListOf<HttpTraceEvent>()

    fun record(event: HttpTraceEvent) {
        events += event
    }

    fun all(): List<HttpTraceEvent> = events.toList()
}

class OkHttpRangeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build(),
    private val recorder: HttpTraceRecorder,
) : RangeHttpClient {
    override fun probe(caseId: String, url: HttpUrl): RemoteArchiveInfo {
        val request = Request.Builder().url(url).head().build()
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
                    throw Spike4FailureException(Spike4Stage.PROBE, "INVALID_HTTP_RESPONSE", "HEAD probe failed for $url with HTTP ${response.code}")
                }
                val archiveLength = contentLength ?: throw Spike4FailureException(
                    Spike4Stage.PROBE,
                    "MISSING_CONTENT_LENGTH",
                    "HEAD probe for $url did not include Content-Length"
                )
                return RemoteArchiveInfo(
                    contentLength = archiveLength,
                    acceptsRanges = response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true,
                    rangeModeHint = response.header("X-Spike-Range-Mode"),
                )
            }
        } catch (failure: Exception) {
            failureMessage = failure.message
            if (failure is Spike4FailureException) {
                throw failure
            }
            throw Spike4FailureException(Spike4Stage.PROBE, "INVALID_HTTP_RESPONSE", "HEAD probe failed for $url: ${failure.message}", failure)
        } finally {
            recorder.record(
                HttpTraceEvent(
                    timestamp = startedAt.toString(),
                    caseId = caseId,
                    stage = Spike4Stage.PROBE,
                    method = "HEAD",
                    path = url.encodedPath,
                    requestRange = null,
                    responseCode = responseCode,
                    contentLength = contentLength,
                    contentRange = contentRange,
                    elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis(),
                    failureMessage = failureMessage,
                )
            )
        }
    }

    override fun read(caseId: String, stage: Spike4Stage, url: HttpUrl, start: Long, endInclusive: Long): RangeReadResult {
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
                    200 -> throw Spike4FailureException(stage, "FULL_BODY_RESPONSE", "GET $url returned 200 for $requestedRange instead of 206")
                    416 -> throw Spike4FailureException(stage, "RANGE_WINDOW_REJECTED", "GET $url rejected range $requestedRange with HTTP 416")
                    else -> throw Spike4FailureException(stage, "INVALID_HTTP_RESPONSE", "GET $url returned HTTP ${response.code} for $requestedRange")
                }
            }
        } catch (failure: Exception) {
            failureMessage = failure.message
            if (failure is Spike4FailureException) {
                throw failure
            }
            throw Spike4FailureException(stage, "INVALID_HTTP_RESPONSE", "GET $url failed for $requestedRange: ${failure.message}", failure)
        } finally {
            recorder.record(
                HttpTraceEvent(
                    timestamp = startedAt.toString(),
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
                )
            )
        }
    }

    private fun handlePartialContent(
        stage: Spike4Stage,
        url: HttpUrl,
        requestedStart: Long,
        requestedEndInclusive: Long,
        response: Response,
    ): RangeReadResult {
        val parsedRange = parseContentRange(response.header("Content-Range")) ?: throw Spike4FailureException(
            stage,
            "MISSING_CONTENT_RANGE",
            "GET $url omitted Content-Range for 206 response"
        )
        if (parsedRange.first != requestedStart) {
            throw Spike4FailureException(
                stage,
                "UNEXPECTED_CONTENT_RANGE_START",
                "GET $url returned unexpected Content-Range start ${parsedRange.first} for requested $requestedStart-$requestedEndInclusive"
            )
        }
        val body = response.body?.bytes() ?: ByteArray(0)
        val expectedLength = parsedRange.second - parsedRange.first + 1
        if (body.size.toLong() != expectedLength) {
            throw Spike4FailureException(
                stage,
                "INVALID_BODY_LENGTH",
                "GET $url returned ${body.size} bytes for Content-Range ${parsedRange.first}-${parsedRange.second}"
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

class RemoteSeekableByteChannel(
    private val caseId: String,
    private val archiveUrl: HttpUrl,
    private val archiveSize: Long,
    private val rangeHttpClient: RangeHttpClient,
    private val tailPrefetchBytes: Int = 131072,
    private val readPrefetchBytes: Int = 65536,
) : SeekableByteChannel {
    private var currentPosition = 0L
    private var open = true
    private var currentStage = Spike4Stage.ZIP_ENUMERATION
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

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    override fun close() {
        open = false
    }

    fun setStage(stage: Spike4Stage) {
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
}

class RemoteZipSession(
    private val caseId: String,
    private val archiveSource: String,
    private val archiveUrl: HttpUrl,
    private val rangeHttpClient: RangeHttpClient,
) : AutoCloseable {
    private val archiveInfo = rangeHttpClient.probe(caseId, archiveUrl).also { info ->
        if (!info.acceptsRanges) {
            throw Spike4FailureException(Spike4Stage.PROBE, "RANGE_NOT_SUPPORTED", "Remote archive $archiveUrl does not advertise byte-range support")
        }
    }
    private val channel = RemoteSeekableByteChannel(caseId, archiveUrl, archiveInfo.contentLength, rangeHttpClient)
    private val zipFile = openZipFile()
    private var enumeratedEntries: List<EnumeratedEntry>? = null
    private val archiveEntriesByIdentity = linkedMapOf<EntryIdentity, ZipArchiveEntry>()

    fun enumerate(): List<EnumeratedEntry> {
        enumeratedEntries?.let { return it }
        return try {
            channel.setStage(Spike4Stage.ZIP_ENUMERATION)
            val entries = zipFile.entries.toList().map { entry ->
                val identity = entry.toIdentity()
                archiveEntriesByIdentity[identity] = entry
                EnumeratedEntry(
                    identity = identity,
                    isDirectory = entry.isDirectory,
                    compressionMethod = entry.method,
                    encrypted = entry.generalPurposeBit.usesEncryption(),
                )
            }
            enumeratedEntries = entries
            entries
        } catch (failure: Exception) {
            throw mapZipFailure(Spike4Stage.ZIP_ENUMERATION, failure)
        }
    }

    fun copySelected(entries: List<EntryIdentity>, outputDir: File): List<File> {
        try {
            if (enumeratedEntries == null) {
                enumerate()
            }
            outputDir.mkdirs()
            channel.setStage(Spike4Stage.LOCAL_COPY)
            return entries.map { identity ->
                val archiveEntry = archiveEntriesByIdentity[identity] ?: throw Spike4FailureException(
                    Spike4Stage.LOCAL_COPY,
                    "SELECTED_ENTRY_MISSING",
                    "Archive entry $identity was not present after enumeration"
                )
                if (!zipFile.canReadEntryData(archiveEntry)) {
                    throw Spike4FailureException(
                        Spike4Stage.LOCAL_COPY,
                        if (archiveEntry.generalPurposeBit.usesEncryption()) "UNSUPPORTED_ENCRYPTION" else "UNSUPPORTED_ZIP_FEATURE",
                        "Commons Compress cannot read ${archiveEntry.name} from $archiveSource"
                    )
                }
                val fileName = "%010d__%s".format(identity.localHeaderOffset, archiveEntry.name.substringAfterLast('/'))
                val outputFile = outputDir.resolve(fileName)
                zipFile.getInputStream(archiveEntry).use { input ->
                    copyEntry(input, outputFile)
                }
                outputFile
            }
        } catch (failure: Exception) {
            throw mapZipFailure(Spike4Stage.LOCAL_COPY, failure)
        }
    }

    override fun close() {
        zipFile.close()
        channel.close()
    }

    private fun openZipFile(): ZipFile {
        return try {
            channel.setStage(Spike4Stage.ZIP_ENUMERATION)
            @Suppress("DEPRECATION")
            ZipFile.builder()
                .setSeekableByteChannel(channel)
                .setIgnoreLocalFileHeader(true)
                .get()
        } catch (failure: Exception) {
            throw mapZipFailure(Spike4Stage.ZIP_ENUMERATION, failure)
        }
    }

    private fun copyEntry(input: InputStream, outputFile: File) {
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    private fun mapZipFailure(stage: Spike4Stage, failure: Exception): Spike4FailureException {
        if (failure is Spike4FailureException) {
            return failure
        }
        if (failure is ArchiveException || failure is ZipException || failure is IOException) {
            return Spike4FailureException(stage, "INVALID_ZIP", failure.message ?: "invalid zip", failure)
        }
        return Spike4FailureException(stage, "INVALID_ZIP", failure.message ?: failure.javaClass.simpleName, failure)
    }

    private fun Enumeration<ZipArchiveEntry>.toList(): List<ZipArchiveEntry> {
        val items = mutableListOf<ZipArchiveEntry>()
        while (hasMoreElements()) {
            items += nextElement()
        }
        return items
    }

    private fun ZipArchiveEntry.toIdentity(): EntryIdentity {
        return EntryIdentity(
            archiveSource = archiveSource,
            entryPath = name,
            localHeaderOffset = localHeaderOffset,
            compressedSize = compressedSize,
            uncompressedSize = size,
            crc32 = crc.takeIf { it >= 0 },
        )
    }
}

object IgnoreGlobFilter {
    fun apply(
        entries: List<EnumeratedEntry>,
        ignoreGlobs: List<String>,
        selectedPaths: List<String>,
    ): FilteredEntries {
        val files = entries.filterNot { it.isDirectory }
        val ignored = files.filter { entry ->
            val baseName = entry.identity.entryPath.substringAfterLast('/')
            ignoreGlobs.any { globMatches(baseName, it) }
        }
        val ignoredIdentities = ignored.map { it.identity }.toSet()
        val visible = files.filterNot { it.identity in ignoredIdentities }
        val selected = selectedPaths.map { path ->
            resolveSelectedEntry(visible, path)
        }
        return FilteredEntries(
            ignoredEntries = ignored,
            visibleEntries = visible,
            selectedEntries = selected,
        )
    }

    private fun resolveSelectedEntry(
        visibleEntries: List<EnumeratedEntry>,
        selectedPath: String,
    ): EnumeratedEntry {
        visibleEntries.singleOrNull { it.identity.entryPath == selectedPath }?.let { return it }
        val basenameMatches = visibleEntries.filter {
            it.identity.entryPath.substringAfterLast('/') == selectedPath.substringAfterLast('/')
        }
        if (basenameMatches.size == 1) {
            return basenameMatches.single()
        }
        if (basenameMatches.size > 1) {
            throw Spike4FailureException(
                Spike4Stage.ARCHIVE_SELECTION,
                "AMBIGUOUS_SELECTED_ENTRY",
                "Selected entry $selectedPath matched multiple visible entries by basename"
            )
        }
        throw Spike4FailureException(
            Spike4Stage.ARCHIVE_SELECTION,
            "SELECTED_ENTRY_MISSING",
            "Selected entry $selectedPath is not present in the visible candidate set"
        )
    }

    private fun globMatches(candidate: String, glob: String): Boolean {
        val regex = buildString {
            append("^")
            glob.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> append("\\").append(ch)
                    else -> append(ch)
                }
            }
            append("$")
        }
        return Regex(regex, setOf(RegexOption.IGNORE_CASE)).matches(candidate)
    }
}

fun String.toArchiveHttpUrl(): HttpUrl = toHttpUrl()
