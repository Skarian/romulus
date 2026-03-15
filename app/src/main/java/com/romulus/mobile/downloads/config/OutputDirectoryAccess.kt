package com.romulus.mobile.downloads.config

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

internal interface OutputDirectoryAccess {
    suspend fun check(outputDirectoryUri: String?): DownloadSettingsReadiness
}

internal class AndroidOutputDirectoryAccess(private val context: Context) : OutputDirectoryAccess {
    override suspend fun check(outputDirectoryUri: String?): DownloadSettingsReadiness {
        val normalizedUri = outputDirectoryUri?.takeIf(String::isNotBlank)
        val root = normalizedUri?.let { uri ->
            DocumentFile.fromTreeUri(context, Uri.parse(uri))
        }
        val isUsable = when {
            normalizedUri == null -> false
            root == null -> false
            !root.exists() -> false
            !root.isDirectory -> false
            !root.canRead() -> false
            !root.canWrite() -> false
            else -> true
        }

        return if (isUsable) {
            DownloadSettingsReadiness(
                isUsable = true,
                brokenReason = null
            )
        } else if (normalizedUri == null) {
            DownloadSettingsReadiness(
                isUsable = false,
                brokenReason = "Download directory is not configured"
            )
        } else {
            DownloadSettingsReadiness(
                isUsable = false,
                brokenReason = "Download directory is no longer accessible"
            )
        }
    }
}
