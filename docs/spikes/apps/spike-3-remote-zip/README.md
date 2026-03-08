# Spike 3 App - Remote Zip Harness

## 1. Purpose

1. This folder now contains the standalone Spike 3 desktop harness implementation.
2. The spec defines what Spike 3 must prove; this doc defines the actual local project shape, command surface, and artifact outputs inside the owned folder.

## 2. Workspace Shape

1. The harness is rooted in `docs/spikes/apps/spike-3-remote-zip/` as a standalone nested Gradle build.
2. It is a Kotlin/JVM desktop project, separate from the Android app.
3. It reads generated fixtures from `docs/spikes/fixtures/generated/spike-3/http-root/`.
4. It writes runtime evidence to `docs/spikes/fixtures/generated/spike-3/run-artifacts/`.

## 3. Implemented Split

1. `server` is a tiny JDK `HttpServer` that serves the generated Spike 3 ZIP fixtures.
2. `runner` executes the full Spike 3 matrix, performs remote ZIP enumeration plus selected-entry reads, and writes run artifacts.
3. The runner uses OkHttp for `HEAD` plus `Range` requests and Commons Compress with `ignoreLocalFileHeader=true` for metadata-first ZIP enumeration.
4. No fallback full-download path exists in this harness.

## 4. Actual Command Surface

1. Prepare fixtures first with `cd docs/spikes && just fixtures`.
2. Default repo-level operator command: `cd docs/spikes && just spike-3`.
3. Default server-only operator command: `cd docs/spikes && just spike-3-server`.
4. Raw debug run from the repo root: `./gradlew -p docs/spikes/apps/spike-3-remote-zip test runSpike3Matrix`.
5. Raw server-only run from the repo root: `./gradlew -p docs/spikes/apps/spike-3-remote-zip runSpike3Server`.
6. Override the default server port `8788` with `SPIKE3_SERVER_PORT` when needed.
7. The latest verified full run wrote `docs/spikes/fixtures/generated/spike-3/run-artifacts/2026-03-06T21-01-11Z/`.

## 5. Artifact Layout

1. Each matrix run creates a timestamped directory under `docs/spikes/fixtures/generated/spike-3/run-artifacts/`.
2. Each run directory contains `summary.md`.
3. Each run directory contains `http-trace.ndjson`.
4. Each enumeration case writes `cases/<case-id>/candidate-set.md`.
5. The selected-download case writes `cases/run-d-selected-only-duplicate/download-manifest.md`.
6. The selected-download case writes its payload files under `cases/run-d-selected-only-duplicate/downloads/`.
7. Each failure case writes `cases/<failure-case>/failure.md`.
8. The duplicate-selection proof writes `0000000315__sample_text_large.txt` and `0000005450__sample_text1.txt` and does not write the unselected duplicate at offset `0`.

## 6. Shared Workspace Boundary

1. The repo-level operator recipes `spike-3` and `spike-3-server` live in coordinator-owned shared file `docs/spikes/justfile`.
2. This folder owns the nested harness and its raw Gradle tasks, not the shared workspace wiring.
3. The shared fixture workflow continues to prepare Spike 3 without requiring `.env.local`.

## 7. Boundary With the Spec

1. Keep externally meaningful execution facts in [`../../specs/spike-3-remote-zip.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-3-remote-zip.md): goal, inputs, run matrix, evidence requirements, and pass or fail gate.
2. Keep harness-specific guidance here: local project layout, actual Gradle tasks, port behavior, artifact tree, and coordinator-owned shared-workspace follow-ups.
3. Shared workspace files such as `docs/spikes/justfile` and the final post-run output doc under `docs/spikes/outputs/` remain outside this folder's ownership.
