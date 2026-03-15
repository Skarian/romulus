@file:Suppress("CyclomaticComplexMethod", "LongMethod", "ViewModelInjection")

package com.romulus.mobile.ui.shell

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.romulus.mobile.app.platform.PersistedUriGrant
import com.romulus.mobile.app.shell.ShellNavigator
import com.romulus.mobile.app.shell.ShellRoute
import com.romulus.mobile.app.startup.SetupSubmissionCoordinator
import com.romulus.mobile.app.startup.ShellReadiness
import com.romulus.mobile.app.startup.StartupRouteDecision
import com.romulus.mobile.app.startup.StartupSessionState
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.downloads.DownloadsFacade
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.SourceFacade
import com.romulus.mobile.ui.components.RomulusPanel
import com.romulus.mobile.ui.downloads.DownloadsScreen
import com.romulus.mobile.ui.downloads.DownloadsViewModel
import com.romulus.mobile.ui.files.FilesRouteArgs
import com.romulus.mobile.ui.files.FilesScreen
import com.romulus.mobile.ui.files.FilesViewModel
import com.romulus.mobile.ui.home.HomeScreen
import com.romulus.mobile.ui.home.HomeViewModel
import com.romulus.mobile.ui.settings.SettingsScreen
import com.romulus.mobile.ui.settings.SettingsViewModel
import com.romulus.mobile.ui.setup.SetupScreen
import com.romulus.mobile.ui.setup.SetupViewModel
import kotlinx.coroutines.flow.StateFlow

private const val DARK_NAV_MIX = 0.68f
private const val LIGHT_NAV_MIX = 0.2f

@Suppress("LongParameterList", "ParameterNaming")
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
    val route by shellNavigator.observeRoute().collectAsState()

    LaunchedEffect(session.routeDecision) {
        (session.routeDecision as? StartupRouteDecision.Shell)?.let { routeDecision ->
            shellNavigator.enterShell(routeDecision.initialRoute)
        }
    }

    when (session.routeDecision) {
        null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (session.bootstrapping) {
                        "Starting Romulus..."
                    } else {
                        "Preparing startup route..."
                    }
                )
            }
        }

        StartupRouteDecision.Setup -> {
            val setupViewModel: SetupViewModel = viewModel(
                factory = savedStateFactory { savedStateHandle ->
                    SetupViewModel(
                        setupSubmissionCoordinator = setupSubmissionCoordinator,
                        savedStateHandle = savedStateHandle
                    )
                }
            )
            SetupScreen(
                viewModel = setupViewModel,
                onPersistSourceGrant = onPersistSourceGrant,
                onPersistOutputGrant = onPersistOutputGrant,
                onCompleted = onSetupCompleted,
                modifier = modifier
            )
        }

        is StartupRouteDecision.Shell -> {
            var downloadsScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
            val routeStateHolder = rememberSaveableStateHolder()
            BackHandler(enabled = route == ShellRoute.Downloads || route == ShellRoute.Settings) {
                shellNavigator.selectTab(ShellRoute.Home)
            }
            val darkTheme = isSystemInDarkTheme()
            val navContainerColor = if (darkTheme) {
                lerp(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.background,
                    DARK_NAV_MIX
                )
            } else {
                lerp(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.background,
                    LIGHT_NAV_MIX
                )
            }
            Scaffold(
                modifier = modifier,
                bottomBar = {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RomulusPanel(
                            containerColor = navContainerColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.outline
                        ) {
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (darkTheme) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                selectedTextColor = if (darkTheme) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                indicatorColor = if (darkTheme) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NavigationBar(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                tonalElevation = 0.dp,
                                windowInsets = NavigationBarDefaults.windowInsets.only(
                                    WindowInsetsSides.Horizontal
                                )
                            ) {
                                NavigationBarItem(
                                    selected = route.isHomeLike(),
                                    onClick = { shellNavigator.selectTab(ShellRoute.Home) },
                                    colors = itemColors,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Home,
                                            contentDescription = "Home",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Home",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                                NavigationBarItem(
                                    selected = route == ShellRoute.Downloads,
                                    onClick = { shellNavigator.selectTab(ShellRoute.Downloads) },
                                    colors = itemColors,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = "Downloads",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Downloads",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                                NavigationBarItem(
                                    selected = route == ShellRoute.Settings,
                                    onClick = { shellNavigator.selectTab(ShellRoute.Settings) },
                                    colors = itemColors,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Settings",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Settings",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                routeStateHolder.SaveableStateProvider(key = route.saveableKey()) {
                    when (route) {
                        ShellRoute.Home -> {
                            val homeViewModel: HomeViewModel = viewModel(
                                factory = savedStateFactory { savedStateHandle ->
                                    HomeViewModel(
                                        shellReadiness = shellReadiness,
                                        sourceFacade = sourceFacade,
                                        savedStateHandle = savedStateHandle
                                    )
                                }
                            )
                            HomeScreen(
                                viewModel = homeViewModel,
                                onOpenFiles = { filesRoute ->
                                    shellNavigator.openFiles(
                                        snapshotId = filesRoute.snapshotId,
                                        entryId = filesRoute.entryId,
                                        entryDisplayName = filesRoute.entryDisplayName
                                    )
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        is ShellRoute.Files -> {
                            val filesRoute = route as ShellRoute.Files
                            val filesViewModel: FilesViewModel = viewModel(
                                key = "files:${filesRoute.snapshotId.value}:" +
                                    filesRoute.entryId.value,
                                factory = savedStateFactory { savedStateHandle ->
                                    FilesViewModel(
                                        routeArgs = FilesRouteArgs(
                                            snapshotId = filesRoute.snapshotId,
                                            entryId = filesRoute.entryId,
                                            entryDisplayName = filesRoute.entryDisplayName
                                        ),
                                        sourceFacade = sourceFacade,
                                        downloadsFacade = downloadsFacade,
                                        diagnosticsFacade = diagnosticsFacade,
                                        savedStateHandle = savedStateHandle
                                    )
                                }
                            )
                            FilesScreen(
                                viewModel = filesViewModel,
                                onNavigateBack = shellNavigator::returnToHomeRoot,
                                onNavigateToDownloadsAndScrollToTop = {
                                    downloadsScrollToTopRequest += 1
                                    shellNavigator.selectTab(ShellRoute.Downloads)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        ShellRoute.Downloads -> {
                            val downloadsViewModel: DownloadsViewModel = viewModel(
                                factory = savedStateFactory { savedStateHandle ->
                                    DownloadsViewModel(
                                        downloadsFacade = downloadsFacade,
                                        savedStateHandle = savedStateHandle
                                    )
                                }
                            )
                            DownloadsScreen(
                                viewModel = downloadsViewModel,
                                modifier = Modifier.padding(innerPadding),
                                scrollToTopRequest = downloadsScrollToTopRequest,
                                onScrollToTopHandle = {
                                    downloadsScrollToTopRequest = 0
                                }
                            )
                        }

                        ShellRoute.Settings -> {
                            val settingsViewModel: SettingsViewModel = viewModel(
                                factory = savedStateFactory { savedStateHandle ->
                                    SettingsViewModel(
                                        shellReadiness = shellReadiness,
                                        realDebridFacade = realDebridFacade,
                                        sourceFacade = sourceFacade,
                                        downloadsFacade = downloadsFacade,
                                        diagnosticsFacade = diagnosticsFacade,
                                        savedStateHandle = savedStateHandle
                                    )
                                }
                            )
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onPersistSourceGrant = onPersistSourceGrant,
                                onPersistOutputGrant = onPersistOutputGrant,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ShellRoute.isHomeLike(): Boolean = this == ShellRoute.Home || this is ShellRoute.Files

private fun ShellRoute.saveableKey(): String = when (this) {
    ShellRoute.Home -> "home"
    is ShellRoute.Files -> "files:${snapshotId.value}:${entryId.value}"
    ShellRoute.Downloads -> "downloads"
    ShellRoute.Settings -> "settings"
}

private inline fun <reified T : ViewModel> savedStateFactory(
    crossinline build: (SavedStateHandle) -> T
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM {
            if (modelClass.isAssignableFrom(T::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return build(extras.createSavedStateHandle()) as VM
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
