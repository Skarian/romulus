@file:Suppress("ClassSignature")

package com.romulus.mobile.app.startup

import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.downloads.config.DownloadSettingsReadiness
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.realdebrid.auth.TokenReadiness
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.snapshot.SourceReadiness
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class BrokenSetting {
    API_TOKEN,
    SOURCE,
    DOWNLOAD_DIRECTORY
}

data class ShellReadiness(
    val brokenSettings: Set<BrokenSetting>,
    val hasUsableSnapshot: Boolean
)

class AppReadinessCoordinator(
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val realDebridFacade: RealDebridFacade,
    private val dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val shellReadiness = combine(
        sourceFacade.observeReadiness(),
        downloadsFacade.observeSettingsReadiness(),
        realDebridFacade.observeTokenReadiness()
    ) { sourceReadiness, downloadsReadiness, tokenReadiness ->
        composeReadiness(
            sourceReadiness = sourceReadiness,
            downloadsReadiness = downloadsReadiness,
            tokenReadiness = tokenReadiness
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = composeReadiness(
            sourceReadiness = sourceFacade.observeReadiness().value,
            downloadsReadiness = downloadsFacade.observeSettingsReadiness().value,
            tokenReadiness = realDebridFacade.observeTokenReadiness().value
        )
    )

    suspend fun readStartupReadiness(): ShellReadiness = composeReadiness(
        sourceReadiness = sourceFacade.readStartupReadiness(),
        downloadsReadiness = downloadsFacade.readSettingsReadiness(),
        tokenReadiness = realDebridFacade.readTokenReadiness()
    )

    fun observeShellReadiness(): StateFlow<ShellReadiness> = shellReadiness

    private fun composeReadiness(
        sourceReadiness: SourceReadiness,
        downloadsReadiness: DownloadSettingsReadiness,
        tokenReadiness: TokenReadiness
    ): ShellReadiness {
        val brokenSettings = buildSet {
            if (!tokenReadiness.isUsable) {
                add(BrokenSetting.API_TOKEN)
            }
            if (!sourceReadiness.isUsable) {
                add(BrokenSetting.SOURCE)
            }
            if (!downloadsReadiness.isUsable) {
                add(BrokenSetting.DOWNLOAD_DIRECTORY)
            }
        }

        return ShellReadiness(
            brokenSettings = brokenSettings,
            hasUsableSnapshot = sourceReadiness.hasUsableSnapshot
        )
    }
}
