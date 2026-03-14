package com.romulus.mobile.diagnostics

import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.events.DiagnosticsManifest
import com.romulus.mobile.diagnostics.events.DiagnosticsSummary
import com.romulus.mobile.diagnostics.store.DiagnosticsFilesystem
import com.romulus.mobile.diagnostics.store.DiagnosticsStore
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsStoreTest {
    @Test
    fun appendsEventsAndProducesSnapshot() = runTest {
        val tempRoot = Files.createTempDirectory("diagnostics-store-test").toFile()
        val store = DiagnosticsStore(
            filesystem = object : DiagnosticsFilesystem {
                override val diagnosticsRoot: File = tempRoot.resolve("diagnostics")
                override val backupRootParent: File = tempRoot.resolve("backups")
            },
            json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
        )
        val event = DiagnosticEvent(
            timestamp = Instant.EPOCH,
            sessionId = "session",
            domain = DiagnosticDomain.DOWNLOADS,
            event = "queued",
            outcome = "succeeded",
            taskId = "task-1",
            snapshotId = "snapshot-1",
            context = mapOf("summaryTotal" to "1")
        )

        store.writeManifest(
            DiagnosticsManifest(
                contractVersion = 1,
                appVersion = "1.0",
                buildNumber = "1",
                androidVersion = "16",
                deviceModel = "device",
                sessionSeed = "session",
                redactionPolicyVersion = 1,
                exportedAt = null
            )
        ).getOrThrow()
        store.appendTimeline(event).getOrThrow()
        store.writeSummary(
            DiagnosticsSummary(
                eventCountsByDomain = mapOf(DiagnosticDomain.DOWNLOADS to 1),
                failureCountsByDomain = emptyMap(),
                latestQueueSummary = null,
                latestSourceRefreshOutcome = null,
                latestFailureByDomain = emptyMap()
            )
        ).getOrThrow()

        val snapshot = store.snapshot().getOrThrow()
        val events = store.readTimelineEvents().getOrThrow()

        assertTrue(snapshot.manifest.exists())
        assertTrue(snapshot.timeline.exists())
        assertTrue(snapshot.summary.exists())
        assertEquals(listOf(event), events)
        assertTrue(tempRoot.deleteRecursively())
    }
}
