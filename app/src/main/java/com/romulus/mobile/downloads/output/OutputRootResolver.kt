@file:Suppress("ClassSignature", "ReturnCount")

package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.config.DownloadSettingsService
import java.time.Clock
import java.time.Instant

internal data class OutputRootBinding(
    val outputDirectoryUri: String,
    val boundAt: Instant
)

internal class OutputRootResolver(
    private val settingsService: DownloadSettingsService,
    private val clock: Clock
) {
    suspend fun resolveFreshBinding(): Result<OutputRootBinding> {
        val outputDirectoryUri = settingsService.readState().outputDirectoryUri
            ?: return Result.failure(
                IllegalStateException("Download directory is not configured")
            )
        val readiness = settingsService.revalidateReadiness()
        if (!readiness.isUsable) {
            return Result.failure(
                IllegalStateException(
                    readiness.brokenReason ?: "Download directory is not usable"
                )
            )
        }
        return Result.success(
            OutputRootBinding(
                outputDirectoryUri = outputDirectoryUri,
                boundAt = clock.instant()
            )
        )
    }

    suspend fun revalidateCurrentOutputDirectory() = settingsService.revalidateReadiness()
}
