# `diagnostics/` Package Architecture

## Purpose

`diagnostics/` owns diagnostics enablement, append-only event capture, retained internal diagnostics artifacts, exported diagnostics bundles, and clear or export actions. It is a sink and reporting surface only. Product packages may emit events into it, but no product decision may depend on diagnostics content.

This package doc stays structural. The durable parts are:
- enablement is immediate and persisted,
- event capture is append-only while enabled,
- retention is bounded to 25 MB internally,
- export retains the latest 3 bundles,
- clear and export must never partially report success,
- diagnostics failure must not block product behavior.

## Owned Responsibilities

- Persist and expose the diagnostics-enabled setting.
- Accept typed diagnostics events from all supported domains.
- Append diagnostics events and failure records while diagnostics is enabled.
- Maintain `manifest.json`, `timeline.jsonl`, `failures.jsonl`, and `summary.json`.
- Rotate retained artifacts oldest-first once storage exceeds the limit.
- Build timestamped export bundles in app-specific external storage.
- Clear internal diagnostics artifacts and exported bundles through one diagnostics-owned action.

## Explicit Non-Responsibilities

- `diagnostics/` does not own queue state, source state, or settings state outside diagnostics enablement.
- `diagnostics/` does not drive retries, routing, or UI lock decisions.
- `diagnostics/` does not own notification delivery.
- `diagnostics/` does not persist plaintext credentials or raw sensitive URLs.

## Authorities and Owned Records

- `DiagnosticsSettings`
  - Persisted enabled or disabled state.
- `DiagnosticEvent`
  - Common sanitized event envelope for all product domains.
- `DiagnosticsManifest`
  - Support metadata for retained diagnostics.
- `DiagnosticsSummary`
  - Rolling support summary derived from appended events.
- `DiagnosticsArtifactSnapshot`
  - Current internal artifact set.
- `DiagnosticsExportBundle`
  - Timestamped exported bundle descriptor.
- `StagedInternalClear` and `StagedExportClear`
  - Diagnostics-owned staged-delete plans that allow clear to commit both internal and exported cleanup together or roll both back.

## Dependencies

- Inbound dependencies:
  - `ui/settings` for enable, clear, and export actions.
  - all product packages for best-effort `emit` calls.
- Outbound dependencies:
  - shared `ConfigStore` for diagnostics settings,
  - internal diagnostics filesystem,
  - app-specific external diagnostics export filesystem.
- What may cross the root-package boundary:
  - `DiagnosticsSettings`,
  - `DiagnosticEvent`,
  - `DiagnosticsExportResult`,
  - `DiagnosticsClearResult`.
- What may not cross the root-package boundary:
  - internal artifact files,
  - redaction internals,
  - callbacks that influence product flow.

## Public Entry Points

- `DiagnosticsFacade.observeSettings(): StateFlow<DiagnosticsSettings>`
- `DiagnosticsFacade.setEnabled(enabled: Boolean): Result<Unit>`
- `DiagnosticsFacade.emit(event: DiagnosticEvent): Result<Unit>`
- `DiagnosticsFacade.export(): DiagnosticsExportResult`
- `DiagnosticsFacade.clear(): DiagnosticsClearResult`

## Internal Structure

### `diagnostics/settings`

- Purpose: own persisted enablement.
- What it owns:
  - enabled or disabled state,
  - hydration of diagnostics settings from storage.
- What it must not own:
  - artifact writes,
  - export bundles.
- Sibling interaction:
  - `diagnostics/events` checks the live enabled state before appending.
- What may cross this seam:
  - `DiagnosticsSettings`.
- What may not cross this seam:
  - event files.

### `diagnostics/events`

- Purpose: own append-only event intake.
- What it owns:
  - best-effort `emit`,
  - redaction before persistence,
  - domain-safe event envelopes.
- What it must not own:
  - product callbacks,
  - retention policy decisions.
- Sibling interaction:
  - appends into `diagnostics/store`,
  - updates rolling summary models.
- What may cross this seam:
  - `DiagnosticEvent`.
- What may not cross this seam:
  - product decisions.

### `diagnostics/store`

- Purpose: own retained internal diagnostics artifacts.
- What it owns:
  - manifest file,
  - timeline file,
  - failures file,
  - summary file,
  - oldest-first artifact rotation.
- What it must not own:
  - export bundle retention,
  - settings UI.
- Sibling interaction:
  - `diagnostics/events` appends here,
  - `diagnostics/export` reads current artifacts here.
- What may cross this seam:
  - `DiagnosticsArtifactSnapshot`.
- What may not cross this seam:
  - queue or source state mutations.

### `diagnostics/export`

- Purpose: own export-bundle creation, export retention, and clear-or-export user actions.
- What it owns:
  - zip-bundle construction,
  - external bundle retention,
  - clear coordination over internal and exported artifacts.
- What it must not own:
  - event append logic,
  - product routing.
- Sibling interaction:
  - reads `diagnostics/store`,
  - consults `diagnostics/settings`.
- What may cross this seam:
  - export or clear results.
- What may not cross this seam:
  - product decisions.

## Internal Files

### `DiagnosticsFacade.kt`
- Internal area: `diagnostics/root`
- Purpose: expose the diagnostics package API.
- Responsibility: delegate to settings, sink, store, and export services.
- Depends on: diagnostics package services only
- Must not depend on: product stores
- Visibility: `public`
- Key types/functions:

```kotlin
class DiagnosticsFacade(
    private val settingsService: DiagnosticsSettingsService,
    private val sink: DiagnosticsSink,
    private val exportService: DiagnosticsExportService
) {
    fun observeSettings(): StateFlow<DiagnosticsSettings>
    suspend fun setEnabled(enabled: Boolean): Result<Unit>
    suspend fun emit(event: DiagnosticEvent): Result<Unit>
    suspend fun export(): DiagnosticsExportResult
    suspend fun clear(): DiagnosticsClearResult
}
```

### `DiagnosticsSettingsStore.kt`
- Internal area: `diagnostics/settings`
- Purpose: own the persisted diagnostics setting in shared `ConfigStore`.
- Responsibility: hydrate `DiagnosticsSettings` from storage on startup.
- Depends on: shared `ConfigStore`
- Must not depend on: artifact files
- Visibility: `public`
- Key types/functions:

```kotlin
data class DiagnosticsSettings(
    val enabled: Boolean
)

interface DiagnosticsSettingsStore {
    suspend fun read(): DiagnosticsSettings?
    suspend fun write(settings: DiagnosticsSettings): Result<Unit>
}
```

### `DiagnosticsSettingsService.kt`
- Internal area: `diagnostics/settings`
- Purpose: expose the live diagnostics-enabled state.
- Responsibility: keep `observeSettings()` storage-backed instead of defaulting to speculative UI state.
- Depends on: `DiagnosticsSettingsStore`
- Must not depend on: artifact files
- Visibility: `internal`
- Key types/functions:

```kotlin
class DiagnosticsSettingsService(
    private val store: DiagnosticsSettingsStore
) {
    fun observe(): StateFlow<DiagnosticsSettings>
    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}
```

### `DiagnosticEventModels.kt`
- Internal area: `diagnostics/events`
- Purpose: define the common diagnostics envelope and support records.
- Responsibility: keep diagnostics domain naming and redaction boundaries explicit.
- Depends on: Kotlin stdlib only
- Must not depend on: product stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
enum class DiagnosticDomain {
    APP_SHELL,
    SOURCE,
    HOME,
    FILES,
    ARCHIVE_SELECTION,
    DOWNLOADS,
    SETTINGS,
    NOTIFICATIONS
}

data class DiagnosticEvent(
    val timestamp: Instant,
    val sessionId: String,
    val domain: DiagnosticDomain,
    val event: String,
    val outcome: String,
    val taskId: String?,
    val snapshotId: String?,
    val context: Map<String, String>
)

data class DiagnosticsManifest(
    val contractVersion: Int,
    val appVersion: String,
    val buildNumber: String,
    val androidVersion: String,
    val deviceModel: String,
    val sessionSeed: String,
    val redactionPolicyVersion: Int,
    val exportedAt: Instant?
)

data class DiagnosticsSummary(
    val eventCountsByDomain: Map<DiagnosticDomain, Int>,
    val failureCountsByDomain: Map<DiagnosticDomain, Int>,
    val latestQueueSummary: DiagnosticsQueueSummary?,
    val latestSourceRefreshOutcome: String?,
    val latestFailureByDomain: Map<DiagnosticDomain, String>
)

data class DiagnosticsQueueSummary(
    val completed: Int,
    val total: Int,
    val failed: Int,
    val cancelled: Int
)
```

### `DiagnosticsSink.kt`
- Internal area: `diagnostics/events`
- Purpose: best-effort diagnostics event intake.
- Responsibility: redact sensitive data, append events only when enabled, and never block primary product flow.
- Depends on: `DiagnosticsSettingsService`, `DiagnosticsStore`, `SummaryProjector`
- Must not depend on: product callbacks
- Visibility: `internal`
- Key types/functions:

```kotlin
class DiagnosticsSink(
    private val settingsService: DiagnosticsSettingsService,
    private val diagnosticsStore: DiagnosticsStore,
    private val summaryProjector: SummaryProjector,
    private val redactor: DiagnosticsRedactor
) {
    suspend fun emit(event: DiagnosticEvent): Result<Unit> {
        // Contract:
        // - if diagnostics is disabled, do nothing successfully
        // - if diagnostics write fails, return success after best-effort handling
        // - diagnostics failure must not block the caller's product flow
    }
}
```

### `DiagnosticsStore.kt`
- Internal area: `diagnostics/store`
- Purpose: own retained internal artifact files.
- Responsibility: append timeline and failure events, rewrite manifest and summary, and expose the current artifact snapshot.
- Depends on: internal filesystem boundary
- Must not depend on: UI classes
- Visibility: `internal`
- Key types/functions:

```kotlin
data class DiagnosticsArtifactSnapshot(
    val manifest: Path,
    val timeline: Path,
    val failures: Path,
    val summary: Path,
    val totalBytes: Long
)

data class StagedInternalClear(
    val backupRoot: Path
)

data class DiagnosticsExportBundle(
    val bundlePath: Path,
    val createdAt: Instant
)

class DiagnosticsStore(
    private val filesystem: DiagnosticsFilesystem
) {
    suspend fun appendTimeline(event: DiagnosticEvent): Result<Unit>
    suspend fun appendFailure(event: DiagnosticEvent): Result<Unit>
    suspend fun writeManifest(manifest: DiagnosticsManifest): Result<Unit>
    suspend fun writeSummary(summary: DiagnosticsSummary): Result<Unit>
    suspend fun snapshot(): Result<DiagnosticsArtifactSnapshot>
    suspend fun stageInternalClear(): Result<StagedInternalClear>
    suspend fun commitInternalClear(staged: StagedInternalClear): Result<Unit>
    suspend fun rollbackInternalClear(staged: StagedInternalClear): Result<Unit>
}
```

### `SummaryProjector.kt`
- Internal area: `diagnostics/store`
- Purpose: keep `summary.json` current from appended events.
- Responsibility: update counts and latest-failure facts without becoming a product-state authority.
- Depends on: `DiagnosticsStore`
- Must not depend on: product stores as a source of truth
- Visibility: `internal`
- Key types/functions:

```kotlin
class SummaryProjector(
    private val diagnosticsStore: DiagnosticsStore
) {
    suspend fun apply(event: DiagnosticEvent): Result<Unit>
}
```

### `RetentionRotator.kt`
- Internal area: `diagnostics/store`
- Purpose: enforce bounded internal retention.
- Responsibility: trim oldest diagnostics artifacts when total size exceeds 25 MB.
- Depends on: `DiagnosticsStore`
- Must not depend on: export bundles
- Visibility: `internal`
- Key types/functions:

```kotlin
class RetentionRotator(
    private val diagnosticsStore: DiagnosticsStore,
    private val maxBytes: Long = 25L * 1024L * 1024L
) {
    suspend fun rotateIfNeeded(): Result<Unit>
}
```

### `DiagnosticsExportService.kt`
- Internal area: `diagnostics/export`
- Purpose: own export and clear user actions.
- Responsibility: build export bundles and coordinate staged clear semantics so failure never reports partial success.
- Depends on: `DiagnosticsStore`, `DiagnosticsBundleWriter`
- Must not depend on: product stores
- Visibility: `internal`
- Key types/functions:

```kotlin
sealed interface DiagnosticsExportResult {
    data class Exported(val bundlePath: Path) : DiagnosticsExportResult
    data class ExportedWithRetentionFailure(
        val bundlePath: Path,
        val message: String
    ) : DiagnosticsExportResult
    data class Failed(val message: String) : DiagnosticsExportResult
}

sealed interface DiagnosticsClearResult {
    data object Cleared : DiagnosticsClearResult
    data class Failed(val message: String) : DiagnosticsClearResult
}

class DiagnosticsExportService(
    private val diagnosticsStore: DiagnosticsStore,
    private val bundleWriter: DiagnosticsBundleWriter
) {
    suspend fun exportCurrent(): DiagnosticsExportResult
    suspend fun clearAll(): DiagnosticsClearResult
}
```

### `DiagnosticsBundleWriter.kt`
- Internal area: `diagnostics/export`
- Purpose: write and retain exported diagnostics bundles.
- Responsibility: create timestamped zip bundles and keep only the latest 3.
- Depends on: export filesystem boundary
- Must not depend on: product stores
- Visibility: `internal`
- Key types/functions:

```kotlin
class DiagnosticsBundleWriter(
    private val exportFilesystem: DiagnosticsExportFilesystem
) {
    suspend fun write(snapshot: DiagnosticsArtifactSnapshot): Result<Path>
    suspend fun retainLatest(limit: Int = 3): Result<Unit>
    suspend fun stageExportClear(): Result<StagedExportClear>
    suspend fun commitExportClear(staged: StagedExportClear): Result<Unit>
    suspend fun rollbackExportClear(staged: StagedExportClear): Result<Unit>
}

data class StagedExportClear(
    val backupRoot: Path
)
```

## Key Flows

1. Enable or disable diagnostics:
   - `DiagnosticsSettingsService` persists the new setting immediately.
   - `DiagnosticsSink` consults the live setting before appending.

2. Event capture:
   - Product packages call `DiagnosticsFacade.emit(event)`.
   - `DiagnosticsSink` redacts the event.
   - If diagnostics is enabled, it appends to `timeline.jsonl` and to `failures.jsonl` when the event is a failure.
   - `SummaryProjector` updates `summary.json`.

3. Retention:
   - `RetentionRotator` checks total retained bytes after append.
   - When internal artifacts exceed 25 MB, oldest retained diagnostics content is rotated out first.

4. Export:
   - `DiagnosticsExportService.exportCurrent()` snapshots the current artifact set.
   - `DiagnosticsBundleWriter` creates a timestamped zip in app-specific external export storage.
   - Export retention trims to the latest 3 bundles.
   - If the bundle is created but retention cleanup fails, the newest bundle remains available and the result reports success with cleanup failure.

5. Clear:
   - `DiagnosticsExportService.clearAll()` stages internal-artifact clear and export-bundle clear before committing either.
   - Commit happens only after both staged-delete plans succeed.
   - If one commit fails, diagnostics rolls both sides back and reports failure.

## Failure and Recovery Rules

- Diagnostics write failures must not crash or block product flows.
- While diagnostics is disabled, no events are appended.
- Clear and export must never surface partial success to the UI.
- Failed clear leaves both internal diagnostics artifacts and exported bundles unchanged.
- Export may succeed while retention cleanup fails; that is reported explicitly without hiding the newest bundle.
- Diagnostics content must be redacted before persistence or export.
- Diagnostics is never a source of product truth.

## Testing Plan

### `DiagnosticsSettingsServiceTest.kt`

- Scope: unit
- Covers:
  - settings hydration from storage
  - enable and disable persistence
- Fixtures:
  - fake `DiagnosticsSettingsStore`

### `DiagnosticsSinkTest.kt`

- Scope: unit
- Covers:
  - disabled diagnostics drops events without failure
  - enabled diagnostics appends timeline events
  - failure events also append to failures log
  - write failure does not block caller success
- Fixtures:
  - fake `DiagnosticsSettingsService`
  - fake `DiagnosticsStore`
  - fake `SummaryProjector`
  - fake redactor

### `RetentionRotatorTest.kt`

- Scope: unit
- Covers:
  - no-op under size limit
  - oldest-first rotation above size limit
- Fixtures:
  - fake `DiagnosticsStore`

### `DiagnosticsBundleWriterTest.kt`

- Scope: unit
- Covers:
  - timestamped bundle creation
  - latest-3 retention
  - staged export clear commit and rollback
- Fixtures:
  - temp export filesystem

### `DiagnosticsExportServiceTest.kt`

- Scope: unit
- Covers:
  - export success
  - export success with retention cleanup failure reports `ExportedWithRetentionFailure`
  - export failure does not report success
  - clear success
  - clear failure rolls internal and exported artifacts back without reporting success
- Fixtures:
  - fake `DiagnosticsStore`
  - fake `DiagnosticsBundleWriter`

### `DiagnosticsIntegrationTest.kt`

- Scope: integration
- Covers:
  - manifest, timeline, failures, and summary files are created
  - export bundle contains current diagnostics artifacts
  - clear removes internal artifacts and exported bundles only after staged clear commit succeeds
- Fixtures:
  - temp internal filesystem
  - temp export filesystem

## Open Questions or Deferred Decisions

None currently.
