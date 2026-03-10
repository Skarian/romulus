package com.romulus.mobile.diagnostics

import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.store.DiagnosticsFilesystem
import com.romulus.mobile.diagnostics.store.DiagnosticsStore
import com.romulus.mobile.diagnostics.store.SummaryProjector
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsStoreScaffoldTest {
    @Test
    fun internalDiagnosticsStoreFailsExplicitlyUntilWired() = runTest {
        val store = DiagnosticsStore(filesystem = object : DiagnosticsFilesystem {})
        val projector = SummaryProjector(diagnosticsStore = store)
        val event = DiagnosticEvent(
            timestamp = Instant.EPOCH,
            sessionId = "session",
            domain = DiagnosticDomain.APP_SHELL,
            event = "boot",
            outcome = "failed",
            taskId = null,
            message = "unwired"
        )

        val snapshotResult = store.snapshot()
        val applyResult = projector.apply(event)

        assertTrue(snapshotResult.isFailure)
        assertTrue(applyResult.isFailure)
        assertEquals("DiagnosticsStore is not wired yet", snapshotResult.exceptionOrNull()?.message)
    }
}
