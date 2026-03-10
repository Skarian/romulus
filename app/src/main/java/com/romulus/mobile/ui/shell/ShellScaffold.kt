package com.romulus.mobile.ui.shell

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.app.shell.ShellNavigator
import com.romulus.mobile.app.startup.SetupSubmissionCoordinator
import com.romulus.mobile.app.startup.ShellReadiness
import com.romulus.mobile.app.startup.StartupRouteDecision
import com.romulus.mobile.app.startup.StartupSessionState
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.SourceFacade
import kotlinx.coroutines.flow.StateFlow

@Suppress("LongParameterList", "ParameterNaming", "UnusedParameter")
@Composable
fun ShellScaffold(
    startupState: StateFlow<StartupSessionState>,
    shellNavigator: ShellNavigator,
    shellReadiness: StateFlow<ShellReadiness>,
    setupSubmissionCoordinator: SetupSubmissionCoordinator,
    sourceFacade: SourceFacade,
    downloadsFacade: DownloadsFacade,
    realDebridFacade: RealDebridFacade,
    diagnosticsFacade: DiagnosticsFacade,
    onPersistSourceGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onPersistOutputGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by startupState.collectAsState()
    val readiness by shellReadiness.collectAsState()
    val route by shellNavigator.observeRoute().collectAsState()

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Shell scaffold is not wired into the running UI yet.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when (val routeDecision = session.routeDecision) {
                    null -> if (session.bootstrapping) {
                        "Startup bootstrap is in progress."
                    } else {
                        "Startup route has not been published yet."
                    }

                    StartupRouteDecision.Setup -> "Startup would route to Setup."
                    is StartupRouteDecision.Shell ->
                        "Startup would route to ${routeDecision.initialRoute}."
                }
            )
            Text(text = "Navigator route: $route")
            Text(
                text = if (readiness.brokenSettings.isEmpty()) {
                    "Shell readiness reports no broken settings."
                } else {
                    "Shell readiness reports broken settings: " +
                        readiness.brokenSettings.joinToString() +
                        "."
                }
            )
            if (readiness.hasUsableSnapshot) {
                Text(text = "A usable source snapshot is currently reported.")
            }
        }
    }
}
