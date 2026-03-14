@file:Suppress(
    "ChainMethodContinuation",
    "ClassSignature",
    "ReturnCount"
)

package com.romulus.mobile.downloads.output

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.romulus.mobile.downloads.queue.ControlSignal
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.cancellation.CancellationException

internal interface FinalizationControl {
    fun currentSignal(): ControlSignal

    fun pulse(): Result<Unit>
}

internal class FinalizationInterruptedException(val signal: ControlSignal) :
    IllegalStateException("Finalization interrupted with $signal")

internal interface OutputFilesystem {
    suspend fun listRelativePaths(
        outputDirectoryUri: String,
        subfolder: String
    ): Result<Set<String>>

    suspend fun writeArtifactToFinalOutput(
        artifactPath: String,
        outputDirectoryUri: String,
        relativePath: String,
        control: FinalizationControl? = null
    ): Result<Long>

    suspend fun deleteFinalOutput(outputDirectoryUri: String, relativePath: String): Result<Unit>

    fun isSupportedArchive(localArtifactPath: String): Boolean
}

internal class AndroidOutputFilesystem(private val context: Context) : OutputFilesystem {
    override suspend fun listRelativePaths(
        outputDirectoryUri: String,
        subfolder: String
    ): Result<Set<String>> = captureResult {
        val directory = resolveDirectory(
            outputDirectoryUri = outputDirectoryUri,
            relativeDirectory = subfolder,
            createMissing = false
        )
        if (directory == null) {
            emptySet()
        } else {
            directory.collectRelativePaths(subfolder)
        }
    }

    override suspend fun writeArtifactToFinalOutput(
        artifactPath: String,
        outputDirectoryUri: String,
        relativePath: String,
        control: FinalizationControl?
    ): Result<Long> = captureResult {
        val finalName = relativePath.substringAfterLast('/')
        val relativeDirectory = relativePath.substringBeforeLast('/', "")
        val directory = resolveDirectory(
            outputDirectoryUri = outputDirectoryUri,
            relativeDirectory = relativeDirectory,
            createMissing = true
        ) ?: error("Output directory is not accessible")
        check(directory.findFile(finalName) == null) {
            "Final output path is already occupied: $relativePath"
        }
        val target = directory.createFile(inferMimeType(finalName), finalName)
            ?: error("Final output file could not be created")
        val bytesCopied = runCatching {
            control.failIfStopped()
            FileInputStream(File(artifactPath)).use { input ->
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) {
                            break
                        }
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead.toLong()
                        control.failIfStopped()
                        control?.pulse()?.getOrElse { throwable ->
                            throw throwable
                        }
                    }
                    copiedBytes
                } ?: error("Final output stream could not be opened")
            }
        }.getOrElse { throwable ->
            runCatching { target.delete() }
            throw throwable
        }
        bytesCopied
    }

    override suspend fun deleteFinalOutput(
        outputDirectoryUri: String,
        relativePath: String
    ): Result<Unit> = captureResult {
        val document = resolveFile(outputDirectoryUri, relativePath)
        if (document == null || !document.exists()) {
            Unit
        } else {
            check(document.delete()) { "Final output could not be deleted: $relativePath" }
        }
    }

    override fun isSupportedArchive(localArtifactPath: String): Boolean {
        val extension = localArtifactPath.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_ARCHIVE_EXTENSIONS
    }

    private fun resolveFile(outputDirectoryUri: String, relativePath: String): DocumentFile? {
        val fileName = relativePath.substringAfterLast('/')
        val relativeDirectory = relativePath.substringBeforeLast('/', "")
        val directory = resolveDirectory(
            outputDirectoryUri = outputDirectoryUri,
            relativeDirectory = relativeDirectory,
            createMissing = false
        )
        return directory?.findFile(fileName)
    }

    private fun resolveDirectory(
        outputDirectoryUri: String,
        relativeDirectory: String,
        createMissing: Boolean
    ): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(outputDirectoryUri)) ?: return null
        val parts = relativeDirectory.split('/').filter(String::isNotBlank)
        var current = root
        for (part in parts) {
            val existing = current.findFile(part)?.takeIf { file -> file.isDirectory }
            current = when {
                existing != null -> existing
                createMissing -> current.createDirectory(part) ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun inferMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            "srt" -> "application/x-subrip"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            else -> "application/octet-stream"
        }
    }

    private fun DocumentFile.collectRelativePaths(relativeDirectory: String): Set<String> =
        listFiles().flatMapTo(linkedSetOf()) { document ->
            when {
                document.isFile && document.name != null -> {
                    setOf(
                        buildRelativePath(
                            relativeDirectory = relativeDirectory,
                            leafName = checkNotNull(document.name)
                        )
                    )
                }

                document.isDirectory && document.name != null -> {
                    document.collectRelativePaths(
                        buildRelativePath(
                            relativeDirectory = relativeDirectory,
                            leafName = checkNotNull(document.name)
                        )
                    )
                }

                else -> emptySet()
            }
        }

    private fun buildRelativePath(relativeDirectory: String, leafName: String): String =
        listOf(relativeDirectory, leafName)
            .filter(String::isNotBlank)
            .joinToString("/")

    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
        val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z")
    }
}

@Suppress("TooGenericExceptionCaught")
private inline fun <T> captureResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellationException: CancellationException) {
    throw cancellationException
} catch (throwable: Throwable) {
    Result.failure(throwable)
}

internal fun FinalizationControl?.failIfStopped() {
    when (this?.currentSignal()) {
        ControlSignal.PAUSE -> throw FinalizationInterruptedException(ControlSignal.PAUSE)
        ControlSignal.CANCEL -> throw FinalizationInterruptedException(ControlSignal.CANCEL)
        ControlSignal.NONE,
        null -> Unit
    }
}
