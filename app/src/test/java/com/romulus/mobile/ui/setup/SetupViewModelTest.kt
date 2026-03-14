package com.romulus.mobile.ui.setup

import androidx.lifecycle.SavedStateHandle
import com.romulus.mobile.app.startup.SetupCompletionRecord
import com.romulus.mobile.app.startup.SetupSubmissionResult
import com.romulus.mobile.app.startup.SetupStateStore
import com.romulus.mobile.app.startup.SetupSubmissionCoordinator
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.ingest.SourceValidationIssue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @Test
    fun submitRejectsMissingRequiredFieldsBeforeCallingOwners() = runTest {
        val viewModel = SetupViewModel(
            setupSubmissionCoordinator = SetupSubmissionCoordinator(
                realDebridFacade = RealDebridFacade(),
                sourceFacade = SourceFacade(),
                downloadsFacade = DownloadsFacade(),
                setupStateStore = FakeSetupStateStore()
            ),
            savedStateHandle = SavedStateHandle()
        )

        viewModel.submit()

        assertEquals("API key is required.", viewModel.state.value.errorMessage)
    }

    @Test
    fun submitRequiresOutputDirectoryBeforeLaunchingOwnerWork() = runTest {
        val viewModel = SetupViewModel(
            setupSubmissionCoordinator = SetupSubmissionCoordinator(
                realDebridFacade = RealDebridFacade(),
                sourceFacade = SourceFacade(),
                downloadsFacade = DownloadsFacade(),
                setupStateStore = FakeSetupStateStore()
            ),
            savedStateHandle = SavedStateHandle()
        )

        viewModel.updateApiKey("token")
        viewModel.updateSourceValue("https://example.com/source.json")
        viewModel.submit()

        assertEquals("Output directory is required.", viewModel.state.value.errorMessage)
    }

    @Test
    fun scopeValidationFeedbackUsesSourceMessage() {
        val message = SetupSubmissionResult.Rejected(
            message = "Source was rejected",
            sourceIssues = listOf(
                SourceValidationIssue.InvalidScope(
                    "Exact .zip scope cannot set includeNestedFiles to true."
                )
            )
        ).toSetupErrorMessage()

        assertEquals(
            "Source was rejected Exact .zip scope cannot set includeNestedFiles to true.",
            message
        )
    }
}

private class FakeSetupStateStore : SetupStateStore {
    override suspend fun read(): SetupCompletionRecord = SetupCompletionRecord(
        isCompleted = false,
        completedAt = null
    )

    override suspend fun markCompleted(at: java.time.Instant): Result<Unit> = Result.success(Unit)
}
