package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.model.DownloadManifestEntry
import com.romulus.spikes.spike1.model.UnrestrictCallRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.outputStream

class DownloadVerifier(
    private val httpClient: OkHttpClient,
) {
    suspend fun downloadAll(records: List<UnrestrictCallRecord>, outputDirectory: Path): List<DownloadManifestEntry> {
        return withContext(Dispatchers.IO) {
            outputDirectory.createDirectories()
            records.mapIndexed { index, record ->
                val fileName = sanitizeFileName(record.responseFilename, index)
                val outputPath = outputDirectory.resolve(fileName)
                val request = Request.Builder().url(record.downloadUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    require(response.isSuccessful) { "Download failed for ${record.downloadUrl} with HTTP ${response.code}" }
                    response.body?.byteStream()?.use { input ->
                        outputPath.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Missing response body for ${record.downloadUrl}")
                }
                DownloadManifestEntry(
                    relativePath = outputPath.fileName.toString(),
                    sizeBytes = Files.size(outputPath),
                    sha256 = sha256(outputPath),
                )
            }
        }
    }

    private fun sanitizeFileName(name: String, index: Int): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "download-$index.bin" }
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
