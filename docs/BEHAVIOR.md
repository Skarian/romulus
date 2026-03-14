# BEHAVIOR - Migration Contracts

This document defines what the app must do for users.
It is intentionally implementation-agnostic.

## 1. Contract Rule

1. Preserve behavior parity, not system parity.
2. Keep contracts at user-visible intent level.
3. Internal timings, caching, and algorithm choices may change unless a user-visible outcome depends on them.

## 2. Behavior Spec Set

1. [`behavior/setup.md`](behavior/setup.md)
2. [`behavior/source.md`](behavior/source.md)
3. [`behavior/home.md`](behavior/home.md)
4. [`behavior/files.md`](behavior/files.md)
5. [`behavior/archive-selection.md`](behavior/archive-selection.md)
6. [`behavior/downloads.md`](behavior/downloads.md)
7. [`behavior/settings.md`](behavior/settings.md)
8. [`behavior/notifications.md`](behavior/notifications.md)
9. [`behavior/security.md`](behavior/security.md)
10. [`behavior/diagnostics.md`](behavior/diagnostics.md)
11. [`behavior/app-shell.md`](behavior/app-shell.md)
12. [`behavior/status-model.md`](behavior/status-model.md)
13. [`behavior/naming.md`](behavior/naming.md)
14. [`behavior/persistence.md`](behavior/persistence.md)

## 3. Source Contract

1. Source JSON shape is defined in [`schema.json`](schema.json).
2. Schema captures input structure.
3. Runtime behavior on valid or invalid input is defined in [`behavior/source.md`](behavior/source.md).

## 4. Contract Boundaries

Required boundaries:
1. Setup gating remains mandatory.
2. Downloads retain recoverability actions for visible tasks; user-confirmed history clear may hide terminal tasks from the default list.
3. Queue state remains durable across process death.
4. Credential handling remains protected.

Flexible boundaries:
1. Screens and controls may be merged if user outcomes stay the same.
2. Internal orchestration and storage internals may be replaced.
3. State complexity may be reduced when outcomes remain clear.

## 5. Intentional Differences from Current App

1. Current Setup behavior is a multi-step flow with step navigation; current contract intentionally uses single-screen setup.
2. Current source ingest can keep valid entries while skipping invalid entries; current contract intentionally enforces all-or-nothing source validity.
3. Current Home behavior still has duplicate-name disambiguation and alphabetical sorting; current contract intentionally uses JSON order and exact names.
4. Current Files behavior still always shows `Apply rename`; current contract intentionally scopes it to entries with rename regex and keeps row names original.
5. Current Files failure behavior does not expose an explicit retry action; current contract intentionally requires a visible `Retry` action.
6. Current Downloads behavior still exposes detailed status tags and `Delete partial`; current contract intentionally simplifies status language, adds explicit `Preparing` behavior for provider-side acquisition with a 24-hour cap, and keeps cleanup under `Restart`.
7. Current Settings behavior still exposes manual refresh and wider concurrency limits; current contract intentionally removes manual refresh from Settings and bounds concurrency to `1..5`.
8. Notification behavior intentionally preserves terse status-only copy before bytes start moving, byte-first progress copy once downloads are active, and exact completion titles (`Downloads complete` or `Downloads finished`).
9. Current Downloads behavior has no history-clear action; current contract adds `Clear history` to hide terminal rows without deleting app data, without unhide.
10. Current docs define an optional entry-level `unarchive` object with required layout policy, Files-page overrides, flat or dedicated-folder extraction, recursive extraction option, and archive cleanup behavior.
11. Current app does not yet provide archive-selection mode for exact `.zip` source paths; current docs add remote internal-file selection and queueing behavior for this roadmap mode.
12. Current app does not yet provide the diagnostics contract; current docs add structured diagnostics capture, bounded retention, clear and export controls, and support-ready bundles.
