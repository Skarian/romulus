package com.romulus.mobile.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.romulus.mobile.core.text.HomeDisambiguator
import com.romulus.mobile.core.validation.ValidationResult
import com.romulus.mobile.data.downloads.QueuedDownload
import com.romulus.mobile.data.downloads.TaskProgress
import com.romulus.mobile.data.downloads.local.DownloadTaskEntity
import com.romulus.mobile.data.settings.AppSettings
import com.romulus.mobile.domain.source.SourceEntry
import com.romulus.mobile.domain.source.SourceMode
import com.romulus.mobile.domain.source.SourceSnapshot
import com.romulus.mobile.feature.downloads.DownloadsScreen
import com.romulus.mobile.feature.files.FilesScreen
import com.romulus.mobile.feature.home.HomeScreen
import com.romulus.mobile.feature.settings.SettingsScreen
import com.romulus.mobile.feature.setup.SetupFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun RomulusApp(initialRoute: String? = null) {
    MaterialTheme {
        val context = LocalContext.current
        val appContainer = remember(context) {
            (context.applicationContext as RomulusApplication).appContainer
        }
        val scope = rememberCoroutineScope()

        var settings by remember { mutableStateOf(AppSettings()) }
        var snapshot by remember { mutableStateOf(emptySnapshot()) }
        var tasks by remember { mutableStateOf<List<DownloadTaskEntity>>(emptyList()) }
        var liveProgress by remember { mutableStateOf<Map<String, TaskProgress>>(emptyMap()) }
        var handledInitialRoute by remember { mutableStateOf<String?>(null) }
        var coldRefreshKey by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            launch {
                appContainer.settingsRepository.settings.collectLatest {
                    settings = it
                }
            }
            launch {
                appContainer.sourceRepository.observeActiveSnapshot().collectLatest {
                    snapshot = it
                }
            }
            launch {
                appContainer.queueRepository.observeTasks().collectLatest {
                    appContainer.taskProgressTracker.pruneToTaskIds(it.mapTo(mutableSetOf()) { task -> task.id })
                    tasks = it
                }
            }
            launch {
                appContainer.taskProgressTracker.observe().collectLatest {
                    liveProgress = it
                }
            }
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { }

        var notificationPermissionRequested by remember { mutableStateOf(false) }
        LaunchedEffect(settings.setupComplete) {
            if (!settings.setupComplete) return@LaunchedEffect
            if (notificationPermissionRequested) return@LaunchedEffect
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            notificationPermissionRequested = true
        }

        LaunchedEffect(settings.setupComplete, settings.sourceMode, settings.sourceValue) {
            if (!settings.setupComplete) return@LaunchedEffect
            if (settings.sourceMode != SourceMode.URL) return@LaunchedEffect
            val sourceValue = settings.sourceValue?.trim().orEmpty()
            if (sourceValue.isBlank()) return@LaunchedEffect
            val key = "${settings.sourceMode}::$sourceValue"
            if (coldRefreshKey == key) return@LaunchedEffect
            appContainer.sourceRepository.refreshFromUrlOnColdLaunchIfNeeded()
            coldRefreshKey = key
        }

        if (!settings.setupComplete) {
            SetupFlow(
                settings = settings,
                onValidateApiKey = { apiKey ->
                    val validation = appContainer.realDebridClient.validateApiKey(apiKey)
                    if (validation is ValidationResult.Valid) {
                        appContainer.settingsRepository.saveApiKey(apiKey)
                    }
                    validation
                },
                onSetUrlSource = { url ->
                    appContainer.sourceRepository.setUrlSource(url)
                },
                onSetLocalSource = { uri ->
                    grantReadPermission(context, uri)
                    appContainer.sourceRepository.setLocalFileSource(uri)
                },
                onSetDownloadDirectory = { uri ->
                    grantReadWritePermission(context, uri)
                    appContainer.settingsRepository.setDownloadDirectoryUri(uri.toString())
                },
                onComplete = {
                    if (settings.isConfigurationValid) {
                        appContainer.settingsRepository.setSetupComplete(true)
                    }
                }
            )
            return@MaterialTheme
        }

        val navController = rememberNavController()
        val tabs = remember {
            listOf(
                NavTab(route = Routes.HOME, label = "Home", icon = Icons.Filled.Home),
                NavTab(route = Routes.DOWNLOADS, label = "Downloads", icon = Icons.Filled.Download),
                NavTab(route = Routes.SETTINGS, label = "Settings", icon = Icons.Filled.Settings)
            )
        }

        val activeCount = tasks.count { it.state.isActive }
        val displayTasks = applyLiveProgress(tasks, liveProgress)
        LaunchedEffect(settings.setupComplete, initialRoute) {
            if (!settings.setupComplete) return@LaunchedEffect
            val route = initialRoute ?: return@LaunchedEffect
            if (handledInitialRoute == route) return@LaunchedEffect
            if (route == Routes.DOWNLOADS) {
                navController.navigate(Routes.DOWNLOADS) {
                    launchSingleTop = true
                }
            }
            handledInitialRoute = route
        }

        Scaffold(
            bottomBar = {
                BottomAppBar {
                    val currentRoute = navController.currentBackStackEntryAsState().value
                        ?.destination
                        ?.route
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!settings.isConfigurationValid) {
                    Text(
                        text = "Configuration requires remediation in Settings.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.HOME) {
                        val sourceStatus = when (settings.sourceMode) {
                            SourceMode.URL -> if (settings.sourceIsStale) {
                                "Source: stale cache"
                            } else {
                                "Source: live URL"
                            }

                            SourceMode.FILE -> "Source: local file"
                            null -> "Source: not configured"
                        }
                        HomeScreen(
                            rows = HomeDisambiguator.toRows(snapshot.entries),
                            sourceStatus = sourceStatus,
                            validationSummary = snapshot.issues.takeIf { it.isNotEmpty() }?.let { issues ->
                                val count = issues.size
                                if (count == 1) {
                                    "1 invalid source entry was skipped."
                                } else {
                                    "$count invalid source entries were skipped."
                                }
                            },
                            onRefresh = {
                                scope.launch {
                                    appContainer.sourceRepository.refreshFromUrlOnColdLaunchIfNeeded()
                                }
                            },
                            onEntryClick = { entryIndex ->
                                navController.navigate("${Routes.FILES}/$entryIndex")
                            }
                        )
                    }

                    composable(Routes.DOWNLOADS) {
                        DownloadsScreen(
                            tasks = displayTasks,
                            onRetry = { id -> scope.launch { appContainer.queueController.retry(id) } },
                            onRestart = { id -> scope.launch { appContainer.queueController.restart(id) } },
                            onCancel = { id -> scope.launch { appContainer.queueController.cancel(id) } },
                            onPause = { id -> scope.launch { appContainer.queueController.pause(id) } },
                            onResume = { id -> scope.launch { appContainer.queueController.resume(id) } },
                            onDeletePartial = { id -> scope.launch { appContainer.queueController.deletePartial(id) } },
                            onClearCompleted = { scope.launch { appContainer.queueController.clearCompleted() } }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            activeCount = activeCount,
                            onSaveApiKey = { apiKey ->
                                val validation = appContainer.realDebridClient.validateApiKey(apiKey)
                                if (validation is ValidationResult.Valid) {
                                    appContainer.settingsRepository.saveApiKey(apiKey)
                                }
                                validation
                            },
                            onSetSourceUrl = { url ->
                                appContainer.sourceRepository.setUrlSource(url)
                            },
                            onSetLocalSource = { uri ->
                                grantReadPermission(context, uri)
                                appContainer.sourceRepository.setLocalFileSource(uri)
                            },
                            onSetDirectory = { uri ->
                                grantReadWritePermission(context, uri)
                                appContainer.settingsRepository.setDownloadDirectoryUri(uri.toString())
                            },
                            onSetConcurrency = { value ->
                                appContainer.settingsRepository.setMaxConcurrency(value)
                            },
                            onManualRefresh = {
                                appContainer.sourceRepository.refreshFromUrlOnColdLaunchIfNeeded()
                            }
                        )
                    }

                    composable(
                        route = "${Routes.FILES}/{entryIndex}",
                        arguments = listOf(navArgument("entryIndex") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val entryIndex = backStackEntry.arguments?.getInt("entryIndex") ?: -1
                        val entry = snapshot.entries.firstOrNull { it.index == entryIndex }
                        if (entry == null) {
                            Text(
                                text = "Entry not found",
                                modifier = Modifier.padding(16.dp)
                            )
                            return@composable
                        }
                        FilesScreen(
                            snapshotId = snapshot.snapshotId,
                            entry = entry,
                            onResolveFiles = { selectedEntry ->
                                resolveFilesForEntry(
                                    selectedEntry,
                                    appContainer.settingsRepository,
                                    appContainer.fileSelectionRepository
                                )
                            },
                            onEnqueue = { queued ->
                                appContainer.queueController.enqueue(queued)
                            },
                            onBackToHome = {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                        )
                    }

                }
            }
        }
    }
}

private suspend fun resolveFilesForEntry(
    entry: SourceEntry,
    settingsRepository: com.romulus.mobile.data.settings.SettingsRepository,
    fileSelectionRepository: com.romulus.mobile.data.files.FileSelectionRepository
): Result<List<com.romulus.mobile.domain.files.FileOption>> {
    val apiKey = settingsRepository.readApiKey()
        ?: return Result.failure(IllegalStateException("API key not configured"))
    return fileSelectionRepository.resolveFiles(entry, apiKey)
}

private fun applyLiveProgress(
    tasks: List<DownloadTaskEntity>,
    liveProgress: Map<String, TaskProgress>
): List<DownloadTaskEntity> {
    if (tasks.isEmpty()) return tasks
    return tasks.map { task ->
        val progress = liveProgress[task.id] ?: return@map task
        task.copy(
            bytesDownloaded = maxOf(task.bytesDownloaded, progress.bytesDownloaded),
            totalBytes = progress.totalBytes ?: task.totalBytes
        )
    }
}

private fun emptySnapshot(): SourceSnapshot {
    return SourceSnapshot(
        snapshotId = "empty",
        sourceMode = SourceMode.FILE,
        sourceValue = "",
        generatedAtEpochMs = 0,
        entries = emptyList(),
        issues = emptyList(),
        stale = false
    )
}

private fun grantReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun grantReadWritePermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private object Routes {
    const val HOME = "home"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val FILES = "files"
}
