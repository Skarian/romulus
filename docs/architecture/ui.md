# `ui/` Package Architecture

## Purpose

`ui/` owns presentation only: screens, view models, screen-local state, and translation from user intent into owner-facing commands. It renders the shell chosen by `app/`, but it does not own durable queue state, background execution, credentials, source snapshots, or diagnostics artifacts.

For migration work, `ui/` should stay closer to the current app than the other root packages. The current styling, table structure, buttons, dialogs, and overall interaction shape are the baseline unless a behavior contract or package boundary forces a targeted change.

This package doc uses structural pseudocode only. The goal is to freeze:
- screen boundaries,
- state ownership,
- effect channels,
- owner-facing commands,
- configuration-change preservation rules.

It does not try to freeze one exact Compose implementation.

## Owned Responsibilities

- Render the shell, setup, home, files, downloads, and settings surfaces.
- Hold screen-local search, selection, dialog, snackbar, and list-position state.
- Translate user edits and taps into typed commands for root-package facades.
- Observe owner-backed state and map it into presentation models.
- Preserve screen-local state across configuration change through lifecycle-aware view models and saved state.
- Preserve the current app's proven visual language by default.

## Explicit Non-Responsibilities

- `ui/` does not create or mutate durable queue rows directly.
- `ui/` does not own source snapshots, token storage, settings records, or diagnostics files.
- `ui/` does not run, pause, resume, or recover background work by itself.
- `ui/` does not parse notification intents or capture URI grants by itself.
- `ui/` does not orchestrate multi-owner business workflows beyond calling app-owned or owner-owned entry points.

## Authorities and Owned Records

- `ShellUiState`
  - Selected top-level route and shell-scoped transient effects.
- `SetupUiState`
  - First-run edit buffers, picker results, local validation messages, and submit-in-progress state.
- `HomeUiState`
  - Search dialog state plus explicit refresh visibility and typed warning presentation for invalid-settings, source-load, retained-prior, or missing-snapshot content modes.
- `HomeEffect`
  - Transient manual-refresh success or failure feedback.
- `FilesUiState`
  - Resolver state, selected rows, search query, file-preferences state, detail-dialog state, and list position.
- `DownloadsUiState`
  - Queue projection plus detail-dialog and clear-history dialog state.
- `SettingsUiState`
  - Editable drafts, field-specific lock state, diagnostics action progress, and toast or inline feedback state.

## Dependencies

- Inbound dependencies:
  - `app/` hosts the UI and owns shell route state.
- Outbound dependencies:
  - `app/` for shell navigation, setup completion handoff, and live shell readiness.
  - `source/` for accepted-source state, home state, refresh, and file browse loading.
  - `downloads/` for queue enqueue, queue projection, queue actions, and download-settings state.
  - `realdebrid/` for masked token state and token save.
  - `diagnostics/` for diagnostics controls only.
- What may cross the root-package boundary:
  - owner-facing facades,
  - typed commands and typed results,
  - immutable presentation models,
  - typed effect callbacks owned by `app/`.
- What may not cross the root-package boundary:
  - raw stores,
  - raw Android permission APIs,
  - provider transport models,
  - worker loops.

## Public Entry Points

- `ShellScaffold(...)`
- `SetupScreen(...)`
- `HomeScreen(...)`
- `FilesScreen(...)`
- `DownloadsScreen(...)`
- `SettingsScreen(...)`

These entry points are consumed only by `app/`.

## Internal Structure

### `ui/shell`

- Purpose: render the persistent shell chrome and host the current screen.
- What it owns:
  - tab layout,
  - shell-level snackbar host,
  - route-to-screen rendering,
  - restoring the last Home-owned subroute when the user returns to the `Home` tab,
  - compact shell spacing so rotated phone layouts keep usable content height.
- What it must not own:
  - startup routing,
  - notification deep-link parsing,
  - business readiness decisions.
- Sibling interaction:
  - uses `ui/setup`, `ui/home`, `ui/files`, `ui/downloads`, and `ui/settings` as screen content.
- What may cross this seam:
  - `ShellRoute`,
  - `ShellReadiness`,
  - app-owned callbacks.
- What may not cross this seam:
  - raw stores,
  - Android intents.

### `ui/setup`

- Purpose: render the single-screen setup flow.
- What it owns:
  - local edit buffers,
  - picked source-file and output-directory URIs,
  - local validation and save feedback.
- What it must not own:
  - token validation rules,
  - source acceptance rules,
  - durable setting writes.
- Sibling interaction:
  - hosted by `ui/shell` while setup is incomplete.
- What may cross this seam:
  - `SetupDraft`,
  - one-shot `SetupEffect`,
  - persisted-grant callbacks from `app/`.
- What may not cross this seam:
  - raw credential storage,
  - raw `ConfigStore`.

### `ui/home`

- Purpose: render source rows, invalid-settings state, stale-source warning, and refresh affordance.
- What it owns:
  - search query,
  - search dialog visibility.
- What it must not own:
  - source refresh policy,
  - broken-setting authority,
  - snapshot replacement.
- Sibling interaction:
  - hosted by `ui/shell`,
  - routes into `ui/files`.
- What may cross this seam:
  - `ShellReadiness`,
  - `HomeSourceState`,
  - `FilesRouteArgs`.
- What may not cross this seam:
  - snapshot store handles,
  - queue state.

### `ui/files`

- Purpose: render standard-file or archive-selection browse results and queue-intake controls.
- What it owns:
  - selected row ids,
  - search state,
  - file-preferences state,
  - detail-dialog state,
  - list position.
- What it must not own:
  - browse resolution algorithms,
  - durable queue creation,
  - output naming policy.
- Sibling interaction:
  - entered from `ui/home`,
  - routes to `ui/downloads` after successful enqueue.
- What may cross this seam:
  - `SelectableItem`,
  - `QueueTaskInput`,
  - `FilePreferencesState`.
- What may not cross this seam:
  - raw provider clients,
  - raw remote ZIP readers,
  - queue stores.

### `ui/downloads`

- Purpose: render queue rows, queue summary, detail dialog, and clear-history flow.
- What it owns:
  - selected-detail dialog state,
  - clear-history dialog state,
  - row-menu visibility.
- What it must not own:
  - canonical queue state,
  - retry scheduling,
  - notification rules.
- Sibling interaction:
  - hosted by `ui/shell`,
  - may be opened from `ui/files` or notification deep link.
- What may cross this seam:
  - `DownloadsProjection`,
  - `QueueActionCommand`,
  - `ClearHistoryDialogState`.
- What may not cross this seam:
  - ledger mutations outside public facade commands.

### `ui/settings`

- Purpose: render editors for token, source, download settings, and diagnostics controls.
- What it owns:
  - editable drafts,
  - per-field lock presentation,
  - diagnostics action progress and feedback.
- What it must not own:
  - token storage,
  - source refresh orchestration,
  - queue mutation beyond explicit user actions.
- Sibling interaction:
  - hosted by `ui/shell`,
  - consumes live `ShellReadiness` to decide which fields stay editable during active downloads.
- What may cross this seam:
  - masked token,
  - accepted source summary,
  - download-settings state,
  - diagnostics state.
- What may not cross this seam:
  - credential vault handles,
  - diagnostics artifacts,
  - queue store handles.

## Internal Files

### `ShellScaffold.kt`
- Internal area: `ui/shell`
- Purpose: render the shell host and route to the active screen.
- Responsibility: bind app-owned route state to composable destinations and collect app-facing effects from setup.
- Depends on: `ShellRoute`, screen composables, app-owned callbacks
- Must not depend on: neighboring package stores
- Visibility: `public`
- Key types/functions:

```kotlin
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
    onSetupCompleted: () -> Unit
)
```

### `SetupScreen.kt`
- Internal area: `ui/setup`
- Purpose: render the first-run form.
- Responsibility: show edits, picker results, validation errors, and submit progress while respecting the same safe top inset used by the shell-hosted screens, visually grouping the setup sections, and exposing the Real-Debrid token helper link.
- Depends on: `SetupViewModel`
- Must not depend on: stores or raw platform APIs
- Visibility: `public`
- Key types/functions:

```kotlin
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onPersistSourceGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onPersistOutputGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onCompleted: () -> Unit
)
```

### `SetupViewModel.kt`
- Internal area: `ui/setup`
- Purpose: hold setup-screen state and translate save intent into the app-owned setup workflow.
- Responsibility: keep local drafts, request grant capture through callbacks, and emit setup-complete effect on success.
- Depends on: `SetupSubmissionCoordinator`
- Must not depend on: `CredentialVault`, `ConfigStore`, queue stores
- Visibility: `public`
- Key types/functions:

```kotlin
data class SetupUiState(
    val apiKeyDraft: String,
    val sourceMode: SourceMode,
    val sourceValueDraft: String,
    val sourceDocumentUri: Uri?,
    val outputDirectoryUri: Uri?,
    val isSaving: Boolean,
    val errorMessage: String?
)

sealed interface SetupEffect {
    data object Completed : SetupEffect
}

class SetupViewModel(
    private val setupSubmissionCoordinator: SetupSubmissionCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val state: StateFlow<SetupUiState>
    val effects: Flow<SetupEffect>

    fun updateApiKey(value: String)
    fun updateSourceMode(mode: SourceMode)
    fun updateSourceValue(value: String)
    fun acceptSourceGrant(uri: Uri)
    fun acceptOutputGrant(uri: Uri)
    fun submit()
}
```

### `HomeScreen.kt`
- Internal area: `ui/home`
- Purpose: render home-state modes and the source row list.
- Responsibility: show invalid-settings, source-load error, typed retained-prior or missing-snapshot warnings, or content according to owner-backed state.
- Depends on: `HomeViewModel`
- Must not depend on: snapshot stores or queue stores
- Visibility: `public`
- Key types/functions:

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenFiles: (FilesRouteArgs) -> Unit
)
```

### `HomeViewModel.kt`
- Internal area: `ui/home`
- Purpose: combine live shell-readiness with source-owned home state.
- Responsibility: ensure Home recovers live when a broken setting is fixed in Settings.
- Depends on: `StateFlow<ShellReadiness>`, `SourceFacade`
- Must not depend on: startup-only snapshots, queue stores
- Visibility: `public`
- Key types/functions:

```kotlin
sealed interface HomeMode {
    data class InvalidSettings(val broken: Set<BrokenSetting>) : HomeMode
    data class SourceLoadError(
        val message: String,
        val retryVisible: Boolean
    ) : HomeMode
    data class Content(
        val snapshotId: SnapshotId,
        val rows: List<HomeRowModel>,
        val warning: HomeContentWarning?
    ) : HomeMode
}

sealed interface HomeContentWarning {
    data class LatestRefreshFailed(val message: String) : HomeContentWarning
    data class MissingSnapshotFallback(val message: String) : HomeContentWarning
}

data class HomeUiState(
    val mode: HomeMode,
    val searchQuery: String,
    val searchDialogOpen: Boolean,
    val refreshVisible: Boolean
)

sealed interface HomeEffect {
    data class RefreshFeedback(val message: String, val success: Boolean) : HomeEffect
}

data class HomeRowModel(
    val displayName: String,
    val folderContext: String,
    val routeArgs: FilesRouteArgs
)

class HomeViewModel(
    private val shellReadiness: StateFlow<ShellReadiness>,
    private val sourceFacade: SourceFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val state: StateFlow<HomeUiState>
    val effects: Flow<HomeEffect>

    fun updateSearchQuery(value: String)
    fun openSearch()
    fun closeSearch()
    fun clearSearch()
    fun refresh()
}
```

### `FilesScreen.kt`
- Internal area: `ui/files`
- Purpose: render browse results, selection controls, preferences dialog, and queue action.
- Responsibility: bind files-page state to the current visual table and dialog patterns.
- Depends on: `FilesViewModel`
- Must not depend on: queue stores, provider clients
- Visibility: `public`
- Key types/functions:

```kotlin
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit
)
```

### `FilesViewModel.kt`
- Internal area: `ui/files`
- Purpose: own screen-local browse state and build queue-intake commands.
- Responsibility: request browse results once per route unless user retries, preserve local state across recreation, and normalize file-preference toggles before enqueue.
- Depends on: `SourceFacade`, `DownloadsFacade`
- Must not depend on: queue stores, output services, remote ZIP transport classes
- Visibility: `public`
- Key types/functions:

```kotlin
data class FilesRouteArgs(
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId,
    val entryDisplayName: String
)

data class FilesUiState(
    val entryDisplayName: String,
    val mode: FilesMode,
    val isResolving: Boolean,
    val preparing: FilesPreparingState?,
    val rows: List<SelectableRowModel>,
    val selectedIds: Set<SelectableItemId>,
    val preferences: FilePreferencesState,
    val searchQuery: String,
    val resolverError: String?,
    val listAnchor: Int
)

enum class FilesMode {
    STANDARD,
    ARCHIVE_SELECTION
}

data class SelectableRowModel(
    val itemId: SelectableItemId,
    val originalDisplayName: String,
    val sizeLabel: String,
    val partLabel: String?
)

data class FilesPreparingState(
    val statusLabel: String?,
    val progressPercent: Double?,
    val timeoutAtEpochMillis: Long
)

class FilesViewModel(
    private val routeArgs: FilesRouteArgs,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val state: StateFlow<FilesUiState>

    fun retryResolve()
    fun updateSearchQuery(value: String)
    fun toggleSelection(itemId: SelectableItemId)
    fun selectAllVisible()
    fun clearSelection()
    fun updatePreferences(preferences: FilePreferencesState)
    suspend fun queueSelected(): EnqueueResult
}
```

### `FilePreferencesState.kt`
- Internal area: `ui/files`
- Purpose: make the page-local rename and unarchive choices explicit.
- Responsibility: normalize recursive extraction to `false` whenever `unarchive` is off while preserving the source-defined layout policy for queueing.
- Depends on: Kotlin stdlib only
- Must not depend on: source stores or queue stores
- Visibility: `internal`
- Key types/functions:

```kotlin
data class FilePreferencesState(
    val renameAvailable: Boolean,
    val applyRename: Boolean,
    val unarchivePolicy: ExtractionLayoutPolicy?,
    val unarchiveEnabled: Boolean,
    val recursiveUnarchiveEnabled: Boolean
) {
    fun normalized(): FilePreferencesState
}
```

### `DownloadsScreen.kt`
- Internal area: `ui/downloads`
- Purpose: render the durable queue projection in the current downloads-page layout.
- Responsibility: show newest-first rows, detail dialog, and clear-history confirmation.
- Depends on: `DownloadsViewModel`
- Must not depend on: queue stores or notification APIs
- Visibility: `public`
- Key types/functions:

```kotlin
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel
)
```

### `DownloadsViewModel.kt`
- Internal area: `ui/downloads`
- Purpose: layer screen-local dialog state over the owner-backed queue projection.
- Responsibility: forward owner-backed canonical row actions, keep clear-history confirmation local, and keep detail-dialog state local.
- Depends on: `DownloadsFacade`
- Must not depend on: ledger stores or output services
- Visibility: `public`
- Key types/functions:

```kotlin
data class DownloadsUiState(
    val projection: DownloadsProjection,
    val detailTaskId: TaskId?,
    val clearHistoryDialog: ClearHistoryDialogState?
)

data class ClearHistoryDialogState(
    val includeFailed: Boolean
)

class DownloadsViewModel(
    private val downloadsFacade: DownloadsFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val state: StateFlow<DownloadsUiState>

    fun performAction(command: QueueActionCommand)
    fun openDetails(taskId: TaskId)
    fun dismissDetails()
    fun openClearHistory()
    fun dismissClearHistory()
    fun confirmClearHistory(includeFailed: Boolean)
}
```

### `SettingsScreen.kt`
- Internal area: `ui/settings`
- Purpose: render editable settings sections and diagnostics controls.
- Responsibility: keep the current settings layout while reflecting field-specific locks correctly, use a discrete slider for concurrency, and expose compact diagnostics actions.
- Depends on: `SettingsViewModel`
- Must not depend on: stores or raw platform APIs
- Visibility: `public`
- Key types/functions:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onPersistSourceGrant: suspend (Uri) -> Result<PersistedUriGrant>,
    onPersistOutputGrant: suspend (Uri) -> Result<PersistedUriGrant>
)
```

### `SettingsViewModel.kt`
- Internal area: `ui/settings`
- Purpose: combine owner-backed settings state with live shell-readiness and active-download state.
- Responsibility: keep only the broken field editable while active downloads continue, and keep unaffected fields locked.
- Depends on: `StateFlow<ShellReadiness>`, `RealDebridFacade`, `SourceFacade`, `DownloadsFacade`, `DiagnosticsFacade`
- Must not depend on: credential vault handles, diagnostics artifacts, queue stores
- Visibility: `public`
- Key types/functions:

```kotlin
data class SettingsFieldLockState(
    val tokenEditable: Boolean,
    val sourceEditable: Boolean,
    val downloadDirectoryEditable: Boolean,
    val concurrencyEditable: Boolean
)

data class SettingsUiState(
    val maskedToken: String,
    val sourceSummary: AcceptedSourceSummary?,
    val downloadSettings: DownloadSettingsState,
    val diagnosticsSettings: DiagnosticsSettings,
    val lockState: SettingsFieldLockState,
    val feedbackMessage: String?
)

class SettingsViewModel(
    private val shellReadiness: StateFlow<ShellReadiness>,
    private val realDebridFacade: RealDebridFacade,
    private val sourceFacade: SourceFacade,
    private val downloadsFacade: DownloadsFacade,
    private val diagnosticsFacade: DiagnosticsFacade,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val state: StateFlow<SettingsUiState>

    fun saveApiKey(candidate: String)
    fun saveSource(command: AcceptSourceCommand)
    fun saveDownloadSettings(draft: DownloadSettingsDraft)
    fun setDiagnosticsEnabled(enabled: Boolean)
    fun clearDiagnostics()
    fun exportDiagnostics(destinationUri: Uri, targetLabel: String)
}
```

## Key Flows

1. Setup flow:
   - `SetupViewModel` holds local drafts and picked URIs.
   - URI-grant capture stays app-owned and is invoked through callbacks.
   - On successful `SetupSubmissionCoordinator.submit`, `SetupViewModel` emits `SetupEffect.Completed`.
   - `ShellScaffold` calls the app-owned `onSetupCompleted` callback so the startup session transitions from `Setup` to `Shell` without process recreation.

2. Home invalid-settings recovery:
   - `HomeViewModel` combines `ShellReadiness` and `HomeSourceState`.
   - If Settings repairs a broken token, source grant, or output directory, the live readiness flow updates.
   - Home leaves the invalid-settings mode without requiring a new startup route.
   - Home warning presentation stays typed so retained-prior warning and missing-snapshot fallback are not collapsed into one string field.
   - Manual refresh emits one `HomeEffect.RefreshFeedback` for toast presentation without turning transient feedback into persistent page state.

3. Files browse and queue:
   - `FilesViewModel` resolves browse items once per route unless the user taps `Retry`.
   - Search, selection, and preferences remain screen-local.
   - `queueSelected()` builds `QueueTaskInput` from `SelectableItem` identity plus normalized file preferences.
   - Successful enqueue routes to Downloads.

4. Downloads screen:
   - `DownloadsViewModel` observes the durable `DownloadsProjection`.
   - Detail dialog and clear-history confirmation are screen-local only.
   - The queue list shows rows in the order already projected by `downloads/`; UI must not resort them independently.

5. Settings field locking:
   - With no active downloads, all fields remain editable.
   - With active downloads and no broken setting, all product settings lock.
   - With active downloads and one broken saved setting, only the affected field remains editable and all unaffected fields stay locked.

6. Configuration change preservation:
   - Every screen ViewModel is lifecycle-aware and backed by saved state where needed.
   - `ui/` must not rely on `remember { ViewModel(...) }` for state that must survive configuration change.

## Failure and Recovery Rules

- `ui/` must not invent durable fallback state when owner flows fail.
- Resolver failure on Files stays on the page with visible `Retry`.
- Failed settings saves keep prior valid values intact and show corrective feedback.
- Setup success or failure must be represented through explicit effects or owner-backed state, not inferred from recomposition timing.
- UI migration work should preserve the current app's visual baseline unless a behavior or boundary rule requires a focused change.

## Testing Plan

### `SetupViewModelTest.kt`

- Scope: unit
- Covers:
  - local validation for missing required setup fields
  - persisted-grant acceptance into local state
  - successful submit emits `Completed`
  - failed submit leaves setup visible with error feedback
- Fixtures:
  - fake `SetupSubmissionCoordinator`
  - `SavedStateHandle`

### `HomeViewModelTest.kt`

- Scope: unit
- Covers:
  - invalid-settings mode from live readiness
  - source-load error mode
  - retained-prior warning and missing-snapshot fallback remain distinct typed warnings
  - refresh visibility is true only for URL-backed home states
  - manual refresh emits success and failure feedback effects
  - recovery after broken setting is fixed without recreating the view model
- Fixtures:
  - fake `SourceFacade`
  - mutable `ShellReadiness` flow
  - `SavedStateHandle`

### `FilesViewModelTest.kt`

- Scope: unit
- Covers:
  - standard-mode browse load
  - archive-selection browse load
  - select-all and select-none operate on visible rows only
  - recursive-unarchive normalization when unarchive is off
  - successful enqueue uses selected item identity and current preferences
- Fixtures:
  - fake `SourceFacade`
  - fake `DownloadsFacade`
  - selectable-item fixtures
  - `SavedStateHandle`

### `DownloadsViewModelTest.kt`

- Scope: unit
- Covers:
  - queue projection binding
  - detail-dialog open and dismiss
  - clear-history confirmation flow
  - queue-action forwarding
- Fixtures:
  - fake `DownloadsFacade`
  - `SavedStateHandle`

### `SettingsViewModelTest.kt`

- Scope: unit
- Covers:
  - all fields editable when no downloads are active
  - only broken field editable during active downloads
  - token, source, and download-settings save routing
  - diagnostics controls remain available while downloads are active
- Fixtures:
  - mutable `ShellReadiness` flow
  - fake owner facades
  - `SavedStateHandle`

### `FilesStateRestorationIntegrationTest.kt`

- Scope: integration
- Covers:
  - configuration change preserves search query
  - configuration change preserves selection
  - configuration change preserves file preferences
  - configuration change does not trigger a second browse load automatically
- Fixtures:
  - Compose test rule
  - fake `FilesViewModel`

### `ShellScaffoldIntegrationTest.kt`

- Scope: integration
- Covers:
  - startup session state renders only setup until bootstrap publishes a shell route
  - startup shell route renders the requested tab after the one-shot bootstrap completes
  - setup completion callback enters shell without activity recreation
  - route changes preserve current shell destination across configuration change
- Fixtures:
  - Compose test rule
  - fake `ShellNavigator`
  - fake view models

## Open Questions or Deferred Decisions

None currently.
