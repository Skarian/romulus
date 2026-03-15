# `downloads/` Package Architecture

## Purpose

`downloads/` owns download settings, durable queue state, execution attempts, output handling, and the background worker entrypoint.
It is the only package allowed to create queue rows, mutate canonical queue state, and finalize download results.

## Responsibilities

- Persist and validate download settings such as output directory and concurrency.
- Create durable queue rows from Files-page queue intents.
- Own pause, resume, cancel, retry, restart, and clear-history behavior.
- Persist claims, retry schedules, preparing metadata, transfer checkpoints, visibility metadata, and final-output records.
- Execute standard-file work through `realdebrid/` and archive-entry work through `remotezip/`.
- Reserve deterministic output identity, finalize outputs, and clean up archive or restart artifacts.
- Gate forward progress when shared prerequisites such as auth are broken.
- Project queue state for Downloads UI and notifications.

## Boundaries

- `downloads/` does not validate source JSON or assign snapshot identity.
- `downloads/` does not parse raw Real-Debrid HTTP payloads directly.
- `downloads/` does not enumerate remote ZIP contents; it consumes selected-entry identity from `source/`.
- `downloads/` does not own diagnostics retention.
- `downloads/` consumes persisted output-directory descriptors from `app/`; it does not capture grants itself.

## Public Seams

- Download settings read and update.
- Queue enqueue, queue projection, active-download observation, and row actions.
- Clear-history.
- App-launch recovery wake.

## Durable Invariants

1. Queue rows are durable and remain the canonical state across process death.
2. Queue state must match the real work happening now.
3. Interruption must not reset `Preparing` timing or lose resumable transfer checkpoints.
4. `Clear history` is visibility-only and must not delete queue records.
5. Output naming, extraction order, and restart cleanup remain deterministic.
6. Queue work uses the enqueue-time snapshot and selection contract rather than rebinding to later source state.

## Key Flows

### Enqueue and Projection

1. Files submits one or more queue intents.
2. `downloads/queue` creates durable rows and initial state.
3. Downloads UI reads owner-backed projection, not reconstructed local state.

### Execution and Recovery

1. `downloads/work` claims runnable rows using current concurrency limits.
2. `downloads/attempts` executes provider acquisition or archive-entry copy.
3. `downloads/output` reserves output identity, writes data, finalizes results, and cleans up when needed.
4. Recovery preserves existing preparing windows, resumable writes, and queue ownership.

### History and Restart

1. `Clear history` only changes row visibility.
2. `Restart` cleans prior outputs before requeueing fresh work.
3. Hidden terminal rows stay excluded from default list summaries.
