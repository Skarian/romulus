# `app/` Package Architecture

## Purpose

`app/` is the Android composition root.
It owns startup, setup gating, shell routing, persisted URI-grant seams, and notification deep-link intake.

## Responsibilities

- Build and expose the process-wide dependency graph.
- Decide whether the app should show Setup or the main shell.
- Restore persisted URI grants before neighboring packages evaluate readiness.
- Compose shell readiness from `realdebrid/`, `source/`, and `downloads/`.
- Trigger one cold-launch source refresh in URL mode.
- Translate Android launch intents and notification taps into safe shell routes.
- Hand off from successful setup into the main shell.
- Request notification permission and app-launch queue recovery at the correct app-owned moments.

## Boundaries

- `app/` does not validate API tokens, source JSON, or download settings.
- `app/` does not own source snapshots, browse rows, queue rows, output reservations, or diagnostics artifacts.
- `app/` does not repair broken product state directly; it keeps the shell visible and lets owning packages surface corrective states.
- `app/` does not compose notification copy or execute download work.

## Public Seams

- App graph creation and dependency wiring.
- Startup bootstrap and startup session state.
- Shell route state and navigation.
- Persisted source and output directory grant capture.
- Notification deep-link parsing.

## Durable Invariants

1. Startup bootstrap runs once per explicit cold launch, outside normal recomposition.
2. Persisted grants are restored before saved-setting readiness is evaluated.
3. Setup gating stays app-owned even though readiness comes from neighboring packages.
4. `app/` owns navigation vocabulary, but not business-state repair logic.

## Key Flows

### Cold Start

1. Restore persisted grants.
2. Read setup completion and owner-provided readiness.
3. Route to Setup or the main shell.
4. If setup is already complete and URL mode is active, trigger the one cold-launch refresh.

### Setup Completion

1. Setup submits through owner facades.
2. After all required owners report ready, `app/` marks setup complete.
3. The shell route moves to Home without requiring process recreation.
