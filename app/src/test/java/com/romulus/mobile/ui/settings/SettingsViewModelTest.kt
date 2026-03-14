package com.romulus.mobile.ui.settings

import com.romulus.mobile.app.startup.BrokenSetting
import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.ingest.SourceValidationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun brokenDownloadDirectoryDoesNotUnlockConcurrencyDuringActiveDownloads() {
        val lockState = determineSettingsFieldLockState(
            brokenSettings = setOf(BrokenSetting.DOWNLOAD_DIRECTORY),
            activeDownloads = true
        )

        assertFalse(lockState.tokenEditable)
        assertFalse(lockState.sourceEditable)
        assertTrue(lockState.downloadDirectoryEditable)
        assertFalse(lockState.concurrencyEditable)
    }

    @Test
    fun rejectedSourceFeedbackIncludesValidationDetails() {
        val feedback = AcceptSourceResult.Rejected(
            issues = listOf(
                SourceValidationIssue.InvalidPath("/broken"),
                SourceValidationIssue.InvalidIgnoreRule("*.tmp")
            )
        ).toSettingsFeedbackMessage()

        assertEquals(
            "Source was rejected.\nInvalid path: /broken.\nInvalid ignore rule: *.tmp.",
            feedback
        )
    }

    @Test
    fun unarchiveValidationFeedbackUsesSourceMessage() {
        val feedback = SourceValidationIssue.InvalidUnarchiveRule(
            message = "Dedicated-folder rename pattern is invalid."
        ).toSettingsFeedbackMessage()

        assertEquals(
            "Dedicated-folder rename pattern is invalid.",
            feedback
        )
    }

    @Test
    fun scopeValidationFeedbackUsesSourceMessage() {
        val feedback = SourceValidationIssue.InvalidScope(
            message = "Exact .zip scope cannot set includeNestedFiles to true."
        ).toSettingsFeedbackMessage()

        assertEquals(
            "Exact .zip scope cannot set includeNestedFiles to true.",
            feedback
        )
    }
}
