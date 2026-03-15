package com.romulus.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.romulus.mobile.app.RomulusApplication
import com.romulus.mobile.app.shell.ShellRoute
import com.romulus.mobile.app.startup.StartupBootstrapRequest
import com.romulus.mobile.app.startup.StartupSessionViewModel
import com.romulus.mobile.ui.shell.ShellScaffold
import com.romulus.mobile.ui.theme.RomulusTheme

class MainActivity : ComponentActivity() {
    private val appGraph
        get() = (application as RomulusApplication).appGraph

    private val startupSessionViewModel by viewModels<StartupSessionViewModel> {
        StartupSessionViewModel.factory(appGraph.startupBootstrapper)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        startupSessionViewModel.startOnce(
            StartupBootstrapRequest(
                coldLaunchToken = startupSessionViewModel.coldLaunchToken,
                launchIntent = appGraph.notificationDeepLinkHandler.resolve(intent)
            )
        )

        setContent {
            RomulusTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { }

                SideEffect {
                    appGraph.notificationPermissionRequester.bindHost(
                        hasPermission = {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        },
                        requestPermission = {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    )
                }

                ShellScaffold(
                    startupState = startupSessionViewModel.state,
                    shellNavigator = appGraph.shellNavigator,
                    shellReadiness = appGraph.appReadinessCoordinator.observeShellReadiness(),
                    setupSubmissionCoordinator = appGraph.setupSubmissionCoordinator,
                    sourceFacade = appGraph.sourceFacade,
                    downloadsFacade = appGraph.downloadsFacade,
                    realDebridFacade = appGraph.realDebridFacade,
                    diagnosticsFacade = appGraph.diagnosticsFacade,
                    onPersistSourceGrant = appGraph.uriGrantRegistry::captureSourceGrant,
                    onPersistOutputGrant = appGraph.uriGrantRegistry::captureOutputGrant,
                    onSetupCompleted = {
                        startupSessionViewModel.completeSetup(ShellRoute.Home)
                        appGraph.notificationPermissionRequester.requestIfNeeded()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appGraph.shellNavigator.acceptLaunchIntent(
            appGraph.notificationDeepLinkHandler.resolve(intent)
        )
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
        const val ROUTE_DOWNLOADS = "downloads"
    }
}
