# Implement Spike 4 integrated Android harness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `.agent/PLANS.md`, but per explicit user direction it lives at `docs/spikes/apps/spike-4-android/PLAN.md` beside the Spike 4 harness README instead of the default ExecPlan directory.

- Plan ID: EP-2026-03-06__spike-4-android-harness
- Status: DONE
- Created: 2026-03-06
- Last Updated: 2026-03-06
- Owner: UNCONFIRMED

## Purpose / Big Picture

Spike 4 exists to prove that the accepted outcomes from Spike 1, Spike 2, and Spike 3 can run together on Android as one coherent end-to-end flow. After this work is complete, an operator can prepare `docs/spikes/.env.local`, connect one Android device or emulator, run one repo-level command from `docs/spikes`, and inspect the refreshed artifact directory under `docs/spikes/fixtures/generated/spike-4/run-artifacts/latest/`.

The useful result is not a reusable product module and it is not a UI prototype. The useful result is an Android-targeted evidence harness plus a reviewed post-run handoff document at `docs/spikes/outputs/spike-4-integration.md`. Spike 4 now proves exact-path resolution, remote ZIP enumeration of an exact `.zip` container, ignore filtering before visible selection, selected-only internal-file copy, optional single-pass local unarchive, archive cleanup after successful extraction, final-file rename, and stage-labeled diagnostics. Recursive unarchive is explicitly out of scope for this spike.

## Progress

- [x] (2026-03-06T00:00Z) Created this execution plan in `docs/spikes/apps/spike-4-android/PLAN.md`.
- [x] (2026-03-06T00:00Z) Re-read the accepted Spike 1, Spike 2, and Spike 3 output docs plus the Spike 4 spec, harness README, fixture contract, and behavior docs to ground this plan in the current repository state.
- [x] (2026-03-06T00:00Z) Chose the implementation shape for the spike harness: standalone Android project, minimal host activity, instrumentation-first connected matrix, repo-level artifact pullback, and no dependency on the production `:app` module.
- [x] (2026-03-06T00:00Z) Scaffolded the standalone Android project under `docs/spikes/apps/spike-4-android/`, including the local Gradle build, minimal host activity, and dedicated `app` module.
- [x] (2026-03-06T00:00Z) Implemented host-side runtime-config staging from `docs/spikes/.env.local` into generated androidTest assets without persisting the Real-Debrid token under `docs/spikes/fixtures/generated/`.
- [x] (2026-03-06T00:00Z) Implemented the integrated resolver, remote-ZIP, queue-task, local-unarchive, cleanup, rename, diagnostics, and artifact-writing pipeline under `app/src/androidTest/java/com/romulus/spikes/spike4/`.
- [x] (2026-03-06T00:00Z) Reduced the run matrix to the non-recursive scope: Run A proves archive-selection with `unarchive=true`, Run B proves selected-only copy with `unarchive=false`, and Run C proves deterministic selection failure.
- [x] (2026-03-06T00:00Z) Wired the shared `docs/spikes/justfile` recipe and updated `docs/spikes/apps/spike-4-android/README.md` to match the current harness surface.
- [x] (2026-03-06T00:00Z) Verified the standalone harness build wiring locally with `./gradlew -p docs/spikes/apps/spike-4-android :app:assembleDebug :app:assembleDebugAndroidTest` and `./gradlew -p docs/spikes/apps/spike-4-android :app:lintDebug`.
- [x] (2026-03-06T00:00Z) Executed the connected matrix on a real device, refreshed the reviewed artifact bundle at `docs/spikes/fixtures/generated/spike-4/run-artifacts/latest/`, and authored `docs/spikes/outputs/spike-4-integration.md`.
- [x] (2026-03-06T00:00Z) Removed recursive unarchive from the Spike 4 implementation, spec, and harness docs after the user moved that capability out of Spike 4 scope.

## Surprises & Discoveries

- Observation: the current generated Spike 4 readiness material is intentionally human-readable only and does not persist the Real-Debrid token.
  Evidence: `docs/spikes/fixtures/generated/spike-4/user-input.md` shows presence and sanitized values, while `docs/spikes/fixtures/README.md` explicitly says fixture generation does not print or persist the token value.

- Observation: Spike 4 is no longer blocked on Spike 1-3 research uncertainty because the repo already contains accepted implementation-handoff docs for all three prerequisite spikes.
  Evidence: `docs/spikes/outputs/spike-1-resolution.md`, `docs/spikes/outputs/spike-2-unarchive.md`, and `docs/spikes/outputs/spike-3-remote-zip.md` are present and now read as implementation-first guides rather than spike receipts.

- Observation: Spike 4 cannot read repo-local fixtures or `.env.local` directly at runtime because the harness must execute on Android, while the canonical inputs and artifacts live in the host repository tree.
  Evidence: `docs/spikes/specs/spike-4-integration.md` requires a focused Android app, and the earlier Spike 2 harness already needed host-to-device staging plus device-to-host artifact pullback for the same reason.

- Observation: the initial connected-matrix Gradle task left stale host artifacts because the pull step ran before instrumentation rather than after it.
  Evidence: the clean rerun showed fresh non-recursive run IDs on-device and via manual pull, while the repo-local `latest/` bundle still held the old recursive run IDs until task ordering was corrected.

## Decision Log

- Decision: keep this plan at `docs/spikes/apps/spike-4-android/PLAN.md`.
  Rationale: the user explicitly asked for the Spike 4 execution plan in the harness folder, and the existing spike-local plan pattern is already established for Spike 1, Spike 2, and Spike 3.
  Date/Author: 2026-03-06 / USER + Codex

- Decision: build Spike 4 as a standalone disposable Android project rooted under `docs/spikes/apps/spike-4-android/`, with one minimal `app` module and no dependency on the production root `:app` module.
  Rationale: the spike must be isolated, deletable, and small enough to serve as evidence harness code rather than migration implementation.
  Date/Author: 2026-03-06 / Codex

- Decision: drive Spike 4 through connected instrumentation instead of building a richer in-app UI flow.
  Rationale: the Spike 4 spec requires a focused Android app and target-runtime proof, but it does not require product-grade UI. Instrumentation is the smallest Android surface that can exercise live network calls, on-device storage, and post-run artifact capture.
  Date/Author: 2026-03-06 / Codex

- Decision: stage runtime config as generated androidTest assets instead of reading `.env.local` directly from the device.
  Rationale: Android instrumentation cannot read the repo workspace path directly, and the token must not be copied into `docs/spikes/fixtures/generated/`.
  Date/Author: 2026-03-06 / Codex

- Decision: treat the outer provider `.zip` as a transport container rather than a user-visible queue row when internal archive-selection mode is active.
  Rationale: the behavior contract and Spike 3 outputs require one queue-task-like identity per selected internal file, not one row for the outer `.zip`.
  Date/Author: 2026-03-06 / Codex

- Decision: keep duplicate-safe internal entry identity by carrying `entryPath`, `localHeaderOffset`, compressed size, uncompressed size, and CRC32.
  Rationale: Spike 3 already proved filename alone is insufficient for exact internal-entry identity.
  Date/Author: 2026-03-06 / Codex

- Decision: use `Commons Compress` for metadata-first remote ZIP enumeration and selected-entry extraction, and `7-Zip-JBinding-4Android` for local archive extraction.
  Rationale: those dependency choices were already accepted through Spike 3 and Spike 2 respectively, and Spike 4 is supposed to compose accepted outcomes rather than reopen library selection.
  Date/Author: 2026-03-06 / Codex

- Decision: keep recursive unarchive out of scope for Spike 4.
  Rationale: the user explicitly removed recursive behavior after reviewing the live fixture state, so the spike now proves only the single-pass integrated archive-selection path.
  Date/Author: 2026-03-06 / USER + Codex

- Decision: make `spike4ConnectedMatrix` pull artifacts after instrumentation completes.
  Rationale: the repo-level operator command must refresh `docs/spikes/fixtures/generated/spike-4/run-artifacts/latest/` with the actual run that just completed.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

As of 2026-03-06, the Spike 4 harness is implemented and verified for the current scope. The harness builds and lints, the connected matrix runs on a real Android device, and the reviewed output doc exists at `docs/spikes/outputs/spike-4-integration.md`.

The integrated pipeline is now proven through resolver, remote-ZIP archive selection, selected-only local copy, optional single-pass unarchive, cleanup, rename, diagnostics, and deterministic failure capture. The repo-level connected run now refreshes the host artifact bundle after instrumentation, so the default operator command and the reviewed `latest/` directory are aligned.

Recursive archive extraction is intentionally not part of the Spike 4 contract anymore. Any future recursive work would require a new scope decision and a new spike or follow-on proof, not a hidden expansion of this completed harness.
