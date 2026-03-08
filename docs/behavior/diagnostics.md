# Diagnostics Behavior

## Trigger

1. User changes diagnostics settings from Settings; Settings control behavior follows [`settings.md`](settings.md).
2. App emits contracted behavior events across supported domains while diagnostics is enabled.
3. User selects `Clear diagnostics`.
4. User selects `Export diagnostics`.

## Expected Result

1. Diagnostics is off by default.
2. `Enable diagnostics` turns diagnostics capture on or off immediately without app restart.
3. While diagnostics is disabled, app does not append diagnostics events.
4. While diagnostics is enabled, app writes this diagnostics set in internal app storage:
   - `manifest.json`,
   - `timeline.jsonl`,
   - `failures.jsonl`,
   - `summary.json`.
5. `manifest.json` includes support metadata:
   - diagnostics contract version,
   - app version and build,
   - Android version and device model,
   - active session id seed,
   - export timestamp when exported,
   - redaction policy version.
6. `timeline.jsonl` captures meaningful behavior events across:
   - app shell,
   - source,
   - home,
   - files,
   - archive-selection,
   - downloads,
   - settings,
   - notifications.
7. `failures.jsonl` captures failure outcomes only, with sanitized reason and context. When a task fails from `Preparing` timeout, failure context includes the last known provider status and provider progress when available.
8. `summary.json` captures support snapshot aggregates:
   - event and failure counts by behavior domain,
   - latest queue summary counters (as defined in [`downloads.md`](downloads.md)),
   - latest source refresh outcome (as defined in [`source.md`](source.md)),
   - latest failure per behavior domain.
9. Diagnostics event records use a common envelope:
   - `timestamp`,
   - `sessionId`,
   - `domain`,
   - `event`,
   - `outcome`,
   - relevant context ids (`taskId`, `snapshotId`) when present.
10. Diagnostics content uses canonical queue states from [`status-model.md`](status-model.md).
11. Diagnostics captures provider-acquisition lifecycle events when applicable:
   - entered `Preparing`,
   - provider update observed,
   - resumed `Preparing` after recovery,
   - links ready,
   - 24-hour `Preparing` timeout reached.
12. Diagnostics artifacts must not include plaintext credentials or sensitive tokens; redaction and masking requirements follow [`security.md`](security.md).
13. Diagnostics storage is bounded to at most `25 MB` total with oldest-first rotation.
14. `Clear diagnostics` shows a confirmation dialog before applying.
15. Confirmed clear removes diagnostics artifacts from internal storage, removes previously exported diagnostics bundles from app-specific external diagnostics export storage, and shows success toast feedback.
16. `Export diagnostics` creates a timestamped zip bundle from current diagnostics artifacts.
17. Export bundles are written to app-specific external files diagnostics export storage for support sharing and `adb pull`.
18. Export retention keeps only the latest `3` bundles and removes older bundles automatically.
19. `Clear diagnostics` and `Export diagnostics` are available while downloads are active.

## Failure Behavior

1. Diagnostics write failures do not crash the app or block primary user flows.
2. If `Clear diagnostics` fails, existing diagnostics artifacts and previously exported diagnostics bundles remain unchanged and app shows error toast feedback.
3. If `Export diagnostics` fails, no partial bundle is presented as successful and app shows error toast feedback.
4. If retention cleanup fails, newest export remains available and app shows error feedback for cleanup failure.
