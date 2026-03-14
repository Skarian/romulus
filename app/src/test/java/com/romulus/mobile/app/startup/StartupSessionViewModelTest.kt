package com.romulus.mobile.app.startup

import android.net.Uri
import com.romulus.mobile.app.platform.GrantRestoreReport
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.app.platform.UriGrantRegistry
import com.romulus.mobile.app.shell.ShellRoute
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.SourceFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupSessionViewModelTest {
    @Test
    fun startOnceBootstrapsOnlyOnceForOneSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val setupStateStore = CountingSetupStateStore(isCompleted = false)
            val viewModel = StartupSessionViewModel(
                startupBootstrapper = startupBootstrapper(setupStateStore)
            )
            val request = StartupBootstrapRequest(
                coldLaunchToken = ColdLaunchToken.create(),
                launchIntent = null
            )

            viewModel.startOnce(request)
            viewModel.startOnce(request)
            advanceUntilIdle()

            assertEquals(1, setupStateStore.readCount)
            assertEquals(StartupRouteDecision.Setup, viewModel.state.value.routeDecision)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun completeSetupTransitionsSetupSessionIntoShell() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = StartupSessionViewModel(
                startupBootstrapper = startupBootstrapper(
                    CountingSetupStateStore(isCompleted = false)
                )
            )

            viewModel.startOnce(
                StartupBootstrapRequest(
                    coldLaunchToken = ColdLaunchToken.create(),
                    launchIntent = null
                )
            )
            advanceUntilIdle()

            viewModel.completeSetup(ShellRoute.Home)

            assertEquals(
                StartupRouteDecision.Shell(initialRoute = ShellRoute.Home),
                viewModel.state.value.routeDecision
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun completeSetupLeavesShellSessionsUnchanged() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = StartupSessionViewModel(
                startupBootstrapper = startupBootstrapper(
                    CountingSetupStateStore(isCompleted = true)
                )
            )

            viewModel.startOnce(
                StartupBootstrapRequest(
                    coldLaunchToken = ColdLaunchToken.create(),
                    launchIntent = null
                )
            )
            advanceUntilIdle()

            viewModel.completeSetup(ShellRoute.Downloads)

            assertEquals(
                StartupRouteDecision.Shell(initialRoute = ShellRoute.Home),
                viewModel.state.value.routeDecision
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun startupBootstrapper(setupStateStore: SetupStateStore): StartupBootstrapper =
        StartupBootstrapper(
            setupStateStore = setupStateStore,
            uriGrantRegistry = SessionTestUriGrantRegistry(),
            appReadinessCoordinator = AppReadinessCoordinator(
                sourceFacade = SourceFacade(),
                downloadsFacade = DownloadsFacade(),
                realDebridFacade = RealDebridFacade(),
                dispatcher = StandardTestDispatcher()
            ),
            sourceFacade = SourceFacade(),
            downloadsFacade = DownloadsFacade(),
            diagnosticsFacade = DiagnosticsFacade()
        )
}

private class CountingSetupStateStore(
    private val isCompleted: Boolean
) : SetupStateStore {
    var readCount: Int = 0
        private set

    override suspend fun read(): SetupCompletionRecord {
        readCount += 1
        return SetupCompletionRecord(
            isCompleted = isCompleted,
            completedAt = null
        )
    }

    override suspend fun markCompleted(at: Instant): Result<Unit> = Result.success(Unit)
}

private class SessionTestUriGrantRegistry : UriGrantRegistry {
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
