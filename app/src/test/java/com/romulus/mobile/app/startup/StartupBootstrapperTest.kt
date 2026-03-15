package com.romulus.mobile.app.startup

import android.net.Uri
import com.romulus.mobile.app.platform.GrantRestoreReport
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.app.platform.UriGrantRegistry
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.SourceFacade
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupBootstrapperTest {
    @Test
    fun incompleteSetupRoutesToSetup() = runTest {
        val bootstrapper = StartupBootstrapper(
            setupStateStore = FakeSetupStateStore(isCompleted = false),
            uriGrantRegistry = FakeUriGrantRegistry(),
            appReadinessCoordinator = AppReadinessCoordinator(
                sourceFacade = SourceFacade(),
                downloadsFacade = DownloadsFacade(),
                realDebridFacade = RealDebridFacade(),
                dispatcher = StandardTestDispatcher(testScheduler)
            ),
            sourceFacade = SourceFacade(),
            downloadsFacade = DownloadsFacade(),
            diagnosticsFacade = DiagnosticsFacade()
        )

        val route = bootstrapper.bootstrap(
            StartupBootstrapRequest(
                coldLaunchToken = ColdLaunchToken.create(),
                launchIntent = null
            )
        )

        assertEquals(StartupRouteDecision.Setup, route)
    }
}

private class FakeSetupStateStore(
    private val isCompleted: Boolean
) : SetupStateStore {
    override suspend fun read(): SetupCompletionRecord = SetupCompletionRecord(
        isCompleted = isCompleted,
        completedAt = null
    )

    override suspend fun markCompleted(at: Instant): Result<Unit> = Result.success(Unit)
}

private class FakeUriGrantRegistry : UriGrantRegistry {
    override suspend fun captureSourceGrant(uri: Uri): Result<PersistedUriGrant> =
        Result.failure(UnsupportedOperationException("unused"))

    override suspend fun captureOutputGrant(uri: Uri): Result<PersistedUriGrant> =
        Result.failure(UnsupportedOperationException("unused"))

    override suspend fun restorePersistedGrants(): GrantRestoreReport = GrantRestoreReport(
        restored = emptyList(),
        failed = emptyList()
    )

    override suspend fun revoke(uri: Uri): Result<Unit> = Result.success(Unit)
}
