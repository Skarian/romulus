# ARCHITECTURE

This document describes the ownership model of the shipped app.
It complements [`BEHAVIOR.md`](BEHAVIOR.md): behavior docs define user-visible outcomes, while this file defines which package owns each runtime concern.

Concrete class and file layout now live in the source tree. The architecture docs intentionally stop at package responsibilities, durable invariants, and cross-package flows.

## Root Package Map

```text
app/
ui/
source/
downloads/
realdebrid/
remotezip/
diagnostics/
```

## Ownership Rules

1. `app/` owns startup, setup gating, navigation, URI-grant seams, and notification deep-link intake.
2. `ui/` owns presentation, screen-local state, and user-intent translation.
3. `source/` owns source acceptance, active snapshot publication, and browse preparation.
4. `downloads/` owns queue state, execution, output handling, and download settings.
5. `realdebrid/` owns encrypted token access and all Real-Debrid protocol behavior.
6. `remotezip/` owns exact-container ZIP probing, enumeration, and selected-entry copy.
7. `diagnostics/` owns diagnostics enablement, event retention, export, and clear.

## System Invariants

1. The app stays a single Android app module.
2. Each durable record has one owning package even when multiple owners share the same physical storage medium.
3. `source/` is the only authority for the active source snapshot.
4. `downloads/queue` is the only authority for durable queue state, row visibility, and row actions.
5. `downloads/output` is the only authority for output reservation identity, finalization, archive cleanup, and restart cleanup preconditions.
6. `realdebrid/` exposes Romulus-shaped models; raw provider DTOs stay inside the package.
7. `remotezip/` never falls back to whole-container download.
8. `diagnostics/` is a sink only; product behavior must not depend on diagnostics content.

## Shared Runtime Contracts

1. One queue row always means one user-selected work unit:
   - one standard provider file, or
   - one selected internal ZIP entry.
2. Queue work binds to the enqueue-time snapshot and selected-item identity and never silently rebinds to a newer source snapshot.
3. Source refresh either publishes a new config-plus-snapshot pair atomically or leaves the prior pair active.
4. Standard browse fills may use temporary Real-Debrid enumeration to build an on-device cache, then delete the temporary provider torrents.
5. Archive-selection browse and archive-entry execution stay on the exact-container path; they do not degrade into standard browse or whole-archive download.
6. Diagnostics failures must not block primary user flows.

## Major Flows

### Startup and Shell

1. `app/` restores persisted grants, gathers readiness from neighboring owners, and routes to Setup or the main shell.
2. `app/` does not repair source, token, queue, or output state directly; the owning packages surface corrective states inside the shell.

### Source Acceptance and Publication

1. `ui/setup` and `ui/settings` submit source changes through `source/`.
2. `source/` validates the document, stages the candidate config and snapshot, and publishes them together.
3. Home and Files read only published source state, not in-flight candidates.

### Browse

1. In standard mode, `source/` returns rows from cached browse inventory, filling that cache through `realdebrid/` when needed.
2. In archive-selection mode, `source/` resolves the exact outer ZIP through `realdebrid/` and enumerates internal entries through `remotezip/`.
3. `ui/files` owns search, selection, and page-local queue preferences.

### Queue Execution

1. `ui/files` submits queue intents to `downloads/`.
2. `downloads/queue` creates durable rows and remains the authority for status, retries, restarts, cancellation, and visibility.
3. `downloads/work` runs claimed tasks, `downloads/attempts` performs transfer work, and `downloads/output` finalizes outputs.
4. `downloads/` uses `realdebrid/` for standard acquisition and `remotezip/` for archive-entry copy.

### Diagnostics

1. Product packages emit best-effort events into `diagnostics/`.
2. `diagnostics/` persists, rotates, exports, and clears diagnostics artifacts without influencing product decisions.

## Package Docs

1. [`architecture/app.md`](architecture/app.md)
2. [`architecture/ui.md`](architecture/ui.md)
3. [`architecture/source.md`](architecture/source.md)
4. [`architecture/downloads.md`](architecture/downloads.md)
5. [`architecture/realdebrid.md`](architecture/realdebrid.md)
6. [`architecture/remotezip.md`](architecture/remotezip.md)
7. [`architecture/diagnostics.md`](architecture/diagnostics.md)
8. [`architecture/diagram/diagrams/app-architecture.mmd`](architecture/diagram/diagrams/app-architecture.mmd)

## Historical Context

Migration rationale, spike evidence, and cutover history remain in [`MIGRATION.md`](MIGRATION.md) and the `spikes/` docs.
