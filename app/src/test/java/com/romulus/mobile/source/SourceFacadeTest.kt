package com.romulus.mobile.source

import com.romulus.mobile.source.browse.BrowseRequest
import com.romulus.mobile.source.browse.BrowseResult
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFacadeTest {
    @Test
    fun scaffoldSourceFacadeFailsExplicitlyUntilWired() = runTest {
        val facade = SourceFacade()

        val acceptResult = facade.accept(
            AcceptSourceCommand(
                mode = SourceMode.URL,
                rawValue = "https://example.com/source.json",
                persistedUri = null
            )
        )
        val browseResult = facade.browse(
            BrowseRequest(
                snapshotId = SnapshotId("snapshot"),
                entryId = SourceEntryId("entry")
            )
        )

        assertTrue(acceptResult is AcceptSourceResult.Failed)
        assertTrue(browseResult is BrowseResult.Failed)
        assertFalse(facade.readStartupReadiness().isUsable)
        assertEquals("SourceFacade is not wired yet", facade.readStartupReadiness().brokenReason)
        assertNull(facade.observeAcceptedSourceSummary().value)
    }
}
