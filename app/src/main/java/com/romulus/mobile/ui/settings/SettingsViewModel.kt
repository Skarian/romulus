package com.romulus.mobile.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romulus.mobile.app.startup.BrokenSetting
import com.romulus.mobile.app.startup.ShellReadiness
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.export.DiagnosticsClearResult
import com.romulus.mobile.diagnostics.export.DiagnosticsExportResult
import com.romulus.mobile.diagnostics.settings.DiagnosticsSettings
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.realdebrid.auth.TokenSaveResult
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.ingest.SourceValidationIssue
import com.romulus.mobile.source.snapshot.AcceptedSourceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsFieldLockState(
    val tokenEditable: Boolean,
    val sourceEditable: Boolean,
    val downloadDirectoryEditable: Boolean,
    val concurrencyEditable: Boolean
)

data class SettingsUiState(
    val maskedToken: String,
    val sourceSummary: AcceptedSourceSummary?,
    val downloadSettings: DownloadSettingsState,
    val diagnosticsSettings: DiagnosticsSettings,
    val lockState: SettingsFieldLockState,
    val feedbackMessage: String?
)

@Suppress("LongParameterList")
class SettingsViewModel(
    private val shellReadiness: StateFlow<ShellReadiness>,
    private val realDebridFacade: RealDebridFacade,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val diagnosticsFacade: DiagnosticsFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private data class OwnerStateSnapshot(
        val maskedToken: String,
        val sourceSummary: AcceptedSourceSummary?,
        val downloadSettings: DownloadSettingsState,
        val diagnosticsSettings: DiagnosticsSettings,
        val activeDownloads: Boolean
    )

    private val feedbackMessage = MutableStateFlow<String?>(null)

    init {
        savedStateHandle.keys()
    }

    private val ownerState = combine(
        realDebridFacade.observeMaskedToken(),
        sourceFacade.observeAcceptedSourceSummary(),
        downloadsFacade.observeSettings(),
        diagnosticsFacade.observeSettings(),
        downloadsFacade.observeActiveDownloadsFlag()
    ) { maskedToken, sourceSummary, downloadSettings, diagnosticsSettings, activeDownloads ->
        OwnerStateSnapshot(
            maskedToken = maskedToken.maskedValue,
            sourceSummary = sourceSummary,
            downloadSettings = downloadSettings,
            diagnosticsSettings = diagnosticsSettings,
            activeDownloads = activeDownloads
        )
    }

    val state: StateFlow<SettingsUiState> = combine(
        shellReadiness,
        ownerState,
        feedbackMessage
    ) { readiness, owner, feedback ->
        SettingsUiState(
            maskedToken = owner.maskedToken,
            sourceSummary = owner.sourceSummary,
            downloadSettings = owner.downloadSettings,
            diagnosticsSettings = owner.diagnosticsSettings,
            lockState = determineSettingsFieldLockState(
                brokenSettings = readiness.brokenSettings,
                activeDownloads = owner.activeDownloads
            ),
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(
            maskedToken = realDebridFacade.observeMaskedToken().value.maskedValue,
            sourceSummary = sourceFacade.observeAcceptedSourceSummary().value,
            downloadSettings = downloadsFacade.observeSettings().value,
            diagnosticsSettings = diagnosticsFacade.observeSettings().value,
            lockState = determineSettingsFieldLockState(
                brokenSettings = shellReadiness.value.brokenSettings,
                activeDownloads = downloadsFacade.observeActiveDownloadsFlag().value
            ),
            feedbackMessage = feedbackMessage.value
        )
    )

    fun saveApiKey(candidate: String) {
        viewModelScope.launch {
            feedbackMessage.value = when (
                val result = realDebridFacade.saveValidatedToken(candidate)
            ) {
                TokenSaveResult.Saved -> null
                is TokenSaveResult.Rejected -> result.message
                is TokenSaveResult.Failed -> result.message
            }
        }
    }

    fun saveSource(command: AcceptSourceCommand) {
        viewModelScope.launch {
            feedbackMessage.value = when (val result = sourceFacade.accept(command)) {
                is AcceptSourceResult.Accepted -> null
                is AcceptSourceResult.Rejected -> result.toSettingsFeedbackMessage()
                is AcceptSourceResult.Failed -> result.message
            }
        }
    }

    fun saveDownloadSettings(draft: DownloadSettingsDraft) {
        viewModelScope.launch {
            feedbackMessage.value = downloadsFacade.updateSettings(draft).exceptionOrNull()?.message
        }
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            feedbackMessage.value = diagnosticsFacade.setEnabled(enabled).exceptionOrNull()?.message
        }
    }

    fun clearDiagnostics() {
        viewModelScope.launch {
            feedbackMessage.value = when (val result = diagnosticsFacade.clear()) {
                DiagnosticsClearResult.Cleared -> null
                is DiagnosticsClearResult.Failed -> result.message
            }
        }
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            feedbackMessage.value = when (val result = diagnosticsFacade.export()) {
                is DiagnosticsExportResult.Exported -> null
                is DiagnosticsExportResult.ExportedWithRetentionFailure -> result.message
                is DiagnosticsExportResult.Failed -> result.message
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun determineSettingsFieldLockState(
    brokenSettings: Set<BrokenSetting>,
    activeDownloads: Boolean
): SettingsFieldLockState = when {
    !activeDownloads -> SettingsFieldLockState(
        tokenEditable = true,
        sourceEditable = true,
        downloadDirectoryEditable = true,
        concurrencyEditable = true
    )

    brokenSettings.isEmpty() -> SettingsFieldLockState(
        tokenEditable = false,
        sourceEditable = false,
        downloadDirectoryEditable = false,
        concurrencyEditable = false
    )

    else -> SettingsFieldLockState(
        tokenEditable = BrokenSetting.API_TOKEN in brokenSettings,
        sourceEditable = BrokenSetting.SOURCE in brokenSettings,
        downloadDirectoryEditable = BrokenSetting.DOWNLOAD_DIRECTORY in brokenSettings,
        concurrencyEditable = false
    )
}

internal fun AcceptSourceResult.Rejected.toSettingsFeedbackMessage(): String = buildString {
    append("Source was rejected.")
    issues.forEach { issue ->
        append('\n')
        append(issue.toSettingsFeedbackMessage())
    }
}

internal fun SourceValidationIssue.toSettingsFeedbackMessage(): String = when (this) {
    is SourceValidationIssue.InvalidVersion -> "Unsupported source version: $found."
    is SourceValidationIssue.InvalidSubfolder -> "Invalid subfolder: $subfolder."
    is SourceValidationIssue.InvalidPath -> "Invalid path: $path."
    is SourceValidationIssue.InvalidIgnoreRule -> "Invalid ignore rule: $pattern."
    is SourceValidationIssue.InvalidRenameRule -> message
    is SourceValidationIssue.InvalidRecursiveUnarchive -> message
}
