package com.romulus.mobile.ui.setup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romulus.mobile.app.startup.SetupDraft
import com.romulus.mobile.app.startup.SetupSubmissionCoordinator
import com.romulus.mobile.app.startup.SetupSubmissionResult
import com.romulus.mobile.downloads.config.DownloadLimits
import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.source.ingest.SourceValidationIssue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val apiKeyDraft: String = "",
    val sourceMode: SourceMode = SourceMode.URL,
    val sourceValueDraft: String = "",
    val sourceDocumentUri: Uri? = null,
    val outputDirectoryUri: Uri? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SetupEffect {
    data object Completed : SetupEffect
}

class SetupViewModel(
    private val setupSubmissionCoordinator: SetupSubmissionCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<SetupEffect>()
    val effects: Flow<SetupEffect> = mutableEffects.asSharedFlow()

    init {
        savedStateHandle.keys()
    }

    fun updateApiKey(value: String) {
        mutableState.value = mutableState.value.copy(
            apiKeyDraft = value,
            errorMessage = null
        )
    }

    fun updateSourceMode(mode: SourceMode) {
        mutableState.value = mutableState.value.copy(
            sourceMode = mode,
            errorMessage = null
        )
    }

    fun updateSourceValue(value: String) {
        mutableState.value = mutableState.value.copy(
            sourceValueDraft = value,
            errorMessage = null
        )
    }

    fun acceptSourceGrant(uri: Uri) {
        mutableState.value = mutableState.value.copy(
            sourceDocumentUri = uri,
            errorMessage = null
        )
    }

    fun acceptOutputGrant(uri: Uri) {
        mutableState.value = mutableState.value.copy(
            outputDirectoryUri = uri,
            errorMessage = null
        )
    }

    fun showError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message)
    }

    fun submit() {
        val draft = state.value
        val validationError = validate(draft)
        if (validationError != null) {
            mutableState.value = draft.copy(errorMessage = validationError)
            return
        }

        viewModelScope.launch {
            mutableState.value = draft.copy(isSaving = true, errorMessage = null)
            when (
                val result = setupSubmissionCoordinator.submit(
                    SetupDraft(
                        apiKey = draft.apiKeyDraft.trim(),
                        source = AcceptSourceCommand(
                            mode = draft.sourceMode,
                            rawValue = if (draft.sourceMode == SourceMode.FILE) {
                                draft.sourceDocumentUri.toString()
                            } else {
                                draft.sourceValueDraft.trim()
                            },
                            persistedUri = draft.sourceDocumentUri?.toString()
                        ),
                        downloadSettings = DownloadSettingsDraft(
                            outputDirectoryUri = draft.outputDirectoryUri.toString(),
                            maxConcurrency = DEFAULT_MAX_CONCURRENCY
                        )
                    )
                )
            ) {
                SetupSubmissionResult.Completed -> {
                    mutableState.value = draft.copy(isSaving = false, errorMessage = null)
                    mutableEffects.emit(SetupEffect.Completed)
                }

                is SetupSubmissionResult.Rejected -> {
                    mutableState.value = draft.copy(
                        isSaving = false,
                        errorMessage = formatRejection(result)
                    )
                }
            }
        }
    }

    private fun validate(state: SetupUiState): String? = when {
        state.apiKeyDraft.isBlank() -> "API key is required."
        state.sourceMode == SourceMode.URL && state.sourceValueDraft.isBlank() ->
            "Source URL is required."

        state.sourceMode == SourceMode.FILE && state.sourceDocumentUri == null ->
            "Source document is required."

        state.outputDirectoryUri == null -> "Output directory is required."
        else -> null
    }

    private fun formatRejection(result: SetupSubmissionResult.Rejected): String {
        val firstIssue = result.sourceIssues.firstOrNull() ?: return result.message
        val issueMessage = when (firstIssue) {
            is SourceValidationIssue.InvalidVersion ->
                "Unsupported source version ${firstIssue.found}."

            is SourceValidationIssue.InvalidSubfolder ->
                "Invalid source subfolder ${firstIssue.subfolder}."

            is SourceValidationIssue.InvalidPath ->
                "Invalid source path ${firstIssue.path}."

            is SourceValidationIssue.InvalidIgnoreRule ->
                "Invalid ignore rule ${firstIssue.pattern}."

            is SourceValidationIssue.InvalidRenameRule -> firstIssue.message
            is SourceValidationIssue.InvalidUnarchiveRule -> firstIssue.message
        }
        return "${result.message} $issueMessage"
    }

    private companion object {
        const val DEFAULT_MAX_CONCURRENCY = DownloadLimits.DEFAULT_CONCURRENCY
    }
}
