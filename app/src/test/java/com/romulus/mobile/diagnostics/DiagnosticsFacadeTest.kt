package com.romulus.mobile.diagnostics

import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsFacadeTest {
    @Test
    fun scaffoldDiagnosticsEmitFailsExplicitlyUntilWired() = runTest {
        val facade = DiagnosticsFacade()

        val result = facade.emit(
            DiagnosticEvent(
                timestamp = Instant.parse("2026-03-10T00:00:00Z"),
                sessionId = "session",
                domain = DiagnosticDomain.APP_SHELL,
                event = "startup",
                outcome = "failure",
                taskId = null,
                snapshotId = null,
                context = mapOf("message" to "unwired")
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "DiagnosticsFacade is not wired yet",
            result.exceptionOrNull()?.message
        )
    }
}
