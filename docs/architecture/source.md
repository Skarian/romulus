# `source/` Package Architecture

## Purpose

`source/` owns accepted-source configuration, source-document validation, snapshot publication, and browse preparation. It is the only package allowed to turn raw source input into an active `SourceSnapshot`, and it is the only package allowed to turn one snapshot entry into selectable browse rows for `ui/files`.

This package remains more concrete than `app/` or `ui/` because it owns hard correctness rules:
- failed source updates must not commit the attempted source change,
- snapshot replacement is atomic from the reader's point of view through one active-pair pointer,
- queued downloads bind to immutable snapshot and selected-item identity captured at enqueue time,
- archive-selection mode must never fall back to standard browse.

## Owned Responsibilities

- Accept URL or local-file source input from setup and settings.
- Parse and validate `version: 1` source documents.
- Persist the active accepted-source record.
- Stage and publish the latest active snapshot.
- Expose live source-readiness and home-state projections.
- Prepare standard browse rows from locally cached browse inventory keyed by snapshot entry, filling that cache through temporary Real-Debrid enumeration when needed.
- Prepare archive-selection browse rows from exact `.zip` paths through `remotezip/`.
- Apply path scope first and ignore rules second before rows reach `ui/files`.

## Explicit Non-Responsibilities

- `source/` does not own API-token validation or storage.
- `source/` does not own queue rows, retry policy, or worker scheduling.
- `source/` does not own remote ZIP copy execution.
- `source/` does not own output reservation, naming collisions, or unarchive.
- `source/` does not capture URI grants; it consumes persisted grant descriptors from `app/`.

## Authorities and Owned Records

- `AcceptedSourceConfigRecord`
  - Canonical active source configuration.
  - Includes source mode, active input value, persisted URI reference when applicable, and last-refresh metadata.
  - Staged candidates may exist internally, but they must remain invisible until snapshot activation succeeds.
- `SourceActivationRecord`
  - Canonical active pointer that makes one staged config and one staged snapshot visible together.
- `SourceDocument`
  - Parsed source input before acceptance.
- `SourceValidationIssue`
  - Canonical validation rejection reasons.
- `SourceSnapshot`
  - Immutable accepted snapshot with stable `snapshotId`.
- `SourceEntryId`
  - Assigned only by `source/snapshot`.
- `SelectableItem.StandardFile`
  - Standard browse identity assigned only by `source/browse`.
- `BrowseFailure`
  - Stage-labeled browse failure for `ui/files`.
- `SourceReadiness`
  - Live source-owned readiness for startup and settings repair flows.
- `HomeSourceWarning`
  - Typed warning surface for retained-prior refresh failure versus missing-snapshot fallback.

## Dependencies

- Inbound dependencies:
  - `app/` for startup readiness and cold-launch refresh.
  - `ui/setup` and `ui/settings` for acceptance commands.
  - `ui/home` for home-state observation and refresh.
  - `ui/files` for browse requests.
- Outbound dependencies:
- `realdebrid/` for standard browse cache fills, exact `.zip` file matching, and resumable outer-container preparation during archive-selection mode.
  - `remotezip/` for remote ZIP enumeration.
  - `diagnostics/` for accept, refresh, and browse events.
- What may cross the root-package boundary:
  - `AcceptSourceCommand`,
  - `AcceptedSourceSummary`,
  - `HomeSourceState`,
  - `BrowseRequest`,
  - `BrowseResult`,
  - `SourceReadiness`.
- What may not cross the root-package boundary:
  - raw stores,
  - raw Real-Debrid API models,
  - raw remote ZIP streams,
  - queue mutations.

## Public Entry Points

- `SourceFacade.accept(command: AcceptSourceCommand): AcceptSourceResult`
- `SourceFacade.refresh(trigger: SourceRefreshTrigger): SourceRefreshResult`
- `SourceFacade.observeHomeState(): StateFlow<HomeSourceState>`
- `SourceFacade.observeReadiness(): StateFlow<SourceReadiness>`
- `SourceFacade.readStartupReadiness(): SourceReadiness`
- `SourceFacade.observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?>`
- `SourceFacade.browse(request: BrowseRequest): BrowseResult`

## Internal Structure

### `source/ingest`

- Purpose: turn raw URL or local-file input into a validated candidate source update.
- What it owns:
  - loading raw bytes,
  - parsing JSON,
  - runtime validation,
  - staged commit or rollback of the active accepted-source record.
- What it must not own:
  - browse-item identity,
  - remote ZIP enumeration,
  - queue creation.
- Sibling interaction:
  - asks `source/snapshot` to stage and activate a candidate snapshot.
- What may cross this seam:
  - `SourceDocument`,
  - `AcceptSourceCommand`,
  - `AcceptSourceResult`.
- What may not cross this seam:
  - raw UI drafts,
  - browse rows,
  - queue metadata.

### `source/snapshot`

- Purpose: own snapshot staging, activation, and home-state projection.
- What it owns:
  - `snapshotId` assignment,
  - staged snapshot persistence,
  - the active config-plus-snapshot pointer,
  - refresh replacement rules,
  - stale-source and source-load error projections.
- What it must not own:
  - source-document validation,
  - browse enumeration,
  - queue binding.
- Sibling interaction:
  - receives validated documents from `source/ingest`,
  - feeds immutable snapshot data into `source/browse`.
- What may cross this seam:
  - immutable `SourceSnapshot`,
  - `HomeSourceState`,
  - `SourceRefreshResult`.
- What may not cross this seam:
  - mutable browse selection,
  - remote ZIP sessions,
  - queue actions.

### `source/browse`

- Purpose: prepare selectable rows for the Files page.
- What it owns:
  - standard browse enumeration,
  - archive-selection enumeration,
  - standard-file identity assignment,
  - path and ignore filtering.
- What it must not own:
  - snapshot persistence,
  - queue creation,
  - output writes.
- Sibling interaction:
  - consumes active snapshot entries from `source/snapshot`,
  - uses cached standard browse inventory for standard mode,
  - uses `realdebrid/` and `remotezip/` for archive-selection mode.
- What may cross this seam:
  - `SelectableItem`,
  - `BrowseMode`,
  - `BrowseFailure`.
- What may not cross this seam:
  - transport clients,
  - queue stores,
  - output reservations.

### `source/torrentmeta`

- Purpose: own torrent-native standard-file selection intent plus the cached standard browse inventory shape.
- What it owns:
  - torrent-native file records and selection-intent construction.
- What it must not own:
  - Real-Debrid request execution,
  - browse filtering,
  - queue creation.
- Sibling interaction:
  - feeds cached standard browse inventory into `source/browse`.
- What may cross this seam:
  - torrent metadata file records,
  - torrent-native selection intent.
- What may not cross this seam:
  - queue rows,
  - Real-Debrid models.

## Internal Files

### `SourceFacade.kt`
- Internal area: `source/root`
- Purpose: expose the only public surface for `app/` and `ui/`.
- Responsibility: delegate to ingest, snapshot, and browse without leaking internal stores.
- Depends on: `SourceAcceptanceService`, `SnapshotPublisher`, `BrowseService`
- Must not depend on: queue stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
class SourceFacade(
    private val acceptanceService: SourceAcceptanceService,
    private val snapshotPublisher: SnapshotPublisher,
    private val browseService: BrowseService
) {
    suspend fun accept(command: AcceptSourceCommand): AcceptSourceResult
    suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult
    fun observeHomeState(): StateFlow<HomeSourceState>
    fun observeReadiness(): StateFlow<SourceReadiness>
    suspend fun readStartupReadiness(): SourceReadiness
    fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?>
    suspend fun browse(request: BrowseRequest): BrowseResult
}
```

### `SourceConfigStore.kt`
- Internal area: `source/ingest`
- Purpose: own the staged accepted-source records in shared `ConfigStore`.
- Responsibility: persist only `AcceptedSourceConfigRecord`, keep staged candidates invisible until `source/snapshot` points at them, and hydrate active reads through the shared activation pointer.
- Depends on: shared `ConfigStore`
- Must not depend on: snapshot store, browse services
- Visibility: `public`
- Key types/functions:

```kotlin
enum class SourceMode {
    URL,
    FILE
}

data class AcceptedSourceConfigRecord(
    val mode: SourceMode,
    val rawValue: String,
    val persistedUri: String?,
    val acceptedAt: Instant,
    val lastRefreshAt: Instant?,
    val lastRefreshOutcome: SourceRefreshOutcome?
)

interface SourceConfigStore {
    suspend fun readStaged(stageId: String): AcceptedSourceConfigRecord?
    suspend fun stageCandidate(next: AcceptedSourceConfigRecord): Result<StagedSourceConfig>
    suspend fun discardCandidate(staged: StagedSourceConfig): Result<Unit>
}

data class StagedSourceConfig(
    val stageId: String,
    val acceptedAt: Instant
)
```

### `SourceActivationStore.kt`
- Internal area: `source/snapshot`
- Purpose: own the one active source pair pointer.
- Responsibility: make one staged config and one staged snapshot visible together by committing a single activation record.
- Depends on: durable pointer store boundary
- Must not depend on: browse services or provider clients
- Visibility: `public`
- Key types/functions:

```kotlin
@JvmInline
value class SourceActivationId(val value: String)

data class SourceActivationRecord(
    val activationId: SourceActivationId,
    val configStageId: String,
    val snapshotId: SnapshotId,
    val activatedAt: Instant
)

interface SourceActivationStore {
    suspend fun readActive(): SourceActivationRecord?
    suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord>
}
```

### `SourceDocumentParser.kt`
- Internal area: `source/ingest`
- Purpose: decode source JSON into app-owned models.
- Responsibility: load from URL or persisted local file, decode [`schema.json`](../schema.json) version `1`, and surface load or parse failures without mutating active state.
- Depends on: loader boundary, Kotlin serialization
- Must not depend on: snapshot store, queue models, remote ZIP
- Visibility: `internal`
- Key types/functions:

```kotlin
data class SourceTorrentDocument(
    val magnetUri: String,
    val partLabel: String?
)

data class IgnoreRulesDocument(
    val glob: List<String>
)

data class RenameRule(
    val pattern: String,
    val replacement: String
)

data class SourceEntryDocument(
    val displayName: String,
    val subfolder: String,
    val torrents: List<SourceTorrentDocument>,
    val path: String?,
    val ignore: IgnoreRulesDocument?,
    val rename: RenameRule?,
    val unarchive: Boolean?,
    val recursiveUnarchive: Boolean?
)

data class SourceDocument(
    val version: Int,
    val entries: List<SourceEntryDocument>
)

interface SourceLoader {
    suspend fun loadFromUrl(url: String): Result<ByteArray>
    suspend fun loadFromPersistedUri(uri: String): Result<ByteArray>
}

class SourceDocumentParser(
    private val sourceLoader: SourceLoader
) {
    suspend fun load(command: AcceptSourceCommand): Result<SourceDocument>
}
```

### `SourceValidation.kt`
- Internal area: `source/ingest`
- Purpose: centralize runtime validation rules that sharpen the schema.
- Responsibility: reject invalid path scope, ignore rules, rename regex, and recursive-unarchive invariants that sharpen [`schema.json`](../schema.json) before any source update becomes active.
- Depends on: `SourceDocumentParser.kt`
- Must not depend on: snapshot store or browse services
- Visibility: `internal`
- Key types/functions:

```kotlin
sealed interface SourceValidationIssue {
    data class InvalidVersion(val found: Int) : SourceValidationIssue
    data class InvalidSubfolder(val subfolder: String) : SourceValidationIssue
    data class InvalidPath(val path: String) : SourceValidationIssue
    data class InvalidIgnoreRule(val pattern: String) : SourceValidationIssue
    data class InvalidRenameRule(val message: String) : SourceValidationIssue
    data class InvalidRecursiveUnarchive(val message: String) : SourceValidationIssue
}

class SourceValidation {
    fun validate(document: SourceDocument): List<SourceValidationIssue> {
        // Contract-bearing rules:
        // - only version 1 is accepted
        // - path must normalize to root, directory scope, or exact `.zip`
        // - ignore globs must target basenames only
        // - rename regex must compile at acceptance time
        // - recursiveUnarchive requires unarchive=true
    }
}
```

### `SourcePathRules.kt`
- Internal area: `source/ingest`
- Purpose: keep path normalization and ignore matching consistent between validation and browse.
- Responsibility: normalize path values once and define the path-scope-then-ignore filtering rule.
- Depends on: Kotlin stdlib only
- Must not depend on: stores or provider clients
- Visibility: `internal`
- Key types/functions:

```kotlin
fun normalizePath(raw: String?): String?
fun ProviderFileRecord.isWithinScope(scope: String): Boolean
fun String.matchesIgnoreRules(ignoreGlobs: List<String>): Boolean
```

### `SourceAcceptanceService.kt`
- Internal area: `source/ingest`
- Purpose: enforce all-or-nothing source acceptance.
- Responsibility: load, validate, stage a candidate snapshot, stage the matching accepted-source config, and switch visibility through one shared activation pointer or discard both.
- Depends on: `SourceDocumentParser`, `SourceValidation`, `SourceConfigStore`, `SnapshotPublisher`
- Must not depend on: browse services or queue services
- Visibility: `internal`
- Key types/functions:

```kotlin
data class AcceptSourceCommand(
    val mode: SourceMode,
    val rawValue: String,
    val persistedUri: String?
)

sealed interface AcceptSourceResult {
    data class Accepted(val snapshotId: SnapshotId) : AcceptSourceResult
    data class Rejected(val issues: List<SourceValidationIssue>) : AcceptSourceResult
    data class Failed(val message: String) : AcceptSourceResult
}

class SourceAcceptanceService(
    private val parser: SourceDocumentParser,
    private val validation: SourceValidation,
    private val sourceConfigStore: SourceConfigStore,
    private val snapshotPublisher: SnapshotPublisher,
    private val clock: Clock
) {
    suspend fun accept(command: AcceptSourceCommand): AcceptSourceResult {
        val document = parser.load(command).getOrElse { return AcceptSourceResult.Failed(it.message ?: "Source load failed") }
        val issues = validation.validate(document)
        if (issues.isNotEmpty()) return AcceptSourceResult.Rejected(issues)

        val acceptedAt = clock.instant()
        val stagedSnapshot = snapshotPublisher.stageReplacement(document, acceptedAt = acceptedAt)
            .getOrElse { return AcceptSourceResult.Failed(it.message ?: "Snapshot staging failed") }

        val stagedConfig = sourceConfigStore.stageCandidate(
            AcceptedSourceConfigRecord(
                mode = command.mode,
                rawValue = command.rawValue,
                persistedUri = command.persistedUri,
                acceptedAt = acceptedAt,
                lastRefreshAt = null,
                lastRefreshOutcome = null
            )
        ).getOrElse {
            snapshotPublisher.discard(stagedSnapshot)
            return AcceptSourceResult.Failed("Accepted source record could not be staged")
        }

        val activation = snapshotPublisher.activate(
            stagedSnapshot = stagedSnapshot,
            stagedConfig = stagedConfig,
            activatedAt = acceptedAt
        )
        if (activation.isFailure) {
            // Contract: only the activation pointer makes the staged pair visible.
            val configCleanup = sourceConfigStore.discardCandidate(stagedConfig)
            val snapshotCleanup = snapshotPublisher.discard(stagedSnapshot)
            if (configCleanup.isFailure || snapshotCleanup.isFailure) {
                return AcceptSourceResult.Failed("Source activation failed and staged cleanup also failed")
            }
            return AcceptSourceResult.Failed("Source activation failed")
        }

        return AcceptSourceResult.Accepted(stagedSnapshot.snapshotId)
    }
}
```

### `SnapshotStore.kt`
- Internal area: `source/snapshot`
- Purpose: persist staged replacement snapshots.
- Responsibility: store immutable snapshots by `snapshotId` and leave visibility to the shared activation pointer.
- Depends on: filesystem or database boundary
- Must not depend on: provider clients or queue services
- Visibility: `public`
- Key types/functions:

```kotlin
@JvmInline
value class SnapshotId(val value: String)

@JvmInline
value class SourceEntryId(val value: String)

data class SourceTorrentRef(
    val magnetUri: String,
    val partLabel: String?
)

data class SourceSnapshotEntry(
    val entryId: SourceEntryId,
    val displayName: String,
    val subfolder: String,
    val torrents: List<SourceTorrentRef>,
    val normalizedPath: String,
    val ignoreGlobs: List<String>,
    val renameRule: RenameRule?,
    val unarchiveConfigured: Boolean,
    val unarchiveDefault: Boolean,
    val recursiveConfigured: Boolean,
    val recursiveUnarchiveDefault: Boolean
)

data class SourceSnapshot(
    val snapshotId: SnapshotId,
    val acceptedAt: Instant,
    val entries: List<SourceSnapshotEntry>
)

data class StagedSnapshot(
    val snapshotId: SnapshotId
)

interface SnapshotStore {
    suspend fun read(snapshotId: SnapshotId): SourceSnapshot?
    suspend fun writeStaged(snapshot: SourceSnapshot): Result<StagedSnapshot>
    suspend fun discard(staged: StagedSnapshot): Result<Unit>
}
```

### `SnapshotPublisher.kt`
- Internal area: `source/snapshot`
- Purpose: own snapshot staging, activation, refresh, and active-source projection.
- Responsibility: replace the active snapshot atomically or retain the prior usable snapshot, while keeping staged accepted-source config invisible until activation succeeds.
- Depends on: `SnapshotStore`, `SourceConfigStore`, `SourceActivationStore`, `SourceDocumentParser`, `SourceValidation`
- Must not depend on: browse services, queue services
- Visibility: `internal`
- Key types/functions:

```kotlin
sealed interface SourceRefreshTrigger {
    data object HomeManualRefresh : SourceRefreshTrigger
    data object ColdLaunch : SourceRefreshTrigger
    data object SettingsSave : SourceRefreshTrigger
}

sealed interface SourceRefreshResult {
    data class Replaced(val snapshotId: SnapshotId) : SourceRefreshResult
    data class RetainedPrior(val priorSnapshotId: SnapshotId, val message: String) : SourceRefreshResult
    data class FailedWithoutSnapshot(val message: String) : SourceRefreshResult
}

class SnapshotPublisher(
    private val snapshotStore: SnapshotStore,
    private val sourceConfigStore: SourceConfigStore,
    private val activationStore: SourceActivationStore,
    private val parser: SourceDocumentParser,
    private val validation: SourceValidation,
    private val clock: Clock
) {
    fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary>
    fun observeHomeState(): StateFlow<HomeSourceState>
    fun observeReadiness(): StateFlow<SourceReadiness>
    suspend fun readStartupReadiness(): SourceReadiness
    suspend fun stageReplacement(document: SourceDocument, acceptedAt: Instant): Result<StagedSnapshot>
    suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord>
    suspend fun discard(staged: StagedSnapshot): Result<Unit>

    suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult {
        // Contract-bearing rules:
        // - read the active activation record, then load the active config through its staged config id
        // - load and validate new source content
        // - on failure with prior snapshot: keep prior snapshot active and emit stale warning
        // - on failure without prior snapshot: emit source-load error
        // - on success: stage the replacement snapshot and matching config candidate
        // - commit one SourceActivationRecord that points at the staged pair
        // - on activation failure: discard staged snapshot and staged config, leaving the prior activation record visible
    }
}
```

### `SourceModels.kt`
- Internal area: `source/snapshot`
- Purpose: keep source-owned public models explicit.
- Responsibility: define accepted-source summary, home state, and readiness state.
- Depends on: Kotlin stdlib only
- Must not depend on: provider clients or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
data class AcceptedSourceSummary(
    val mode: SourceMode,
    val rawValue: String,
    val persistedUri: String?,
    val lastRefreshOutcome: SourceRefreshOutcome?
)

data class SourceReadiness(
    val acceptedMode: SourceMode?,
    val isUsable: Boolean,
    val hasUsableSnapshot: Boolean,
    val brokenReason: String?
)

sealed interface HomeSourceState {
    data class SourceLoadError(
        val message: String,
        val refreshAvailable: Boolean
    ) : HomeSourceState
    data class Content(
        val snapshotId: SnapshotId,
        val rows: List<HomeSourceRow>,
        val warning: HomeSourceWarning?,
        val refreshAvailable: Boolean
    ) : HomeSourceState
}

sealed interface HomeSourceWarning {
    data class LatestRefreshFailed(val message: String) : HomeSourceWarning
    data class MissingSnapshotFallback(val message: String) : HomeSourceWarning
}

data class HomeSourceRow(
    val entryId: SourceEntryId,
    val displayName: String,
    val folderContext: String
)

enum class SourceRefreshOutcome {
    ACCEPTED,
    RETAINED_PRIOR,
    FAILED_WITHOUT_SNAPSHOT
}
```

### `BrowseService.kt`
- Internal area: `source/browse`
- Purpose: load one snapshot entry and choose standard or archive-selection browse.
- Responsibility: reject missing snapshot-entry input and delegate to the correct builder.
- Depends on: `SnapshotStore`, `StandardBrowseBuilder`, `ArchiveBrowseBuilder`
- Must not depend on: queue services or output services
- Visibility: `internal`
- Key types/functions:

```kotlin
data class BrowseRequest(
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId
)

sealed interface BrowseResult {
    data class Loaded(val mode: BrowseMode, val items: List<SelectableItem>) : BrowseResult
    data class Failed(val failure: BrowseFailure) : BrowseResult
}

enum class BrowseMode {
    STANDARD,
    ARCHIVE_SELECTION
}

class BrowseService(
    private val snapshotStore: SnapshotStore,
    private val standardBrowseBuilder: StandardBrowseBuilder,
    private val archiveBrowseBuilder: ArchiveBrowseBuilder
) {
    suspend fun load(request: BrowseRequest): BrowseResult
}
```

### `StandardBrowseBuilder.kt`
- Internal area: `source/browse`
- Purpose: build standard browse rows from cached standard browse inventory.
- Responsibility: apply path scope first, ignore rules second, and assign stable item ids from source-entry id plus torrent-native selection intent.
- Depends on: `CachedStandardBrowseInventoryService`
- Must not depend on: remote ZIP or queue services
- Visibility: `internal`
- Key types/functions:

```kotlin
class StandardBrowseBuilder(
    private val enumerateTorrentMetadata: suspend (SnapshotId, SourceSnapshotEntry) -> Result<TorrentMetadataInventory>
) {
    suspend fun build(snapshotId: SnapshotId, entry: SourceSnapshotEntry): BrowseResult
}
```

### `CachedStandardBrowseInventoryService.kt`
- Internal area: `source/browse`
- Purpose: load standard browse inventory for one snapshot entry, using the local browse cache first and filling it through temporary Real-Debrid enumeration on miss.
- Responsibility: key cached browse inventory by `snapshotId` plus `entryId`, translate provider inventory into torrent-native selection intent, and keep browse filtering out of the fetch layer.
- Depends on: `StandardBrowseInventoryCacheStore`, `RealDebridFacade`
- Must not depend on: queue services or remote ZIP services
- Visibility: `internal`
- Key types/functions:

```kotlin
class CachedStandardBrowseInventoryService(
    private val cacheStore: StandardBrowseInventoryCacheStore,
    private val enumerateProviderFiles: suspend (ProviderInventoryRequest) -> Result<ProviderInventory>
) {
    suspend fun load(snapshotId: SnapshotId, entry: SourceSnapshotEntry): Result<TorrentMetadataInventory>
}
```

### `StandardBrowseInventoryCacheStore.kt`
- Internal area: `source/browse`
- Purpose: persist per-snapshot-entry standard browse inventory on-device.
- Responsibility: hydrate cached browse inventory without touching the provider and write new browse inventory after successful enumeration.
- Depends on: local app files boundary only
- Must not depend on: Real-Debrid HTTP or queue services
- Visibility: `internal`
- Key types/functions:

```kotlin
interface StandardBrowseInventoryCacheStore {
    suspend fun read(snapshotId: SnapshotId, entryId: SourceEntryId): TorrentMetadataInventory?
    suspend fun write(
        snapshotId: SnapshotId,
        entryId: SourceEntryId,
        inventory: TorrentMetadataInventory
    ): Result<Unit>
}
```

### `ArchiveBrowseBuilder.kt`
- Internal area: `source/browse`
- Purpose: build archive-selection rows for exact `.zip` paths.
- Responsibility: map the cached archive-browse service result into either archive-preparing UI state or ready archive-entry rows, apply ignore rules, preserve duplicate-safe archive-entry identity, and sort rows alphabetically by internal file name before they reach `ui/files`.
- Depends on: `CachedArchiveBrowseService`
- Must not depend on: queue services, output services, full-download fallback logic
- Visibility: `internal`
- Key types/functions:

```kotlin
class ArchiveBrowseBuilder(
    private val loadArchiveBrowse: suspend (SnapshotId, SourceSnapshotEntry) -> Result<ArchiveBrowseLoadResult>
) {
    suspend fun build(snapshotId: SnapshotId, entry: SourceSnapshotEntry): BrowseResult
}
```

### `SelectableItemModels.kt`
- Internal area: `source/browse`
- Purpose: define the exact browse-item identity and enqueue-time source context carried into downloads.
- Responsibility: keep standard-file and archive-entry identity immutable.
- Depends on: source models and public `remotezip/` or `realdebrid/` models
- Must not depend on: queue stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
@JvmInline
value class SelectableItemId(val value: String)

data class SelectionPolicy(
    val renameRule: RenameRule?,
    val renameAvailable: Boolean,
    val unarchiveToggleVisible: Boolean,
    val unarchiveDefault: Boolean,
    val recursiveToggleVisible: Boolean,
    val recursiveUnarchiveDefault: Boolean
)

data class SelectableItemSourceContext(
    val entryDisplayName: String,
    val outputSubfolder: String,
    val partLabel: String?,
    val providerFileId: String?
)

sealed interface SelectableItem {
    val itemId: SelectableItemId
    val snapshotId: SnapshotId
    val entryId: SourceEntryId
    val originalDisplayName: String
    val sizeBytes: Long?
    val selectionPolicy: SelectionPolicy
    val sourceContext: SelectableItemSourceContext

    data class StandardFile(
        override val itemId: SelectableItemId,
        override val snapshotId: SnapshotId,
        override val entryId: SourceEntryId,
        override val originalDisplayName: String,
        override val sizeBytes: Long?,
        override val selectionPolicy: SelectionPolicy,
        override val sourceContext: SelectableItemSourceContext,
        val selectionIntent: TorrentFileSelectionIntent
    ) : SelectableItem

    data class ArchiveEntry(
        override val itemId: SelectableItemId,
        override val snapshotId: SnapshotId,
        override val entryId: SourceEntryId,
        override val originalDisplayName: String,
        override val sizeBytes: Long?,
        override val selectionPolicy: SelectionPolicy,
        override val sourceContext: SelectableItemSourceContext,
        val preparationKey: ArchivePreparationKey,
        val archiveEntryIdentity: ArchiveEntryIdentity
    ) : SelectableItem
}

sealed interface BrowseFailure {
    data class MissingEntry(val entryId: SourceEntryId) : BrowseFailure
    data class StandardResolver(val message: String) : BrowseFailure
    data class ArchiveResolver(val message: String) : BrowseFailure
    data class ArchiveEnumeration(val message: String) : BrowseFailure
}
```

## Key Flows

1. Setup or settings source save:
   - `SourceAcceptanceService` loads the input.
   - `SourceValidation` rejects invalid source before any active state changes.
   - `SnapshotPublisher` stages a candidate snapshot.
   - `SourceConfigStore` stages the matching accepted-source config candidate.
   - `SourceActivationStore` commits one active pair pointer that references the staged config and staged snapshot together.
   - Until that activation succeeds, the staged config and staged snapshot are not visible as the active source.
   - If activation fails, the staged config and staged snapshot are discarded and the prior active source remains visible.

2. Cold-launch URL refresh:
   - `app/` calls `SourceFacade.refresh(SourceRefreshTrigger.ColdLaunch)`.
   - On success, a new snapshot replaces the active snapshot atomically.
   - On failure with a prior usable snapshot, Home keeps the prior snapshot and shows `HomeSourceWarning.LatestRefreshFailed`.
   - On failure without a usable snapshot, Home shows the source-load error state.

3. Standard browse:
   - `BrowseService` selects the entry from the active snapshot.
   - `StandardBrowseBuilder` asks the cached standard browse inventory service for that snapshot entry.
   - On cache miss, `source/` temporarily enumerates provider files through `realdebrid/`, stores the resulting browse inventory locally, and reuses it on later opens for the same snapshot entry.
   - Path scope is applied first.
   - Ignore rules are applied second.
   - Returned rows are sorted alphabetically by original file name.

4. Archive-selection browse:
   - `BrowseService` detects exact `.zip` path mode.
   - `ArchiveBrowseBuilder` asks the shared archive-container preparation service for that snapshot entry.
   - On first open, `source/` finds the exact outer `.zip` through `realdebrid/`, starts provider preparation for that one container, and persists the matched file plus resume marker locally.
   - While the outer `.zip` is still preparing, Files stays in archive-preparing state and revisits resume that same provider acquisition instead of adding the magnet again.
   - Once provider links are ready, the preparation service resolves one unrestricted outer-container URL, enumerates internal entries through `remotezip/`, and caches the ready result locally for that snapshot entry.
   - Ready archive-entry rows carry a shared preparation key rather than their own outer-container locator so later downloads reuse the same outer-ZIP acquisition.
   - Ignore rules apply before rows reach the UI.
   - Returned rows are sorted alphabetically by internal file name.
   - Failure stays in archive-selection failure state and never falls back to standard browse.

## Failure and Recovery Rules

- Failed source acceptance must leave the prior accepted-source record and prior active snapshot visible and untouched.
- Failed refresh must not replace the active snapshot unless the replacement snapshot was fully staged and activated.
- `source/` uses one durable `SourceActivationRecord` as the visibility switch; config and snapshot blobs are staged separately but become visible only through that shared pointer.
- If staged cleanup fails after an activation failure, any unreachable staged config or snapshot blobs remain harmless because no active pointer references them; later startup or acceptance cleanup may discard them without affecting the visible source pair.
- Invalid rename regex is rejected at source validation time, not later during output naming.
- If a saved local source file becomes unreadable, `SourceReadiness` reports the source as broken and Home or Settings reacts through live readiness.
- Missing active snapshot content degrades to safe empty-source behavior with a visible warning banner.
- Missing active snapshot content is surfaced distinctly from retained-prior refresh failure; Home must be able to tell the difference without reverse-engineering a warning string.
- Existing queue rows never rebind to a newer snapshot; that invariant is expressed through `QueueTaskInput` and later owned by `downloads/`.

## Testing Plan

### `SourceAcceptanceServiceTest.kt`

- Scope: unit
- Covers:
  - successful URL-source acceptance
  - successful file-source acceptance
  - validation rejection keeps prior config and snapshot
  - activation failure rolls back the accepted-source record
- Fixtures:
  - fake `SourceDocumentParser`
  - fake `SourceConfigStore`
  - fake `SnapshotPublisher`
  - test clock

### `SourceValidationTest.kt`

- Scope: unit
- Covers:
  - directory path validation
  - exact `.zip` path validation
  - exact non-`.zip` rejection
  - rename regex rejection at acceptance time
  - recursive-unarchive requires `unarchive=true`
- Fixtures:
  - source document fixtures

### `SnapshotPublisherTest.kt`

- Scope: unit
- Covers:
  - staged snapshot activation
  - one activation pointer swap makes config and snapshot visible together
  - retained-prior refresh result projects `HomeSourceWarning.LatestRefreshFailed`
  - missing-snapshot fallback projects `HomeSourceWarning.MissingSnapshotFallback`
  - failed-without-snapshot refresh result
  - readiness projection hydrates from stored active config and snapshot
- Fixtures:
  - fake `SnapshotStore`
  - fake `SourceConfigStore`
  - fake `SourceActivationStore`
  - fake loader
  - test clock

### `StandardBrowseBuilderTest.kt`

- Scope: unit
- Covers:
  - path-scope-first filtering
  - ignore-rules-second filtering
  - stable standard-file identity assignment
  - alphabetical sort by original file name
- Fixtures:
  - fake cached inventory enumerator
  - cached browse inventory fixtures

### `ArchiveBrowseBuilderTest.kt`

- Scope: unit
- Covers:
  - exact `.zip` resolution
  - remote ZIP enumeration
  - ignore-rule filtering over archive entries
  - alphabetical sort by internal file name after filtering
  - failure propagation without fallback
- Fixtures:
  - fake `RealDebridFacade`
  - fake `RemoteZipFacade`

### `SourceFacadeIntegrationTest.kt`

- Scope: integration
- Covers:
  - setup save produces active config and active snapshot
  - cold-launch refresh retains prior snapshot on failure
  - home-state projection after missing snapshot content
  - accepted-source summary hydrates from persisted storage on startup
- Fixtures:
  - temp config store
  - temp snapshot store
  - temp activation store
  - fake loader

## Open Questions or Deferred Decisions

None currently.
