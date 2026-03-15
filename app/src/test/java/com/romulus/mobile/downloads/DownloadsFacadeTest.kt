package com.romulus.mobile.downloads

import com.romulus.mobile.downloads.queue.EnqueueResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsFacadeTest {
    @Test
    fun scaffoldDownloadsFacadeStartsIdleAndRejectsWork() = runTest {
        val facade = DownloadsFacade()

        val enqueueResult = facade.enqueue(emptyList())

        assertTrue(enqueueResult is EnqueueResult.Failed)
        assertFalse(facade.observeActiveDownloadsFlag().value)
        assertFalse(facade.readSettingsReadiness().isUsable)
        assertEquals("DownloadsFacade is not wired yet", facade.readSettingsReadiness().brokenReason)
    }
}
