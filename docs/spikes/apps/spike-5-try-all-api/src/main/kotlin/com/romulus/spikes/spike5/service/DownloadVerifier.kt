package com.romulus.spikes.spike5.service

import com.romulus.spikes.spike5.model.DownloadManifestEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

class DownloadVerifier(
    private val httpClient: OkHttpClient,
) {
    suspend fun downloadSingle(downloadUrl: String, responseFilename: String, outputDirectory: Path): DownloadManifestEntry {
        return withContext(Dispatchers.IO) {
            outputDirectory.createDirectories()
            val fileName = sanitizeFileName(responseFilename)
            val outputPath = outputDirectory.resolve(fileName)
            val request = Request.Builder().url(downloadUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) { "Download failed with HTTP ${response.code}" }
                response.body?.byteStream()?.use { input ->
                    outputPath.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Missing response body for $downloadUrl")
            }
            DownloadManifestEntry(
                relativePath = outputPath.fileName.toString(),
                sizeBytes = Files.size(outputPath),
                sha256 = sha256(outputPath),
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "selected-file.bin" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
