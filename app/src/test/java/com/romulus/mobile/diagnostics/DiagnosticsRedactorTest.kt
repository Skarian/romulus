package com.romulus.mobile.diagnostics

import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.events.DiagnosticsRedactor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsRedactorTest {
    @Test
    fun redactsSensitiveValuesAndUrls() {
        val redacted = DiagnosticsRedactor().redact(
            DiagnosticEvent(
                timestamp = Instant.EPOCH,
                sessionId = "session",
                domain = DiagnosticDomain.REAL_DEBRID,
                event = "request",
                outcome = "failed",
                taskId = null,
                snapshotId = null,
                context = mapOf(
                    "authorization" to "Bearer secret-value",
                    "apiKey" to "top-secret",
                    "downloadUrl" to "https://files.example.com/path/file.zip?token=abc",
                    "message" to "Bearer leaked-token"
                )
            )
        )

        assertEquals("[REDACTED]", redacted.context["authorization"])
        assertEquals("[REDACTED]", redacted.context["apiKey"])
        assertEquals("URL[host=files.example.com]", redacted.context["downloadUrl"])
        assertFalse(redacted.context["message"]!!.contains("leaked-token"))
    }
}
