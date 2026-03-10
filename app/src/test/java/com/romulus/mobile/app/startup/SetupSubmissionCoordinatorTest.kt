package com.romulus.mobile.app.startup

import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.ingest.SourceValidationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupSubmissionCoordinatorTest {
    @Test
    fun sourceRejectionPreservesValidationIssues() {
        val result = AcceptSourceResult.Rejected(
            issues = listOf(SourceValidationIssue.InvalidPath("/broken"))
        ).toSetupSubmissionResult()

        assertEquals("Source was rejected", result.message)
        assertEquals(1, result.sourceIssues.size)
        assertTrue(result.sourceIssues.single() is SourceValidationIssue.InvalidPath)
    }
}
