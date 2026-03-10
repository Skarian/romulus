package com.romulus.mobile.app.startup

import android.app.Application
import android.net.Uri
import com.romulus.mobile.app.platform.GrantRestoreReport
import com.romulus.mobile.app.platform.NotificationDeepLinkHandler
import com.romulus.mobile.app.platform.NotificationPermissionRequester
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.app.platform.UriGrantRegistry
import com.romulus.mobile.app.shell.ShellNavigator
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.remotezip.RemoteZipFacade
import com.romulus.mobile.source.SourceFacade
import java.time.Instant
import kotlinx.coroutines.Dispatchers

data class AppGraph(
    val startupBootstrapper: StartupBootstrapper,
    val appReadinessCoordinator: AppReadinessCoordinator,
    val setupSubmissionCoordinator: SetupSubmissionCoordinator,
    val shellNavigator: ShellNavigator,
    val uriGrantRegistry: UriGrantRegistry,
    val notificationDeepLinkHandler: NotificationDeepLinkHandler,
    val notificationPermissionRequester: NotificationPermissionRequester,
    val sourceFacade: SourceFacade,
    val downloadsFacade: DownloadsFacade,
    val realDebridFacade: RealDebridFacade,
    val remoteZipFacade: RemoteZipFacade,
    val diagnosticsFacade: DiagnosticsFacade
) {
    companion object {
        @Suppress("InjectDispatcher")
        fun create(application: Application): AppGraph {
            val diagnosticsFacade = DiagnosticsFacade()
            val sourceFacade = SourceFacade()
            val downloadsFacade = DownloadsFacade()
            val realDebridFacade = RealDebridFacade()
            val remoteZipFacade = RemoteZipFacade()
            val setupStateStore: SetupStateStore = UnwiredSetupStateStore()
            val uriGrantRegistry: UriGrantRegistry =
                FailFastUriGrantRegistry(application.packageName)
            val appReadinessCoordinator = AppReadinessCoordinator(
                sourceFacade = sourceFacade,
                downloadsFacade = downloadsFacade,
                realDebridFacade = realDebridFacade,
                dispatcher = Dispatchers.Default
            )

            return AppGraph(
                startupBootstrapper = StartupBootstrapper(
                    setupStateStore = setupStateStore,
                    uriGrantRegistry = uriGrantRegistry,
                    appReadinessCoordinator = appReadinessCoordinator,
                    sourceFacade = sourceFacade,
                    downloadsFacade = downloadsFacade
                ),
                appReadinessCoordinator = appReadinessCoordinator,
                setupSubmissionCoordinator = SetupSubmissionCoordinator(
                    realDebridFacade = realDebridFacade,
                    sourceFacade = sourceFacade,
                    downloadsFacade = downloadsFacade,
                    setupStateStore = setupStateStore
                ),
                shellNavigator = ShellNavigator(diagnosticsFacade),
                uriGrantRegistry = uriGrantRegistry,
                notificationDeepLinkHandler = NotificationDeepLinkHandler(),
                notificationPermissionRequester = NotificationPermissionRequester(),
                sourceFacade = sourceFacade,
                downloadsFacade = downloadsFacade,
                realDebridFacade = realDebridFacade,
                remoteZipFacade = remoteZipFacade,
                diagnosticsFacade = diagnosticsFacade
            )
        }
    }
}

internal class UnwiredSetupStateStore : SetupStateStore {
    override suspend fun read(): SetupCompletionRecord =
        throw UnsupportedOperationException("SetupStateStore is not wired yet")

    override suspend fun markCompleted(at: Instant): Result<Unit> = Result.failure(
        UnsupportedOperationException("SetupStateStore is not wired yet")
    )
}

internal class FailFastUriGrantRegistry(private val packageName: String) : UriGrantRegistry {
    override suspend fun captureSourceGrant(uri: Uri): Result<PersistedUriGrant> = Result.failure(
        UnsupportedOperationException(
            "UriGrantRegistry is not wired yet for $packageName"
        )
    )

    override suspend fun captureOutputGrant(uri: Uri): Result<PersistedUriGrant> = Result.failure(
        UnsupportedOperationException(
            "UriGrantRegistry is not wired yet for $packageName"
        )
    )

    override suspend fun restorePersistedGrants(): GrantRestoreReport =
        throw UnsupportedOperationException("UriGrantRegistry is not wired yet for $packageName")

    override suspend fun revoke(uri: Uri): Result<Unit> = Result.failure(
        UnsupportedOperationException("UriGrantRegistry is not wired yet for $packageName")
    )
}
