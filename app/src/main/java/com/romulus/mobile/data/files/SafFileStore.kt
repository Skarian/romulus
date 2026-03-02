package com.romulus.mobile.data.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.romulus.mobile.core.files.FilenameCollisionResolver
import java.io.InputStream
import java.io.OutputStream

class SafFileStore(
    private val appContext: Context,
    private val collisionResolver: FilenameCollisionResolver
) {

    fun createOutputFile(
        treeUri: String,
        subfolder: String,
        preferredFileName: String
    ): Uri? {
        val root = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri)) ?: return null
        val directory = ensureSubfolder(root, subfolder) ?: return null

        val existingNames = directory.listFiles()
            .mapNotNull { it.name }
            .toSet()
        val resolvedName = collisionResolver.resolve(existingNames, preferredFileName)
        val mimeType = inferMimeType(resolvedName)
        val file = directory.createFile(mimeType, resolvedName)
        return file?.uri
    }

    fun openOutput(uri: String, append: Boolean = false): OutputStream? {
        val mode = if (append) "wa" else "w"
        return appContext.contentResolver.openOutputStream(Uri.parse(uri), mode)
    }

    fun openInput(uri: String): InputStream? {
        return appContext.contentResolver.openInputStream(Uri.parse(uri))
    }

    fun fileExists(uri: String): Boolean {
        val file = DocumentFile.fromSingleUri(appContext, Uri.parse(uri))
        return file?.exists() == true
    }

    fun deleteIfExists(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return true
        val file = DocumentFile.fromSingleUri(appContext, Uri.parse(uri)) ?: return true
        return !file.exists() || file.delete()
    }

    private fun ensureSubfolder(root: DocumentFile, subfolder: String): DocumentFile? {
        val parts = subfolder.split('/').filter { it.isNotBlank() }
        var current = root
        for (part in parts) {
            val next = current.findFile(part)?.takeIf { it.isDirectory }
                ?: current.createDirectory(part)
                ?: return null
            current = next
        }
        return current
    }

    private fun inferMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "srt" -> "application/x-subrip"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
