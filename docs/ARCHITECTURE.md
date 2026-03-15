# ARCHITECTURE - Clean-Sheet Code Shape

## 1. Purpose

1. This document defines the planned code shape for the replacement app, not the structure of the current implementation.
2. The behavior contracts in [`BEHAVIOR.md`](BEHAVIOR.md) remain the source of truth for user-visible outcomes.
3. The spike outputs in [`spikes/outputs/README.md`](spikes/outputs/README.md) are the accepted evidence for the hard boundary decisions recorded here.
4. The goal is a simple single-app architecture that is easy to name, easy to navigate, and easy to extend without vague buckets.
5. This overview owns package ownership, named stores and filesystem boundaries, state authorities, identity authorities, dependency rules, and major flow stages.
6. Detailed state tables, persistence schemas, algorithms, platform mechanics, and failure matrices belong in `docs/architecture/*.md`.
7. The package docs under `docs/architecture/*.md` should use structural pseudocode by default:
   - keep concrete file names, public signatures, owned records, and durable invariants,
   - use fuller method bodies only when the body itself encodes a contract such as atomic commit, recovery ordering, or output finalization order.
8. If removing a fact from this file would make package ownership ambiguous, it belongs here. Otherwise it belongs in the detailed docs.

## 2. Simplicity Rules

1. Keep this as one Android app module unless a future constraint proves otherwise.
2. Top-level package names must describe ownership, not implementation style.
3. Avoid vague buckets such as `data`, `core`, `utils`, `manager`, or `common` at the top level.
4. Use plain classes and sealed models by default.
5. Introduce interface and repository indirection only when there is a real boundary:
   - external service integration,
   - persisted store or filesystem boundary,
   - export surface that benefits from substitution in tests.
6. `ui/` is presentation only.
7. `source/` and `downloads/` are the only orchestration-heavy product packages.
8. One queue row always means one user-selected work unit:
   - one selected provider file in standard mode,
   - one selected internal ZIP entry in archive-selection mode.

## 3. Root Package Map

```text
app/
ui/
source/
downloads/
realdebrid/
remotezip/
diagnostics/
```

### 3.1 `app/`

1. Composition root and app-shell routing only.
2. Own application startup, setup gating, dependency wiring, navigation, notification deep-link routing, and Android permission or URI-grant entrypoints.
3. Cold-start bootstrap must run from an activity-lifecycle or startup-viewmodel one-shot boundary outside normal composition, with an explicit cold-launch token.
4. `app/` may compose readiness from other owners, but it must not own provider logic, source logic, queue logic, or output policy.

### 3.2 `ui/`

1. Own screens, view models, presentation state, and user-intent translation.
2. Subpackages should stay product-facing:
   - `ui/setup`
   - `ui/home`
   - `ui/files`
   - `ui/downloads`
   - `ui/settings`
3. `ui/` may hold screen-local search, dialog, and selection state.
4. `ui/` must not construct durable queue records directly and must not own long-running work.
5. `ui/` may call owner-facing product entrypoints only and must not coordinate cross-owner business workflows.

### 3.3 `source/`

1. Own source acceptance, validation, persisted source configuration, snapshot publication, and file-browsing preparation.
2. Recommended internal split:
   - `source/ingest`
   - `source/snapshot`
   - `source/browse`
3. `source/` owns:
   - URL versus local-file source handling,
   - schema and runtime validation,
   - source configuration persistence,
   - active source-pair visibility through one config-plus-snapshot activation pointer,
   - active snapshot identity and replacement,
   - standard file listing preparation,
   - archive-selection listing preparation.
4. `source/` applies source scope first and ignore rules second before items reach `ui/files`.

### 3.4 `downloads/`

1. Own queue lifecycle, queue state machine, attempt execution, output handling, background work entrypoint, and download-specific settings.
2. Recommended internal split:
   - `downloads/config`
   - `downloads/queue`
   - `downloads/attempts`
   - `downloads/output`
   - `downloads/work`
3. `downloads/` owns:
   - durable queue records and state transitions,
   - retry, restart, pause, and resume policy,
   - progress checkpoints,
   - queue-global auth gating for forward progress,
   - destination reservation and output commit,
   - execution-time output-root binding,
   - naming and collision handling,
   - unarchive and recursive-unarchive policy,
   - cleanup,
   - progress and completion notification composition.
4. `downloads/work` is the only execution authority for background queue processing; `ui/` never owns running work.

### 3.5 `realdebrid/`

1. Own all Real-Debrid specific protocol behavior and secure API token access through an encrypted credential seam.
2. `realdebrid/` is a shared boundary package because both `source/` and `downloads/` depend on it.
3. `realdebrid/` owns:
   - API token validation,
   - request budgeting,
   - provider file selection and acquisition for queued standard-file work,
   - exact-container provider lookup for archive-selection mode,
   - provider acquisition polling,
   - later unrestricted-link resolution after acquisition reports ready links.
4. `realdebrid/` should expose Romulus-shaped models rather than leaking raw API details across the app.

### 3.6 `remotezip/`

1. Own exact-container remote ZIP behavior.
2. `remotezip/` is also a shared boundary package because it is used during both archive-selection browsing and archive-selection download execution.
3. `remotezip/` owns:
   - transport probing,
   - range-read validation,
   - metadata-first enumeration,
   - stable internal entry identity,
   - selected-entry copy, including resume from a caller-provided byte offset.
4. `remotezip/` must not silently degrade into whole-container fallback.

### 3.7 `diagnostics/`

1. Own diagnostics settings, event capture, internal-artifact retention, exported-bundle retention, clear, and export.
2. Internal organization inside `diagnostics/` is an implementation detail except where later sections assign explicit persistence or export authority.
3. Diagnostics is a sink and reporting surface, not a source of product decisions.
4. Diagnostics failure must never block the main product flow.

## 4. Named Stores and Filesystem Boundaries

1. `ConfigStore` is the shared physical backing store for small persisted configuration records; sharing that file does not create a shared configuration authority.
2. `app/` owns the setup-completion record in `ConfigStore` used by app-shell startup routing.
3. `app/` owns `UriGrantRegistry` for persisted local-source read grants and output-directory write grants.
4. `source/ingest` owns the staged accepted source-configuration records in `ConfigStore`, including accepted source mode, accepted source value, refresh inputs, and any staged-but-not-yet-visible candidate needed for atomic activation.
5. `source/snapshot` owns `SnapshotStore` for staged `SourceSnapshot` blobs plus `SourceActivationStore` for the one active config-plus-snapshot pointer that makes a staged pair visible together.
6. `downloads/config` owns the download-settings record in `ConfigStore` for download directory, concurrency, and other download-scoped settings.
7. `downloads/queue` owns `DownloadLedgerStore` as the shared physical backing store for durable queue records and recovery checkpoints, with separate logical records for `QueueTask`, durable row `createdAt` and `updatedAt` metadata, `QueueTaskState`, attempt counters, retry schedule, row-visibility metadata, persisted pending live actions, queue-owned persisted `OutputReservation` and `FinalOutputRecord` data, persisted `Preparing` display metadata including start time, timeout deadline, last provider status, and last provider progress when available, local transfer checkpoints, and opaque provider-preparation resume markers used to continue polling without resetting the existing `Preparing` deadline.
8. `downloads/output` owns `OutputFilesystem` for temp artifacts, reserved destinations, final outputs, restart cleanup, and the identity shape of reservations plus final-output records; `downloads/output` does not own the queue ledger that persists task-attached copies of those records.
9. `realdebrid/` owns `CredentialVault` access for encrypted API token storage and masked token readback.
10. `diagnostics/settings` owns the persisted diagnostics enabled-state record in its shared settings store.
11. `diagnostics/store` owns `DiagnosticsStore` for retained internal diagnostics artifacts (`manifest.json`, `timeline.jsonl`, `failures.jsonl`, `summary.json`).
12. `diagnostics/export` owns `DiagnosticsExportFilesystem` for writing timestamped exported bundles into the user-selected destination returned by the Android document picker; exported files are user-owned and are not part of diagnostics clear semantics.

## 5. State Authorities

1. `source/snapshot` is the only authority for the active `SourceSnapshot`.
2. Source refresh either replaces the active source pair atomically or leaves the prior pair active.
3. `source/ingest` owns staged accepted-source config records, but a staged config candidate must remain invisible until `source/snapshot` commits the shared activation pointer for the matching snapshot.
4. `downloads/queue` is the only authority for canonical `QueueTaskState`, attempt count, retry scheduling, pending live actions, pause, resume, cancel, retry, restart actions, row visibility, runnable-task claim, stale-run detection, interrupted-work recovery decisions including resume-in-place versus safe requeue, queue-owned persisted reservation/output data, and durable row `updatedAt` metadata.
5. `downloads/work` owns runtime consumption of persisted max concurrency, claim-limit derivation, queue-global auth gating, app-launch recovery wake, worker wake-up scheduling from queue mutations and retry deadlines, asks `downloads/queue` for runnable tasks, runs only queue-claimed work, and must not invent or persist task states outside the queue model.
6. `downloads/output` is the only authority for output reservation identity, final-output identity, temp-to-final promotion, archive cleanup, and restart preconditions, but it receives queue-owned persisted reservation/output data as input rather than reading queue storage by `taskId`.
7. `downloads/queue`, `downloads/work`, `downloads/attempts`, and `downloads/output` may exchange commands and records only through the documented seams here; none may mutate another owner's store or bypass another owner's authority.
8. `downloads/queue` is the authority for whether active downloads exist; `ui/settings` uses that signal to decide whether normal edits are locked or owner-owned corrective changes are allowed.
9. Sharing physical settings storage does not change ownership: `app/`, `source/ingest`, `downloads/config`, and `diagnostics/settings` each validate and persist only their own records.
10. `diagnostics/store` is the only authority for retained internal diagnostics artifacts, and `diagnostics/export` is the only authority for exported-bundle retention and clear cleanup.
11. `diagnostics/` is append-only from the perspective of the product flow; product packages may emit events, but they do not read diagnostics to drive product decisions.

## 6. Identity Authorities

1. `source/snapshot` assigns `snapshotId`.
2. `source/browse` assigns standard-file `SelectableItemId`.
3. `remotezip/` assigns duplicate-safe archive-entry identity; archive-entry retry and redownload must use stable entry identity, never filename or path alone.
4. `downloads/queue` assigns `taskId` and binds it to one enqueue-time `snapshotId` plus one selected-item identity; execution, retry, restart, and recovery never rebind a task to a newer active snapshot.
5. `source/torrentmeta` owns the torrent-native selection intent used to re-derive one queued standard-file selection without changing queue identity, while `realdebrid/` owns exact-zip provider locator data.
6. `downloads/output` assigns reservation identity and final output record identity; display names are never the only durable key.

## 7. Shared Records and Commands

1. `SourceDocument`
   - decoded source input before acceptance.
2. `SourceSnapshot`
   - immutable accepted source state with stable `snapshotId`.
3. `SelectableItem`
   - one browseable row returned to `ui/files`.
   - variants:
     - standard provider file,
     - archive-selected internal ZIP entry.
   - each variant carries stable item identity owned by its producer.
4. `QueueTaskInput`
   - the command payload submitted from `ui/files` into `downloads/queue`.
5. `QueueTask`
   - the durable queue record created from one selected item and user options.
   - binds one `taskId` to one enqueue-time `snapshotId` plus one selected-item identity.
   - persists storage target context, including output sub-folder context when present, but not a permanently pinned saved output-directory URI.
   - execution, retry, restart, and recovery keep that binding instead of rebinding to a newer active snapshot.
6. `QueueTaskState`
   - the canonical durable state for one `QueueTask`.
   - uses the status names defined in [`behavior/status-model.md`](behavior/status-model.md).
7. `OutputReservation`
   - reserved destination plus temp-write authority for one `QueueTask`.
   - created only by `downloads/output`.
8. `FinalOutputRecord`
   - durable final-output record with output-owned identity, persisted by `downloads/queue` for one `QueueTask`.
9. `DiagnosticEvent`
   - one structured event emitted into `diagnostics/`.

## 8. Execution Flows

### 8.1 App Shell and Startup Routing

1. `app/` reads its setup-completion record in `ConfigStore`, `UriGrantRegistry`, and owner-provided readiness from `realdebrid/`, `source/`, and `downloads/config`.
2. `app/` runs that bootstrap exactly once per explicit cold-launch token from the activity-lifecycle startup boundary instead of from normal composition.
3. `app/` routes to setup before setup completes; otherwise it routes to the main navigation shell and lets owner-owned invalid-settings or source-load handling appear within that shell.
4. `app/` never repairs business state directly; broken saved settings are fixed through the owning package while shell navigation remains visible.

### 8.2 Source Ingest and Snapshot Publication

1. `ui/setup` and `ui/settings` submit raw source input to `source/ingest`.
2. `app/` captures or restores the required URI grant before handoff when source or download directory uses Android document URIs.
3. `source/ingest` parses `SourceDocument` and validates schema plus runtime rules; rejected input does not mutate the previously accepted source configuration or active snapshot.
4. On success, `source/ingest` stages the accepted source-configuration candidate in `ConfigStore`, `source/snapshot` writes the new `SourceSnapshot` to `SnapshotStore`, and one activation step makes the new config and new snapshot visible together.
5. That activation step is a single committed pointer record; readers never see a new config without its matching snapshot, or vice versa.
6. `ui/home` reads published `SourceSnapshot` state only.
7. Failed refresh with a prior snapshot leaves the previous snapshot active.

### 8.3 Standard Files Browse

1. `ui/files` asks `source/browse` for the selectable items for one snapshot entry.
2. `source/browse` reads cached standard browse inventory for the snapshot entry and fills that cache through temporary `realdebrid/` enumeration on cache miss.
3. `source/browse` applies source scope first and ignore rules second.
4. `source/browse` returns `SelectableItem.StandardFile[]` to `ui/files`.
5. Search, multi-select, and dialog state remain in `ui/files`.

### 8.4 Archive-Selection Browse

1. `source/browse` detects the exact `.zip` `scope.path` case from the accepted source entry.
2. `source/browse` asks `realdebrid/` for the exact outer ZIP URL.
3. `source/browse` asks `remotezip/` to probe and enumerate internal entries.
4. `source/browse` applies ignore rules before returning rows.
5. `source/browse` sorts archive-entry rows alphabetically by internal file name before returning them.
6. `source/browse` returns `SelectableItem.ArchiveEntry[]`.
7. Failure in this branch stays in archive-selection mode with `Retry`; it never falls back to standard file listing.

### 8.5 Queue Creation

1. `ui/files` sends `QueueTaskInput[]` into `downloads/queue`.
2. `downloads/queue` is the only package allowed to create `QueueTask` plus initial `QueueTaskState` records in `DownloadLedgerStore`.
3. Each `QueueTask` stores enough identity to execute later without UI help:
   - enqueue-time `snapshotId`,
   - source entry reference,
   - selected item identity,
   - original display name,
     - output naming intent,
     - unarchive intent,
     - recursive-unarchive intent,
     - storage target context, including output sub-folder context when present,
     - source-owned execution context needed for retry, restart, or recovery:
       - torrent-native standard-file selection intent, or
       - archive-selection preparation key plus archive-entry identity.
4. Execution, retry, restart, and recovery keep that queue binding and never rebind a task to a newer active snapshot.
5. `ui/downloads` reads durable queue state from `downloads/queue`, not from reconstructed screen-local state.
6. `ui/downloads` and `ui/settings` never infer active work from local UI memory; they read queue authority instead.

### 8.6 Standard Download Execution

1. `downloads/work` is the runtime entrypoint for background execution, reads max concurrency through `downloads/config`, turns it into a claim limit, owns the queue-global auth gate, wakes work on enqueue, app launch, auth repair, or retry events, and asks `downloads/queue` for runnable, queue-claimed tasks only.
2. `downloads/queue` persists the canonical state that matches the actual attempt phase before work proceeds, detects stale interrupted claims through durable claim leases, persists pending live actions, and decides whether interrupted work resumes in place, honors a pending action, or safely requeues; retry history remains metadata, not a separate running state.
3. `downloads/attempts` asks `realdebrid/` to execute the proven three-stage model through explicit public seams:
   - provider file selection,
   - provider acquisition,
   - later unrestricted-link handling after acquisition reports ready links.
4. `Resolving` is the claim-start stage for rehydration, auth-gate check, and fresh download-unit or outer-container resolution; `Preparing` begins only when provider-side acquisition is actually waiting.
5. During active byte transfer, `downloads/attempts` emits local-transfer checkpoint updates at least every 3 seconds and again on pause, cancel, failure, and completion; `downloads/queue` persists those updates into `DownloadLedgerStore`.
6. During `Preparing`, `downloads/attempts` emits provider-preparation resume markers when needed to continue after interruption without resetting the deadline; `downloads/queue` persists those markers and related display metadata into `DownloadLedgerStore`.
7. `realdebrid/` reads the API token through `CredentialVault`, returns waiting updates plus ready-link results during provider acquisition, and resolves unrestricted links only after `downloads/attempts` asks for that later step.
8. `downloads/output` creates `OutputReservation`, binds fresh writes to the current saved output directory when a new reservation is needed, writes the temp output artifact, commits the final output, and reports the outcome back to `downloads/queue`.
9. If one selected file must map to grouped provider file ids internally, that grouping stays hidden inside `realdebrid/`; the queue model still remains one task per user-selected file.

### 8.7 Archive-Selection Download Execution

1. `downloads/work` asks `downloads/queue` for runnable, queue-claimed archive-entry tasks only.
2. `downloads/attempts` resolves the shared archive-preparation record to a ready outer ZIP URL before copy begins.
3. `downloads/attempts` reopens the selected internal entry through `remotezip/` using duplicate-safe stable entry identity, never filename or path alone.
4. `downloads/output` creates `OutputReservation` for the selected entry before any local artifact write begins.
5. During selected-entry copy, `downloads/attempts` emits local-transfer checkpoint updates at least every 3 seconds and again on pause, cancel, failure, and completion; `downloads/queue` persists those updates into `DownloadLedgerStore`.
6. `remotezip/` copies only the selected entry into that reservation-backed local output artifact and must be able to resume from a saved offset instead of forcing full restart.
7. `downloads/output` then either saves that artifact as-is or runs the local unarchive branch.
8. The outer remote ZIP is never the final output unit in this mode.

### 8.8 Output Finalization

1. `downloads/output` owns:
   - output-name reservation,
   - bound output-root identity for one reservation,
   - temp-file placement,
   - temp-artifact type preservation for local unarchive,
   - collision suffixing,
   - local unarchive decision,
   - recursive pass control,
   - archive cleanup,
   - final-output recording.
2. A file becomes a final output only after `downloads/output` commits the reserved destination.
3. Rename applies only to final non-archive outputs.
4. Archive filenames are never renamed before extraction.
5. Successful archive extraction deletes the consumed archive.
6. Failed archive extraction keeps the failed archive and preserves earlier successful outputs.
7. `Restart` does not proceed until `downloads/output` has removed or rejected the prior output set, the reserved temp artifact, and any reserved-but-not-finalized extraction outputs for that row.

### 8.9 Settings and Diagnostics Actions

1. `ui/settings` is a thin editor over multiple owners:
   - API token flows through `realdebrid/`,
   - source configuration flows through `source/`,
   - download directory and concurrency flow through `downloads/config`,
   - diagnostics enabled state flows through `diagnostics/settings`,
   - diagnostics clear and export flow through `diagnostics/`.
2. `downloads/queue` tells `ui/settings` whether active work exists and whether normal edit-lock or owner-owned corrective handling applies.
3. `ui/settings` forwards user intent to owner-facing product entrypoints and renders owner-owned state; it must not orchestrate multi-owner business workflows.
4. Each owner validates and persists its own values; `ui/settings` never acts as a settings backend.
5. `app/` captures new URI grants at the platform boundary and passes them to the owning package instead of storing raw Android permission flow inside `ui/`.
6. This keeps ownership honest even when several small config records share the same physical `ConfigStore`.

## 9. Platform and Security Boundaries

1. `app/` owns Android URI permission capture and restore.
2. `realdebrid/` is the only package that reads plaintext API tokens from `CredentialVault`.
3. `diagnostics/` owns credential, token, and sensitive URL redaction before persistence or export.
4. No other package may persist plaintext credentials or raw sensitive URLs.

## 10. Dependency Rules

1. `app/` may depend on every root package because it is the composition root.
2. `ui/` may call owner-facing product entrypoints only; it must not coordinate multi-owner workflows or reach through one owner to another owner's internals.
3. `source/` may depend on `realdebrid/`, `remotezip/`, and `diagnostics/`.
4. `downloads/` may depend on `realdebrid/`, `remotezip/`, and `diagnostics/`.
5. `realdebrid/` must not depend on `source/`, `downloads/`, or `ui/`.
6. `remotezip/` must not depend on `source/`, `downloads/`, or `ui/`.
7. `diagnostics/` may receive events from the rest of the app but must not own callback-driven product flow.
8. Circular dependencies across root packages are forbidden.

## 11. Boundary Decisions Locked by Spike Evidence

1. `realdebrid/` must keep selection, provider acquisition, and link handling as separate stages.
   Source: [`spikes/outputs/spike-1-resolution.md`](spikes/outputs/spike-1-resolution.md)
2. `downloads/output` must own flattening, rename ordering, bounded recursive unarchive, collision handling, and cleanup instead of pushing that logic into the archive runtime.
   Source: [`spikes/outputs/spike-2-unarchive.md`](spikes/outputs/spike-2-unarchive.md)
3. `remotezip/` must own range probe, metadata-first enumeration, stable entry identity, and selected-entry copy, with no full-download fallback.
   Source: [`spikes/outputs/spike-3-remote-zip.md`](spikes/outputs/spike-3-remote-zip.md)
4. The integrated Android path proves that `source/`, `realdebrid/`, `remotezip/`, and `downloads/output` can work together for archive-selection mode.
   Source: [`spikes/outputs/spike-4-integration.md`](spikes/outputs/spike-4-integration.md)

## 12. What This Architecture Explicitly Avoids

1. No top-level `data/` package.
2. No top-level `core/` or `common` bucket.
3. No class-per-concept repository layer when a plain service or store class is enough.
4. No UI-owned queue record construction.
5. No provider-specific logic in `ui/`.
6. No degraded fallback from archive-selection to standard listing or from remote ZIP to whole-container download.
