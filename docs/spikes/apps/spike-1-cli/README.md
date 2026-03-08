# Spike 1 CLI Harness

## 1. Purpose

1. This folder now contains the disposable Spike 1 Kotlin/JVM CLI implementation.
2. The harness exists to execute the Spike 1 Real-Debrid characterization work, capture artifacts, and inform the later architecture decision.
3. It is not production app code and it is intentionally isolated from the Android build.

## 2. Project Layout

1. `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties` define a nested standalone Gradle application build.
2. `src/main/kotlin/com/romulus/spikes/spike1/` contains the CLI entrypoint, config loading, matrix planning, Real-Debrid client, runner, artifact writer, and gate evaluation.
3. `src/test/kotlin/com/romulus/spikes/spike1/` contains offline unit tests for env parsing, selection planning, artifact writing, and gate evaluation.

## 3. Dependency Stack

1. Runtime dependencies are locked to Kotlin/JVM 17 bytecode targeting, `kotlinx-coroutines-core`, `OkHttp`, `Retrofit`, `kotlinx-serialization-json`, and `retrofit2-kotlinx-serialization-converter`.
2. There is no CLI parsing library, dependency-injection framework, persistence layer, or Android-only dependency in this harness.

## 4. Implemented Behavior

1. Loads `docs/spikes/.env.local` by default and also accepts one optional positional env-file path override.
2. Validates the current likely-cached Spike 1 fields from `docs/spikes/.env.example`.
3. Executes the likely-cached profile matrix in provider-safe order: `run-b-add-directory`, `run-a-add-root`, `run-c-add-exact-zip`, `run-d-selected-only-download`, `run-e-deterministic-failure`.
4. Does not block on pre-existing matching Real-Debrid torrents; add-again is the working lifecycle.
5. Reuses the host cached by run `B` for the later add-again runs and for the deterministic invalid-magnet failure run.
6. Characterizes changed selection by adding the same magnet again for runs `A` and `C` instead of trying to reselect on the already-selected torrent id from an earlier run.
7. Reuses the exact-path torrent from run `C` for run `D` so selected-only download proof happens against an already-verified selected state.
8. Verifies selection by comparing requested provider file IDs with the provider-selected file set after polling.
9. Once returned links exist, treats those links as the downstream unit instead of assuming file-identity mapping from link count or order.
10. Uses a longer bounded link-ready wait of 120 polls at 2 seconds each and writes the last polled torrent info plus polling summary when a run times out before links appear.
11. Writes timestamped artifacts under `docs/spikes/fixtures/generated/spike-1/run-artifacts/`.
12. Spike 1 is now documented as a three-stage Real-Debrid lifecycle:
    - selection stage,
    - provider-acquisition stage,
    - link stage.
13. The current implementation proves the likely-cached provider-acquisition profile and now also implements a resumable known-uncached profile over that same runtime flow.
14. The known-uncached profile persists provider-acquisition timelines, lifecycle markers, and a resume checkpoint instead of relying on one long blocking process.

## 5. Local Commands

1. Verified local build and test command from the repo root:

       ./gradlew -p docs/spikes/apps/spike-1-cli --no-daemon test installDist

2. Local debug run from the repo root once `docs/spikes/.env.local` is populated:

       ./gradlew -p docs/spikes/apps/spike-1-cli --no-daemon run

3. Local debug run with an explicit env-file override:

       ./gradlew -p docs/spikes/apps/spike-1-cli --no-daemon run --args='/absolute/path/to/.env.local'

4. Current operator surface for the two profiles:
   - keep `just spike-1` for the likely-cached profile matrix,
   - use `just spike-1-uncached` for the known-uncached profile.

## 6. Shared Workspace Boundary

1. The default operator surface is `cd docs/spikes && just spike-1`.
2. `spike-1-build`, `spike-1`, `spike-1-uncached`, `spike-1-clean`, and `spike-1-delete` live in coordinator-owned shared file `docs/spikes/justfile`.
3. The likely-cached and known-uncached profiles share the same runtime model; separate operator commands are only a spike convenience.
4. The raw Gradle commands above remain useful for local debugging when the shared operator surface is not needed.

## 7. Artifacts

1. Each CLI invocation creates a new timestamped directory under `docs/spikes/fixtures/generated/spike-1/run-artifacts/` plus updates `latest-run.txt`.
2. Likely-cached invocation roots contain `sanitized-inputs.md`, `matrix-summary.md`, and `matrix-summary.json`.
3. Known-uncached invocation roots contain `sanitized-inputs.md`, `known-uncached-summary.md`, and `known-uncached-summary.json`.
4. Each run directory contains `trace.jsonl`, selection artifacts, provider file snapshots, `last-polled-torrent-info.json`, `polling-summary.json`, link-land records, and `result.md`.
5. Known-uncached run directories also contain `provider-acquisition-timeline.json`, `lifecycle-markers.json`, and `checkpoint.json`.
6. `run-d-selected-only-download` also writes `downloads/` and `download-manifest.json`.
7. `run-e-deterministic-failure` also writes `error.json`.

## 8. Verification Status

1. Offline verification is complete: `./gradlew -p docs/spikes/apps/spike-1-cli --no-daemon test installDist` passes.
2. Latest live likely-cached Real-Debrid execution at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T185325Z/` passed the full current matrix:
   - `run-b-add-directory: PASS`
   - `run-a-add-root: PASS`
   - `run-c-add-exact-zip: PASS`
   - `run-d-selected-only-download: PASS`
   - `run-e-deterministic-failure: EXPECTED_FAILURE`
   - `matrix gate: PASS`
3. That passing rerun confirmed the longer 120x2s link-ready budget was sufficient for the exact-zip add-again case. In that successful run, `run C` reached returned links on the first post-selection poll.
4. Latest live known-uncached Real-Debrid execution at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T205927Z/` passed the implemented resume-aware provider-acquisition profile:
   - `status: PASS`
   - `torrent id: 33TWDIWDYJKRW`
   - `selected path: /X Squad (USA).chd`
   - `last status: downloaded`
   - `last progress: 100.0`
5. That successful known-uncached run proved the resume path and final link-ready transition, but the final artifact bundle only captured a single `downloaded/100%` provider sample after resume rather than a long multi-sample progress curve.
6. The post-run conclusion doc is now written at [`../../outputs/spike-1-resolution.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/spike-1-resolution.md). It serves as the migration-facing Real-Debrid integration guide and separates product behavior from provider-acquisition profile.
7. `cd docs/spikes && just spike-1-delete` now clears all existing RD torrents matching the currently configured Spike 1 likely-cached and known-uncached magnets from `.env.local`.

## 9. References

1. [`../../specs/spike-1-resolution-enumeration.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-1-resolution-enumeration.md)
2. [`../../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md)
3. [`../../../architecture/SPIKE.md`](/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md)
