# `app/` Package Architecture

## Purpose

`app/` is the Android composition root. It owns process startup, setup gating, shell-route state, notification deep-link intake, and URI-grant capture or restoration. It may compose readiness from neighboring packages, but it does not own token rules, source rules, queue rules, diagnostics storage, or output policy.

This package doc uses structural pseudocode by default. Full pseudocode bodies appear only where startup ordering is itself a contract:
- persisted grants must be restored before saved-setting readiness is evaluated,
- URL cold-launch refresh must run once per cold launch from an activity-lifecycle startup boundary outside normal composition,
- successful setup must hand off into the main shell without forcing process recreation.

## Owned Responsibilities

- Build the process-wide dependency graph.
- Persist and read the setup-completion record.
- Capture, restore, and revoke persisted URI grants for local source documents and output directories.
- Compose live shell-readiness from `realdebrid/`, `source/`, and `downloads/config`.
- Trigger one cold-launch source refresh for URL mode exactly once per explicit cold-launch token after setup completion.
- Translate Android intents and notification taps into safe shell routes.
- Own shell navigation state and setup-to-shell handoff.
- Request Android notification permission once after first successful setup completion.
- Request download app-launch recovery wake once after completed setup so stale queue claims are reconsidered explicitly.

## Explicit Non-Responsibilities

- `app/` does not validate API tokens, source JSON, or download settings.
- `app/` does not own source snapshots, browse rows, queue rows, output reservations, or diagnostics artifacts.
- `app/` does not repair broken settings directly; it exposes the shell and lets owners surface corrective state.
- `app/` does not run background download work.
- `app/` does not compose notification copy.

## Authorities and Owned Records

- `SetupCompletionRecord`
  - Persisted in shared `ConfigStore`.
  - Decides whether first-run setup is complete.
- `PersistedUriGrant`
  - Canonical app-owned record for reusable document permissions.
- `ShellReadiness`
  - App-owned composed view of neighboring package readiness.
  - It is derived state, not an authority over neighboring package records.
- `ColdLaunchToken`
  - App-owned token that marks one cold launch and prevents bootstrap work from being replayed through recomposition.
- `StartupRouteDecision`
  - Canonical first route decision for one cold start.
- `StartupSessionState`
  - App-owned lifecycle state for the one-shot startup boundary.
- `AppLaunchIntent`
  - Sanitized representation of Android launch or notification input.
- `ShellRoute`
  - Canonical app-shell route vocabulary.

## Dependencies

- Inbound dependencies:
  - Android `Application`, `Activity`, intent, and picker callbacks enter `app/` first.
  - No product package depends on `app/`.
- Outbound dependencies:
  - `ui/` for rendering only.
- `source/` for accepted-source state, readiness, browse routes, and cold-launch refresh.
- `downloads/` for settings readiness, app-launch recovery wake, and post-files navigation target.
  - `realdebrid/` for token readiness.
  - `diagnostics/` for app-shell event emission.
- What may cross the root-package boundary:
  - owner-facing facades,
  - typed readiness snapshots and flows,
  - shell routes,
  - persisted-grant descriptors.
- What may not cross the root-package boundary:
  - other packages' stores,
  - queue or snapshot mutations invented by `app/`,
  - raw Android platform types into non-`app/` packages.

## Public Entry Points

- `AppGraph.create(application: Application): AppGraph`
- `StartupSessionViewModel.startOnce(request: StartupBootstrapRequest): Unit`
- `StartupSessionViewModel.completeSetup(initialRoute: ShellRoute): Unit`
- `StartupBootstrapper.bootstrap(request: StartupBootstrapRequest): StartupRouteDecision`
- `AppReadinessCoordinator.observeShellReadiness(): StateFlow<ShellReadiness>`
- `SetupSubmissionCoordinator.submit(draft: SetupDraft): SetupSubmissionResult`
- `DownloadsFacade.requestAppLaunchRecovery(): Result<Unit>`
- `ShellNavigator.observeRoute(): StateFlow<ShellRoute>`
- `UriGrantRegistry.captureSourceGrant(uri: Uri): Result<PersistedUriGrant>`
- `UriGrantRegistry.captureOutputGrant(uri: Uri): Result<PersistedUriGrant>`
- `UriGrantRegistry.restorePersistedGrants(): GrantRestoreReport`
- `NotificationDeepLinkHandler.resolve(intent: Intent?): AppLaunchIntent?`

## Internal Structure

### `app/startup`

- Purpose: own cold-start bootstrap, setup gating, live readiness composition, and the initial route decision.
- What it owns:
  - setup-completion reads,
  - persisted-grant restoration,
  - the explicit cold-launch token boundary,
  - cold-launch source-refresh trigger,
  - shell-readiness composition,
  - startup route decision.
- What it must not own:
  - token validation,
  - source parsing,
  - queue mutation,
  - UI rendering.
- Sibling interaction:
  - uses `app/platform` for Android seams,
  - uses `app/shell` for route vocabulary and route state.
- What may cross this seam:
  - `ShellReadiness`,
  - `StartupRouteDecision`,
  - `AppLaunchIntent`.
- What may not cross this seam:
  - raw `Intent`,
  - raw `UriPermission`,
  - neighboring package stores.

### `app/shell`

- Purpose: own stable route names and in-process shell navigation state.
- What it owns:
  - `ShellRoute`,
  - selected tab or file-subroute state,
  - setup-to-shell handoff.
- What it must not own:
  - startup gating rules,
  - screen-local search or dialog state,
  - deep-link parsing.
- Sibling interaction:
  - consumes `app/startup` startup decisions,
  - is hosted by `ui/shell`.
- What may cross this seam:
  - `ShellRoute`,
  - one-shot navigation requests.
- What may not cross this seam:
  - Compose-only state objects,
  - business stores.

### `app/platform`

- Purpose: isolate Android-only seams from product packages.
- What it owns:
  - `Application` and `Activity` bootstrap hooks,
  - document-grant persistence,
  - notification-permission trigger,
  - notification deep-link parsing.
- What it must not own:
  - source or download validation,
  - notification copy,
  - queue recovery.
- Sibling interaction:
  - feeds launch and picker results into `app/startup` and `app/shell`.
- What may cross this seam:
  - `PersistedUriGrant`,
  - `AppLaunchIntent`.
- What may not cross this seam:
  - raw platform callbacks into non-`app/` packages.

## Internal Files

### `RomulusApplication.kt`
- Internal area: `app/platform`
- Purpose: create the process-wide app graph exactly once.
- Responsibility: expose `AppGraph` to Android entry points.
- Depends on: `AppGraph`
- Must not depend on: UI screens, queue stores, provider clients
- Visibility: `public`
- Key types/functions:

```kotlin
class RomulusApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph.create(this)
    }
}
```

### `MainActivity.kt`
- Internal area: `app/platform`
- Purpose: host the root Compose tree and bridge Android intents and picker results into app-owned seams.
- Responsibility: start bootstrap exactly once per cold launch outside normal composition, hand persisted-grant callbacks to the UI host, and keep shell state across configuration change through app-owned navigation state.
- Depends on: `RomulusApplication`, `StartupSessionViewModel`, `ShellNavigator`, `NotificationDeepLinkHandler`, `UriGrantRegistry`, `ui/ShellScaffold.kt`
- Must not depend on: neighboring package stores
- Visibility: `public`
- Key types/functions:

```kotlin
class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph
        get() = (application as RomulusApplication).appGraph

    private val startupSessionViewModel by viewModels<StartupSessionViewModel> {
        StartupSessionViewModel.factory(appGraph.startupBootstrapper)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startupSessionViewModel.startOnce(
            StartupBootstrapRequest(
                coldLaunchToken = startupSessionViewModel.coldLaunchToken,
                launchIntent = appGraph.notificationDeepLinkHandler.resolve(intent)
            )
        )

        setContent {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appGraph.shellNavigator.acceptLaunchIntent(
            appGraph.notificationDeepLinkHandler.resolve(intent)
        )
    }
}
```

### `AppGraph.kt`
- Internal area: `app/startup`
- Purpose: define the concrete composition root.
- Responsibility: create one instance of each root-package facade and the app-owned startup or shell seams.
- Depends on: all root-package public facades and app-owned startup classes
- Must not depend on: screen-local UI state, test fixtures
- Visibility: `public`
- Key types/functions:

```kotlin
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
        fun create(application: Application): AppGraph
    }
}
```

### `StartupSessionViewModel.kt`
- Internal area: `app/startup`
- Purpose: own the one-shot startup boundary used by the activity host.
- Responsibility: ensure bootstrap runs once for one explicit cold-launch token instead of being replayed by recomposition.
- Depends on: `StartupBootstrapper`
- Must not depend on: screen-local UI state, neighboring package stores
- Visibility: `public`
- Key types/functions:

```kotlin
@JvmInline
value class ColdLaunchToken(val value: String) {
    companion object {
        fun create(): ColdLaunchToken
    }
}

data class StartupBootstrapRequest(
    val coldLaunchToken: ColdLaunchToken,
    val launchIntent: AppLaunchIntent?
)

data class StartupSessionState(
    val bootstrapping: Boolean,
    val routeDecision: StartupRouteDecision?
)

class StartupSessionViewModel(
    private val startupBootstrapper: StartupBootstrapper
) : ViewModel() {
    val coldLaunchToken: ColdLaunchToken
    val state: StateFlow<StartupSessionState>

    fun startOnce(request: StartupBootstrapRequest)
    fun completeSetup(initialRoute: ShellRoute)

    companion object {
        fun factory(startupBootstrapper: StartupBootstrapper): ViewModelProvider.Factory
    }
}
```

### `AppReadinessCoordinator.kt`
- Internal area: `app/startup`
- Purpose: compose live shell-readiness from neighboring owner-owned readiness flows.
- Responsibility: expose one startup read and one live flow without taking over neighboring authority.
- Depends on: `SourceFacade`, `DownloadsFacade`, `RealDebridFacade`
- Must not depend on: neighboring package stores
- Visibility: `public`
- Key types/functions:

```kotlin
enum class BrokenSetting {
    API_TOKEN,
    SOURCE,
    DOWNLOAD_DIRECTORY
}

data class ShellReadiness(
    val brokenSettings: Set<BrokenSetting>,
    val hasUsableSnapshot: Boolean
)

class AppReadinessCoordinator(
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val realDebridFacade: RealDebridFacade
) {
    suspend fun readStartupReadiness(): ShellReadiness
    fun observeShellReadiness(): StateFlow<ShellReadiness>
}
```

### `StartupBootstrapper.kt`
- Internal area: `app/startup`
- Purpose: own cold-start ordering.
- Responsibility: restore grants, read setup state, run URL cold-launch refresh once for the provided cold-launch token when required, request queue recovery wake, and return the initial route decision.
- Depends on: `SetupStateStore`, `UriGrantRegistry`, `AppReadinessCoordinator`, `SourceFacade`, `DownloadsFacade`
- Must not depend on: UI reducers, queue stores
- Visibility: `public`
- Key types/functions:

```kotlin
sealed interface StartupRouteDecision {
    data object Setup : StartupRouteDecision
    data class Shell(
        val initialRoute: ShellRoute
    ) : StartupRouteDecision
}

class StartupBootstrapper(
    private val setupStateStore: SetupStateStore,
    private val uriGrantRegistry: UriGrantRegistry,
    private val appReadinessCoordinator: AppReadinessCoordinator,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade
) {
    suspend fun bootstrap(request: StartupBootstrapRequest): StartupRouteDecision {
        val setup = setupStateStore.read()
        if (!setup.isCompleted) return StartupRouteDecision.Setup

        uriGrantRegistry.restorePersistedGrants()

        val sourceReadiness = sourceFacade.readStartupReadiness()
        if (sourceReadiness.acceptedMode == SourceMode.URL) {
            // Contract: the caller provides one explicit cold-launch token and invokes bootstrap once for it.
            sourceFacade.refresh(SourceRefreshTrigger.ColdLaunch)
        }

        appReadinessCoordinator.readStartupReadiness()
        downloadsFacade.requestAppLaunchRecovery()

        return if (request.launchIntent?.preferredRoute == ShellRoute.Downloads) {
            StartupRouteDecision.Shell(initialRoute = ShellRoute.Downloads)
        } else {
            StartupRouteDecision.Shell(initialRoute = ShellRoute.Home)
        }
    }
}
```

### `SetupSubmissionCoordinator.kt`
- Internal area: `app/startup`
- Purpose: own first-run setup submission as one app-facing workflow without moving business ownership into `app/`.
- Responsibility: sequence API-key validation, source acceptance, download-settings save, and setup-completion mark, while keeping shell unlock tied only to the app-owned completion record.
- Depends on: `RealDebridFacade`, `SourceFacade`, `DownloadsFacade`, `SetupStateStore`
- Must not depend on: queue stores, provider clients directly
- Visibility: `public`
- Key types/functions:

```kotlin
data class SetupDraft(
    val apiKey: String,
    val source: AcceptSourceCommand,
    val downloadSettings: DownloadSettingsDraft
)

sealed interface SetupSubmissionResult {
    data object Completed : SetupSubmissionResult
    data class Rejected(
        val message: String,
        val sourceIssues: List<SourceValidationIssue> = emptyList()
    ) : SetupSubmissionResult
}

class SetupSubmissionCoordinator(
    private val realDebridFacade: RealDebridFacade,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val setupStateStore: SetupStateStore
) {
    suspend fun submit(draft: SetupDraft): SetupSubmissionResult
}
```

### `SetupStateStore.kt`
- Internal area: `app/startup`
- Purpose: own only the app-owned setup-completion record inside shared `ConfigStore`.
- Responsibility: keep setup completion separate from neighboring package records.
- Depends on: shared `ConfigStore`
- Must not depend on: source, downloads, or diagnostics rules
- Visibility: `public`
- Key types/functions:

```kotlin
data class SetupCompletionRecord(
    val isCompleted: Boolean,
    val completedAt: Instant?
)

interface SetupStateStore {
    suspend fun read(): SetupCompletionRecord
    suspend fun markCompleted(at: Instant): Result<Unit>
}
```

### `ShellRouteModels.kt`
- Internal area: `app/shell`
- Purpose: define the canonical shell-route vocabulary.
- Responsibility: keep route identity stable across startup, shell navigation, and notification deep links.
- Depends on: Kotlin stdlib only
- Must not depend on: Compose, neighboring package stores
- Visibility: `public`
- Key types/functions:

```kotlin
sealed interface ShellRoute {
    data object Home : ShellRoute
    data class Files(
        val snapshotId: SnapshotId,
        val entryId: SourceEntryId
    ) : ShellRoute
    data object Downloads : ShellRoute
    data object Settings : ShellRoute
}

data class AppLaunchIntent(
    val preferredRoute: ShellRoute?,
    val source: LaunchSource
)

enum class LaunchSource {
    APP_ICON,
    NOTIFICATION,
    UNKNOWN
}
```

### `ShellNavigator.kt`
- Internal area: `app/shell`
- Purpose: own shell-route state and route transitions.
- Responsibility: preserve the current shell route across configuration changes, remember the last Home-owned subroute when the user switches tabs, and accept app-owned navigation requests.
- Depends on: `ShellRouteModels.kt`, `DiagnosticsFacade`
- Must not depend on: screen-local state
- Visibility: `public`
- Key types/functions:

```kotlin
class ShellNavigator(
    private val diagnosticsFacade: DiagnosticsFacade
) {
    fun observeRoute(): StateFlow<ShellRoute>
    fun enterShell(initialRoute: ShellRoute)
    fun selectTab(route: ShellRoute)
    fun openFiles(snapshotId: SnapshotId, entryId: SourceEntryId)
    fun returnToHomeRoot()
    fun acceptLaunchIntent(intent: AppLaunchIntent?)
}
```

### `UriGrantRegistry.kt`
- Internal area: `app/platform`
- Purpose: persist reusable Android document grants.
- Responsibility: capture, restore, list, and revoke only app-owned grant descriptors.
- Depends on: Android `ContentResolver`, app-owned grant persistence
- Must not depend on: source or queue rules
- Visibility: `public`
- Key types/functions:

```kotlin
enum class UriGrantKind {
    SOURCE_READ,
    OUTPUT_TREE
}

data class PersistedUriGrant(
    val kind: UriGrantKind,
    val uri: Uri,
    val persistedAt: Instant
)

data class GrantRestoreReport(
    val restored: List<PersistedUriGrant>,
    val failed: List<Uri>
)

interface UriGrantRegistry {
    suspend fun captureSourceGrant(uri: Uri): Result<PersistedUriGrant>
    suspend fun captureOutputGrant(uri: Uri): Result<PersistedUriGrant>
    suspend fun restorePersistedGrants(): GrantRestoreReport
    suspend fun revoke(uri: Uri): Result<Unit>
}
```

### `NotificationDeepLinkHandler.kt`
- Internal area: `app/platform`
- Purpose: translate Android intents into safe app-owned route requests.
- Responsibility: recognize only supported deep links and degrade safely on unknown input.
- Depends on: `ShellRouteModels.kt`
- Must not depend on: notification text composition, queue state
- Visibility: `public`
- Key types/functions:

```kotlin
class NotificationDeepLinkHandler {
    fun resolve(intent: Intent?): AppLaunchIntent?
}

class NotificationPermissionRequester {
    fun requestIfNeeded()
}
```

- `NotificationPermissionRequester` owns runtime permission prompting only; notification channels are ensured by the posting boundary in `downloads/work`.

## Key Flows

1. Cold start before first successful setup:
   - `StartupBootstrapper` reads `SetupCompletionRecord`.
   - Route is `Setup`.
   - Shell tabs are not shown.

2. Cold start after setup completion:
   - `MainActivity` creates one `ColdLaunchToken` and starts `StartupSessionViewModel.startOnce(...)` from `onCreate`, outside normal composition.
   - `StartupBootstrapper` restores persisted grants.
   - `StartupBootstrapper` checks source mode.
   - If source mode is URL, it triggers `SourceRefreshTrigger.ColdLaunch` exactly once for that cold-launch token.
   - `AppReadinessCoordinator` composes live broken-setting state.
   - `StartupBootstrapper` requests one download app-launch recovery wake so interrupted queue work is reconsidered immediately after launch.
   - Shell opens on `Home` unless the launch intent safely requests `Downloads`.

3. Setup success handoff:
   - `ui/setup` submits `SetupDraft` to `SetupSubmissionCoordinator`.
   - `SetupSubmissionCoordinator` validates and saves the API key first, then accepts the source, then saves download settings.
   - `SetupStateStore` is marked complete only after all three owner operations succeed.
   - The host calls `StartupSessionViewModel.completeSetup(ShellRoute.Home)`.
   - `ui/shell` observes the startup-session transition into `StartupRouteDecision.Shell(...)` and aligns `ShellNavigator` with that initial route.
   - Notification permission is requested once.

4. Mid-session settings breakage:
   - `AppReadinessCoordinator.observeShellReadiness()` updates when token, source, or download-directory readiness changes.
   - Shell stays visible.
   - `ui/home` and `ui/settings` react to live readiness without reconstructing the shell.

5. Notification tap:
   - `NotificationDeepLinkHandler` converts Android `Intent` into `AppLaunchIntent`.
   - `ShellNavigator` routes to `Downloads`.
   - Unknown actions degrade safely to the existing route.

## Failure and Recovery Rules

- Broken saved settings after first completion never route the user back into setup.
- Unknown startup routes fall back safely to `Home`.
- URI-grant restore failure is non-fatal; neighboring packages surface broken-setting state if the missing grant matters.
- Cold-launch refresh failure must not crash startup; `source/` owns retained-prior versus no-snapshot behavior.
- Download app-launch recovery wake is non-fatal; if the worker cannot be scheduled, the shell still starts and the queue remains durable until a later wake succeeds.
- Before first successful setup completion, earlier owner-owned saves may remain persisted if a later setup step fails, but setup stays gated because only `SetupCompletionRecord` unlocks the shell.
- `app/` must never synthesize repaired business state when readiness fails.

## Testing Plan

### `StartupBootstrapperTest.kt`

- Scope: unit
- Covers:
  - setup-gated launch
  - URL cold-launch refresh fires for a cold-launch bootstrap request after completed setup
  - file-source launch does not trigger cold-launch refresh
  - completed-setup launch requests download app-launch recovery wake
  - notification deep-link routes to downloads
- Fixtures:
  - fake `SetupStateStore`
  - fake `UriGrantRegistry`
  - fake `SourceFacade`
  - fake `DownloadsFacade`
  - fake `AppReadinessCoordinator`

### `StartupSessionViewModelTest.kt`

- Scope: unit
- Covers:
  - bootstrap runs once for one cold-launch token even if the host recomposes
  - repeated `startOnce` calls with the same token do not replay cold-launch refresh
  - completed bootstrap publishes the route decision for the shell host
  - successful setup completion transitions the same host session from `Setup` to `Shell`
- Fixtures:
  - fake `StartupBootstrapper`

### `AppReadinessCoordinatorTest.kt`

- Scope: unit
- Covers:
  - broken-setting aggregation across owners
  - live readiness updates after settings repair
  - usable-snapshot flag composition
- Fixtures:
  - fake `SourceFacade`
  - fake `DownloadsFacade`
  - fake `RealDebridFacade`

### `SetupSubmissionCoordinatorTest.kt`

- Scope: unit
- Covers:
  - successful setup sequencing
  - token failure blocks setup completion
  - source failure blocks setup completion
  - download-settings failure blocks setup completion
- Fixtures:
  - fake `RealDebridFacade`
  - fake `SourceFacade`
  - fake `DownloadsFacade`
  - fake `SetupStateStore`

### `ShellNavigatorTest.kt`

- Scope: unit
- Covers:
  - setup-to-shell handoff
  - file-route opening
  - notification deep-link acceptance
  - configuration-change-safe route preservation
- Fixtures:
  - fake `DiagnosticsFacade`

### `UriGrantRegistryIntegrationTest.kt`

- Scope: integration
- Covers:
  - source grant capture and restore
  - output grant capture and restore
  - revoke removes the persisted grant
- Fixtures:
  - Android `ContentResolver`
  - fake document URIs

### `MainActivityStartupIntegrationTest.kt`

- Scope: integration
- Covers:
  - cold start into setup
  - cold start into shell
  - bootstrap starts before Compose rendering and is not re-invoked by recomposition
  - notification deep-link handoff
  - setup success enters shell without activity recreation
- Fixtures:
  - fake `AppGraph`
  - Compose test rule

## Open Questions or Deferred Decisions

None currently.
