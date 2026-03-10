package com.romulus.mobile.app.startup

import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.realdebrid.auth.TokenSaveResult
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.ingest.SourceValidationIssue
import java.time.Instant

data class SetupDraft(
    val apiKey: String,
    val source: AcceptSourceCommand,
    val downloadSettings: DownloadSettingsDraft
)

sealed interface SetupSubmissionResult {
    data object Completed : SetupSubmissionResult

    data class Rejected(
        val message: String,
        val sourceIssues: List<SourceValidationIssue> = emptyList()
    ) : SetupSubmissionResult
}

class SetupSubmissionCoordinator(
    private val realDebridFacade: RealDebridFacade,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val setupStateStore: SetupStateStore
) {
    suspend fun submit(draft: SetupDraft): SetupSubmissionResult {
        val failure = validateApiKey(draft.apiKey)
            ?: acceptSource(draft.source)
            ?: saveDownloadSettings(draft.downloadSettings)

        return failure ?: markCompleted()
    }

    private suspend fun validateApiKey(apiKey: String): SetupSubmissionResult.Rejected? =
        when (val tokenResult = realDebridFacade.saveValidatedToken(apiKey)) {
            TokenSaveResult.Saved -> null
            is TokenSaveResult.Rejected -> SetupSubmissionResult.Rejected(tokenResult.message)
            is TokenSaveResult.Failed -> SetupSubmissionResult.Rejected(tokenResult.message)
        }

    @Suppress(
        "FunctionExpressionBody",
        "FunctionSignature",
        "MaximumLineLength",
        "ParameterListWrapping"
    )
    private suspend fun acceptSource(command: AcceptSourceCommand): SetupSubmissionResult.Rejected? {
        return when (val sourceResult = sourceFacade.accept(command)) {
            is AcceptSourceResult.Accepted -> null
            is AcceptSourceResult.Rejected -> sourceResult.toSetupSubmissionResult()
            is AcceptSourceResult.Failed -> SetupSubmissionResult.Rejected(sourceResult.message)
        }
    }

    @Suppress("FunctionExpressionBody")
    private suspend fun saveDownloadSettings(
        draft: DownloadSettingsDraft
    ): SetupSubmissionResult.Rejected? {
        return downloadsFacade.updateSettings(draft).fold(
            onSuccess = { null },
            onFailure = { throwable ->
                SetupSubmissionResult.Rejected(
                    throwable.message ?: "Download settings were rejected"
                )
            }
        )
    }

    @Suppress("FunctionExpressionBody")
    private suspend fun markCompleted(): SetupSubmissionResult =
        setupStateStore.markCompleted(Instant.now()).fold(
            onSuccess = { SetupSubmissionResult.Completed },
            onFailure = { throwable ->
                SetupSubmissionResult.Rejected(
                    throwable.message ?: "Setup completion could not be saved"
                )
            }
        )
}

internal fun AcceptSourceResult.Rejected.toSetupSubmissionResult(): SetupSubmissionResult.Rejected =
    SetupSubmissionResult.Rejected(
        message = "Source was rejected",
        sourceIssues = issues
    )
