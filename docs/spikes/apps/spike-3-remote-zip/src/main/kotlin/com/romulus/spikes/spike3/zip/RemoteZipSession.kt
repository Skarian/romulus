package com.romulus.spikes.spike3.zip

import com.romulus.spikes.spike3.errors.FailureCodes
import com.romulus.spikes.spike3.errors.Spike3FailureException
import com.romulus.spikes.spike3.http.RangeHttpClient
import com.romulus.spikes.spike3.model.EntryIdentity
import com.romulus.spikes.spike3.model.EnumeratedEntry
import com.romulus.spikes.spike3.model.FailureStage
import okhttp3.HttpUrl
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Enumeration
import java.util.zip.ZipException

class RemoteZipSession(
    private val caseId: String,
    private val fixtureName: String,
    private val archiveUrl: HttpUrl,
    private val rangeHttpClient: RangeHttpClient,
) : AutoCloseable {
    private val archiveInfo = rangeHttpClient.probe(caseId, archiveUrl).also { info ->
        if (!info.acceptsRanges) {
            throw Spike3FailureException(
                stage = FailureStage.PROBE,
                errorCode = FailureCodes.RANGE_NOT_SUPPORTED,
                message = "Remote archive $archiveUrl does not advertise byte-range support",
            )
        }
    }
    private val channel = RemoteSeekableByteChannel(caseId, archiveUrl, archiveInfo.contentLength, rangeHttpClient)
    private val zipFile = openZipFile()
    private var enumeratedEntries: List<EnumeratedEntry>? = null
    private val archiveEntriesByIdentity = linkedMapOf<EntryIdentity, ZipArchiveEntry>()

    fun enumerate(): List<EnumeratedEntry> {
        enumeratedEntries?.let { return it }
        return try {
            channel.setStage(FailureStage.ZIP_ENUMERATION)
            val entries = zipFile.entries.toList().map { entry ->
                val identity = entry.toIdentity(fixtureName)
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
            throw mapZipFailure(FailureStage.ZIP_ENUMERATION, failure)
        }
    }

    fun copySelected(entries: List<EntryIdentity>, outputDir: Path): List<Path> {
        try {
            if (enumeratedEntries == null) {
                enumerate()
            }
            Files.createDirectories(outputDir)
            channel.setStage(FailureStage.DOWNLOAD)
            return entries.map { identity ->
                val archiveEntry = archiveEntriesByIdentity[identity] ?: throw Spike3FailureException(
                    stage = FailureStage.DOWNLOAD,
                    errorCode = FailureCodes.SELECTED_ENTRY_MISSING,
                    message = "Archive entry $identity was not present after enumeration",
                )
                if (!zipFile.canReadEntryData(archiveEntry)) {
                    val errorCode = if (archiveEntry.generalPurposeBit.usesEncryption()) {
                        FailureCodes.UNSUPPORTED_ENCRYPTION
                    } else {
                        FailureCodes.UNSUPPORTED_ZIP_FEATURE
                    }
                    throw Spike3FailureException(
                        stage = FailureStage.DOWNLOAD,
                        errorCode = errorCode,
                        message = "Commons Compress cannot read ${archiveEntry.name} from $fixtureName",
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
            throw mapZipFailure(FailureStage.DOWNLOAD, failure)
        }
    }

    override fun close() {
        zipFile.close()
        channel.close()
    }

    private fun openZipFile(): ZipFile {
        return try {
            channel.setStage(FailureStage.ZIP_ENUMERATION)
            @Suppress("DEPRECATION")
            ZipFile.builder()
                .setSeekableByteChannel(channel)
                .setIgnoreLocalFileHeader(true)
                .get()
        } catch (failure: Exception) {
            throw mapZipFailure(FailureStage.ZIP_ENUMERATION, failure)
        }
    }

    private fun copyEntry(input: InputStream, outputFile: Path) {
        Files.newOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    }

    private fun mapZipFailure(stage: FailureStage, failure: Exception): Spike3FailureException {
        if (failure is Spike3FailureException) {
            return failure
        }
        findSpikeFailure(failure)?.let { return it }
        if (failure is ArchiveException || failure is ZipException) {
            return Spike3FailureException(
                stage = stage,
                errorCode = FailureCodes.INVALID_ZIP,
                message = failure.message ?: FailureCodes.INVALID_ZIP,
                cause = failure,
            )
        }
        if (failure is IOException) {
            return Spike3FailureException(
                stage = stage,
                errorCode = FailureCodes.INVALID_ZIP,
                message = failure.message ?: FailureCodes.INVALID_ZIP,
                cause = failure,
            )
        }
        return Spike3FailureException(
            stage = stage,
            errorCode = FailureCodes.INVALID_ZIP,
            message = failure.message ?: failure.javaClass.simpleName,
            cause = failure,
        )
    }

    private fun findSpikeFailure(failure: Throwable): Spike3FailureException? {
        var current: Throwable? = failure.cause
        while (current != null) {
            if (current is Spike3FailureException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun Enumeration<ZipArchiveEntry>.toList(): List<ZipArchiveEntry> {
        val items = mutableListOf<ZipArchiveEntry>()
        while (hasMoreElements()) {
            items += nextElement()
        }
        return items
    }

    private fun ZipArchiveEntry.toIdentity(fixtureName: String): EntryIdentity {
        return EntryIdentity(
            fixtureName = fixtureName,
            entryPath = name,
            localHeaderOffset = localHeaderOffset,
            compressedSize = compressedSize,
            uncompressedSize = size,
            crc32 = crc.takeIf { it >= 0 },
        )
    }
}
