package com.romulus.mobile.app.startup

import android.app.Application
import com.romulus.mobile.app.platform.AndroidUriGrantRegistry
import com.romulus.mobile.app.platform.NotificationDeepLinkHandler
import com.romulus.mobile.app.platform.NotificationPermissionRequester
import com.romulus.mobile.app.platform.UriGrantRegistry
import com.romulus.mobile.app.shell.ShellNavigator
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.remotezip.RemoteZipFacade
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.source.browse.ArchiveContainerPreparationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
        @Suppress("InjectDispatcher", "LongMethod")
        fun create(application: Application): AppGraph {
            val diagnosticsFacade = DiagnosticsFacade.create(application)
            val realDebridFacade = RealDebridFacade.create(
                application = application,
                diagnosticsFacade = diagnosticsFacade
            )
            val remoteZipFacade = RemoteZipFacade.create()
            val archiveContainerPreparationService = ArchiveContainerPreparationService.create(
                application = application,
                realDebridFacade = realDebridFacade,
                remoteZipFacade = remoteZipFacade
            )
            val sourceFacade = SourceFacade.create(
                application = application,
                realDebridFacade = realDebridFacade,
                archiveContainerPreparationService = archiveContainerPreparationService,
                diagnosticsFacade = diagnosticsFacade
            )
            val downloadsFacade = DownloadsFacade.create(
                application = application,
                realDebridFacade = realDebridFacade,
                remoteZipFacade = remoteZipFacade,
                archiveContainerPreparationService = archiveContainerPreparationService,
                diagnosticsFacade = diagnosticsFacade
            )
            val setupStateStore: SetupStateStore = SharedPreferencesSetupStateStore(application)
            val uriGrantRegistry = AndroidUriGrantRegistry(application)
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
                    downloadsFacade = downloadsFacade,
                    diagnosticsFacade = diagnosticsFacade
                ),
                appReadinessCoordinator = appReadinessCoordinator,
                setupSubmissionCoordinator = SetupSubmissionCoordinator(
                    realDebridFacade = realDebridFacade,
                    sourceFacade = sourceFacade,
                    downloadsFacade = downloadsFacade,
                    setupStateStore = setupStateStore
                ),
                shellNavigator = ShellNavigator(
                    diagnosticsFacade = diagnosticsFacade,
                    diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                ),
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
