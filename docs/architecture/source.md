# `source/` Package Architecture

## Purpose

`source/` owns accepted-source configuration, source-document validation, snapshot publication, and browse preparation.
It is the only package allowed to turn raw source input into an active snapshot or to turn one snapshot entry into selectable browse rows.

## Responsibilities

- Accept URL or local-file source input from Setup and Settings.
- Parse and validate `version: 1` source documents.
- Persist the active accepted-source record.
- Stage and publish snapshots atomically.
- Expose source readiness and Home state.
- Build standard browse rows from locally cached inventory, filling that cache through temporary Real-Debrid enumeration when needed.
- Build archive-selection rows for exact `.zip` `scope.path` values by coordinating `realdebrid/` and `remotezip/`.
- Apply source scope first and ignore rules second before rows reach `ui/files`.

## Boundaries

- `source/` does not own API-token validation or storage.
- `source/` does not own queue rows, retry policy, or worker scheduling.
- `source/` does not own remote ZIP copy execution.
- `source/` does not own output reservation, naming collisions, or unarchive execution.
- `source/` consumes persisted grant descriptors from `app/`; it does not capture grants itself.

## Public Seams

- Source acceptance and refresh.
- Accepted-source summary and readiness observation.
- Home-state projection.
- Browse requests and browse results.

## Durable Invariants

1. Failed source updates must not publish a partial config or partial snapshot.
2. Config and snapshot activation is atomic from readers' point of view.
3. Archive-selection mode must never fall back to standard browse.
4. Browse rows carry stable identity owned by `source/` or `remotezip/`, not by UI state.
5. Queue work later binds to the snapshot and item identity emitted here.

## Key Flows

### Accept and Publish

1. Load raw source bytes from URL or a granted local file.
2. Parse and validate the document.
3. Stage the accepted config and derived snapshot.
4. Publish both together or leave the prior pair active.

### Standard Browse

1. Read cached inventory for the snapshot entry.
2. On cache miss, enumerate provider files through temporary Real-Debrid inventory work.
3. Apply scope and ignore filtering, then return stable rows.

### Archive Selection

1. Resolve the exact outer ZIP through `realdebrid/`.
2. Enumerate internal ZIP entries through `remotezip/`.
3. Apply ignore filtering and return stable archive-entry rows.
