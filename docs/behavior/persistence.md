# Persistence Behavior

## Trigger

1. User saves setup or settings values.
2. Source is loaded or refreshed.
3. Queue rows are queued, processed, paused, cancelled, retried, or restarted.
4. App or worker process is interrupted and later resumed.
5. User enables, clears, or exports diagnostics.

## Expected Result

1. Setup completion fields are persisted:
   - API key,
   - source mode and value,
   - active source snapshot reference,
   - download directory,
   - setup completion flag.
2. Settings fields are persisted:
   - max concurrency,
   - diagnostics enabled state.
3. Source snapshot persistence keeps only the latest active snapshot; source lifecycle behavior follows [`source.md`](source.md).
4. Queue tasks are persisted durably with enough data to recover action and display state, including the enqueue-time source-derived execution context needed to continue without rereading an older snapshot.
5. Local download progress checkpoints are persisted at least every 3 seconds during active byte transfer.
6. A local progress checkpoint is also persisted when a download pauses, is cancelled, fails, or completes.
7. When a task enters `Preparing`, the app persists:
   - `Preparing` start time,
   - 24-hour timeout deadline,
   - last provider status,
   - last provider progress when available,
   - enough provider checkpoint data to resume polling without resetting the deadline.
8. Resume continues from the latest saved checkpoint instead of restarting from zero.
9. Process interruption must not reset `Preparing` timing or its 24-hour deadline.
10. Manual `Retry` and `Restart` start a fresh `Preparing` window when provider-side acquisition is needed again.
11. Queue summary counters remain stable across app restarts.
12. Download row visibility metadata is persisted so rows cleared from Downloads stay hidden across app restarts; clear-history behavior follows [`downloads.md`](downloads.md).
13. When diagnostics is enabled, diagnostics artifacts are persisted with bounded retention and rotation semantics defined in [`diagnostics.md`](diagnostics.md).
14. `Clear diagnostics` removes diagnostics artifacts from internal diagnostics storage and removes previously exported diagnostics bundles from app-specific external diagnostics export storage.
15. Diagnostics export bundles persist in app-specific external diagnostics export storage with retention of latest `3` until the user clears diagnostics; export behavior follows [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Missing required persisted configuration routes user either to Setup before first completion or to the Home invalid-settings error state after setup is complete, without crashing.
2. Missing or unreadable persisted snapshot content degrades to safe empty-source behavior with a visible warning on Home; Home warning behavior follows [`home.md`](home.md).
3. URL-source load or refresh failure without any usable snapshot routes Home to the source-load error state with visible `Retry`; Home behavior follows [`home.md`](home.md).
4. Persistence failures do not silently mark downloads successful.
5. Missing row-visibility metadata defaults to showing rows, not deleting them; Downloads fallback behavior follows [`downloads.md`](downloads.md).
6. Diagnostics persistence failures do not block primary app behavior and surface diagnostics action feedback as defined in [`diagnostics.md`](diagnostics.md).
