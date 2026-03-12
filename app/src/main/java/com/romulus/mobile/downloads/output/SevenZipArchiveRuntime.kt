@file:Suppress(
    "ArgumentListWrapping",
    "BinaryExpressionWrapping",
    "ChainMethodContinuation",
    "ClassSignature",
    "FunctionSignature",
    "ImplicitDefaultLocale",
    "InjectDispatcher",
    "MaximumLineLength",
    "ReturnCount"
)

package com.romulus.mobile.downloads.output

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream

internal class SevenZipArchiveRuntime : ArchiveRuntime {
    override suspend fun inspect(
        archiveFile: File
    ): Result<List<ArchiveRuntimeEntry>> = captureResult {
        withContext(Dispatchers.IO) {
            withOpenedArchive(archiveFile) { archive ->
                buildList {
                    repeat(archive.numberOfItems) { index ->
                        val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                        if (!isFolder) {
                            val rawEntryPath = resolveEntryPath(archive, index)
                            add(
                                ArchiveRuntimeEntry(
                                    rawEntryPath = rawEntryPath,
                                    isArchiveCandidate = isSupportedArchiveName(rawEntryPath)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun extract(
        archiveFile: File,
        targetFiles: Map<String, File>,
        control: FinalizationControl?
    ): Result<ArchiveRuntimePassResult> = captureResult {
        withContext(Dispatchers.IO) {
            withOpenedArchive(archiveFile) { archive ->
                val indices = buildList {
                    repeat(archive.numberOfItems) { index ->
                        val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                        if (!isFolder) {
                            add(index)
                        }
                    }
                }
                val callback = ExtractionCallback(
                    archive = archive,
                    targetFiles = targetFiles,
                    control = control
                )
                try {
                    archive.extract(indices.toIntArray(), false, callback)
                    callback.toResult()
                } catch (@Suppress("TooGenericExceptionCaught") throwable: Exception) {
                    callback.cleanupPartialOutputs()
                    throw throwable
                }
            }
        }
    }

    private fun <T> withOpenedArchive(archiveFile: File, block: (IInArchive) -> T): T {
        val randomAccessFile = RandomAccessFile(archiveFile, "r")
        val archive = try {
            SevenZip.openInArchive(
                resolveArchiveFormat(archiveFile),
                RandomAccessFileInStream(randomAccessFile),
                NoOpOpenCallback
            )
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            randomAccessFile.closeQuietly()
            throw throwable
        }
        return try {
            block(archive)
        } finally {
            try {
                archive.close()
            } catch (_: SevenZipException) {
                Unit
            }
            randomAccessFile.closeQuietly()
        }
    }

    private fun resolveArchiveFormat(archiveFile: File): ArchiveFormat = when (
        archiveFile.extension.lowercase()
    ) {
        "zip" -> ArchiveFormat.ZIP
        "rar" -> ArchiveFormat.RAR
        "7z" -> ArchiveFormat.SEVEN_ZIP
        else -> error("Unsupported archive: ${archiveFile.name}")
    }

    private fun resolveEntryPath(archive: IInArchive, index: Int): String {
        val archivePath = archive.getProperty(index, PropID.PATH) as? String
        return archivePath?.takeIf(String::isNotBlank) ?: "entry-$index"
    }

    private fun isSupportedArchiveName(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_ARCHIVE_EXTENSIONS
    }

    private class ExtractionCallback(
        private val archive: IInArchive,
        private val targetFiles: Map<String, File>,
        private val control: FinalizationControl?
    ) : IArchiveExtractCallback {
        private val activeOutputs = ConcurrentHashMap<Int, ActiveOutput>()
        private val outcomes = ConcurrentHashMap<Int, ExtractionOutcome>()
        private val currentIndex = ThreadLocal<Int>()

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            currentIndex.set(index)
            val rawEntryPath = resolveEntryPath(archive, index)
            val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
            val outcome = outcomes.getOrPut(index) {
                ExtractionOutcome(
                    rawEntryPath = rawEntryPath,
                    isArchiveCandidate = isSupportedArchiveName(rawEntryPath)
                )
            }
            outcome.askMode = extractAskMode.name
            if (extractAskMode != ExtractAskMode.EXTRACT || isFolder) {
                return null
            }
            val targetFile = checkNotNull(targetFiles[rawEntryPath]) {
                "Missing local extraction target for $rawEntryPath"
            }
            targetFile.parentFile?.mkdirs()
            val tempFile = targetFile.resolveSibling(".${targetFile.name}.part-$index")
            val outputStream = FileOutputStream(tempFile)
            activeOutputs[index] = ActiveOutput(
                targetFile = targetFile,
                tempFile = tempFile,
                outputStream = outputStream
            )
            return ISequentialOutStream { data ->
                control.failIfStopped()
                outputStream.write(data)
                activeOutputs[index]?.bytesWritten = max(
                    activeOutputs[index]?.bytesWritten ?: 0L,
                    (activeOutputs[index]?.bytesWritten ?: 0L) + data.size.toLong()
                )
                control?.pulse()?.getOrElse { throwable ->
                    throw throwable
                }
                control.failIfStopped()
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            val index = currentIndex.get() ?: return
            val outcome = outcomes.getOrPut(index) {
                val rawEntryPath = resolveEntryPath(archive, index)
                ExtractionOutcome(
                    rawEntryPath = rawEntryPath,
                    isArchiveCandidate = isSupportedArchiveName(rawEntryPath)
                )
            }
            outcome.operationResult = extractOperationResult.name
            val activeOutput = activeOutputs.remove(index) ?: return
            activeOutput.outputStream.flush()
            activeOutput.outputStream.close()
            outcome.bytesWritten = max(outcome.bytesWritten, activeOutput.bytesWritten)
            if (extractOperationResult == ExtractOperationResult.OK) {
                activeOutput.targetFile.parentFile?.mkdirs()
                if (!activeOutput.tempFile.renameTo(activeOutput.targetFile)) {
                    activeOutput.tempFile.copyTo(activeOutput.targetFile, overwrite = true)
                    activeOutput.tempFile.delete()
                }
                outcome.localArtifactPath = activeOutput.targetFile.absolutePath
            } else {
                activeOutput.tempFile.delete()
            }
        }

        override fun setCompleted(completeValue: Long) = Unit

        override fun setTotal(total: Long) = Unit

        fun toResult(): ArchiveRuntimePassResult {
            val outputs = outcomes.values
                .mapNotNull { outcome ->
                    outcome.localArtifactPath?.let { localArtifactPath ->
                        ArchiveRuntimeExtractedArtifact(
                            rawEntryPath = outcome.rawEntryPath,
                            localArtifactPath = localArtifactPath,
                            sizeBytes = outcome.bytesWritten,
                            isArchiveCandidate = outcome.isArchiveCandidate
                        )
                    }
                }
                .sortedBy(ArchiveRuntimeExtractedArtifact::rawEntryPath)
            val failureMessage = outcomes.values
                .firstOrNull { outcome ->
                    outcome.operationResult != null &&
                        outcome.operationResult != ExtractOperationResult.OK.name
                }?.let { outcome ->
                    "Extraction failed for ${outcome.rawEntryPath} with ${outcome.operationResult}"
                }
            return ArchiveRuntimePassResult(
                outputs = outputs,
                failureMessage = failureMessage
            )
        }

        fun cleanupPartialOutputs() {
            activeOutputs.values.forEach { activeOutput ->
                runCatching { activeOutput.outputStream.close() }
                runCatching { activeOutput.tempFile.delete() }
                runCatching { activeOutput.targetFile.delete() }
            }
            activeOutputs.clear()
            targetFiles.values.forEach { targetFile ->
                runCatching { targetFile.delete() }
            }
        }

        private data class ActiveOutput(
            val targetFile: File,
            val tempFile: File,
            val outputStream: FileOutputStream,
            var bytesWritten: Long = 0
        )

        private data class ExtractionOutcome(
            val rawEntryPath: String,
            val isArchiveCandidate: Boolean,
            var askMode: String? = null,
            var bytesWritten: Long = 0,
            var operationResult: String? = null,
            var localArtifactPath: String? = null
        )
    }

    private object NoOpOpenCallback : IArchiveOpenCallback {
        override fun setCompleted(files: Long?, bytes: Long?) = Unit

        override fun setTotal(files: Long?, bytes: Long?) = Unit
    }

    private companion object {
        val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z")
    }
}

private fun RandomAccessFile.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        Unit
    }
}

private fun resolveEntryPath(archive: IInArchive, index: Int): String {
    val archivePath = archive.getProperty(index, PropID.PATH) as? String
    return archivePath?.takeIf(String::isNotBlank) ?: "entry-$index"
}

private fun isSupportedArchiveName(path: String): Boolean {
    val extension = path.substringAfterLast('.', "").lowercase()
    return extension in setOf("zip", "rar", "7z")
}

@Suppress("TooGenericExceptionCaught")
private inline fun <T> captureResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellationException: CancellationException) {
    throw cancellationException
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
