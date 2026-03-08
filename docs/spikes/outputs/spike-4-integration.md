# Spike 4 Integration

## Run Context

1. Fixture inputs came from `docs/spikes/.env.local`, staged into generated androidTest assets, and summarized in `docs/spikes/fixtures/generated/spike-4/user-input.md`.
2. The connected run evidence reviewed here is the pulled host artifact bundle at `docs/spikes/fixtures/generated/spike-4/run-artifacts/latest/`.
3. Session under review:
   - session id: `session-1772838057499`
   - started: `2026-03-06T23:00:57.499588Z`
   - finished: `2026-03-06T23:01:18.998445Z`
   - device: `samsung SM-S916U`
   - Android: `16`
   - runtime: `7-Zip-JBinding 16.02-2.03`, `usedPlatform = Linux-arm`
4. Harness used:
   - `docs/spikes/apps/spike-4-android/`
   - repo command surface `cd docs/spikes && just spike-4-android`
5. Primary references used for interpretation:
   - `docs/spikes/specs/spike-4-integration.md`
   - `docs/spikes/outputs/spike-1-resolution.md`
   - `docs/spikes/outputs/spike-2-unarchive.md`
   - `docs/spikes/outputs/spike-3-remote-zip.md`

## Evidence Summary

1. Integrated resolver plus remote-ZIP archive-selection flow works on-device.
   - `run-a-archive-selection-unarchive-on` passed in `session.json`.
   - Resolver artifacts exist under `run-a-archive-selection-unarchive-on/resolver/`.
   - Archive-selection artifacts exist under `run-a-archive-selection-unarchive-on/archive-selection/`.
2. Selected-only internal-file copy is proven.
   - `run-b-archive-selection-unarchive-off/outputs/before-unarchive.json` contains only the two selected internal ZIP entries.
   - `run-b-archive-selection-unarchive-off/outputs/final-output-manifest.json` shows those same `.zip` files preserved because unarchive is disabled.
   - The selected identities are preserved in `run-b-archive-selection-unarchive-off/archive-selection/selected-entry-set.json`.
3. Queue-task-to-output identity continuity is proven for both successful paths.
   - `run-a-archive-selection-unarchive-on/identity-trace.json` maps selected archive names to final `.nib` outputs after extraction and rename.
   - `run-b-archive-selection-unarchive-off/identity-trace.json` maps the same selected archive names to copied local archive paths with no extraction step.
4. Local unarchive, flattening, cleanup ordering, and rename are proven for the `unarchive=true` path.
   - `run-a-archive-selection-unarchive-on/outputs/passes/pass-01/output-manifest.json` shows only final `.nib` outputs.
   - `run-a-archive-selection-unarchive-on/outputs/final-output-manifest.json` matches the extraction pass output.
   - Output names confirm rename application:
     - `10th Frame (USA) (Alt).zip` -> `10th Frame (Alt).nib`
     - `720 Degrees (USA).zip` -> `720 Degrees (Side 1).nib`, `720 Degrees (Side 2).nib`
   - `run-a-archive-selection-unarchive-on/cleanup.json` records archive deletion after successful extraction.
5. Deterministic stage failure is proven.
   - `run-c-selection-failure` finished as `EXPECTED_FAILURE_OBSERVED`.
   - `run-c-selection-failure/failure.json` shows stage-labeled archive-selection failure for missing selected entry `__spike4_missing__/not-present.zip`.

## Decision Proposal

1. Keep the Spike 4 harness shape.
   - Standalone Android app, instrumentation-first matrix, generated live-config staging, and host artifact pullback are all viable.
2. Keep the integrated implementation seams already chosen.
   - Real-Debrid resolution, strict range-based remote ZIP enumeration, selected-only local copy, and `7-Zip-JBinding-4Android` local extraction all worked together on-device.
3. Treat Spike 4 as complete for the current architecture phase.
   - The integrated single-pass archive-selection flow is now proven end to end.
4. Keep the Spike 4 conclusion scoped to what this run actually proved.
   - Spike 4 proves the single-pass archive-selection integration path and does not change the broader product contract for recursive unarchive.

## Risk Register

1. Non-blocking risk:
   - The current harness accepts basename-only selected-entry input when it resolves uniquely inside the visible candidate set.
   - This is practical for the current live fixture state, but duplicate basenames still fail hard as ambiguous.
2. Non-blocking risk:
   - Connected execution remains dependent on live device stability and Real-Debrid account/runtime state.
   - The reviewed sessions succeeded, but earlier retries hit missing `INTERNET`, strict JSON parsing, and transient device-offline states before those were corrected.
3. Non-blocking risk:
   - The first implementation of `spike4ConnectedMatrix` pulled host artifacts before instrumentation, which left stale repo-local evidence until task ordering was fixed.
   - The current Gradle task now pulls after the run completes, so this is a corrected workflow risk rather than an open blocker.

## Architecture Implications

1. The integrated stage ordering is now evidenced on Android for the archive-selection path:
   - exact `.zip` resolution,
   - metadata-first internal enumeration,
   - ignore filtering before visible selection,
   - selected-only copy,
   - optional single-pass local unarchive,
   - cleanup,
   - rename,
   - diagnostics and artifact capture.
2. Architecture can treat one selected internal file as one queue-task-like unit.
   - `identity-trace.json` proves the outer provider `.zip` is not the user-visible work unit in archive-selection mode.
3. Architecture should preserve duplicate-safe internal-entry identity.
   - `selected-entry-set.json` carries `entryPath` plus `localHeaderOffset`, compressed size, uncompressed size, and CRC32.
4. Architecture should treat Spike 4 as proof of the single-pass archive-selection integration path only.
   - Recursive unarchive remains defined by the broader product contract and was not exercised by this Spike 4 run.
