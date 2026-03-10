package com.romulus.mobile.app.startup

import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.SourceFacade
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppReadinessCoordinatorTest {
    @Test
    fun scaffoldReadinessStartsBrokenUntilOwnersAreWired() = runTest {
        val coordinator = AppReadinessCoordinator(
            sourceFacade = SourceFacade(),
            downloadsFacade = DownloadsFacade(),
            realDebridFacade = RealDebridFacade(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val readiness = coordinator.readStartupReadiness()

        assertEquals(
            setOf(
                BrokenSetting.API_TOKEN,
                BrokenSetting.SOURCE,
                BrokenSetting.DOWNLOAD_DIRECTORY
            ),
            readiness.brokenSettings
        )
        assertFalse(readiness.hasUsableSnapshot)
    }
}
