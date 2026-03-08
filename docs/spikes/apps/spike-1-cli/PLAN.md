# Spike 1 CLI Execution Plan

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `.agent/PLANS.md`. It intentionally lives beside the Spike 1 harness docs in `docs/spikes/apps/spike-1-cli/` so a future coding agent can implement the disposable Spike 1 app from this folder alone.

- Plan ID: EP-2026-03-06\_\_spike-1-cli
- Status: DONE
- Created: 2026-03-06
- Last Updated: 2026-03-06
- Owner: UNCONFIRMED

## Purpose / Big Picture

After this work is fully implemented, an operator will be able to fill `docs/spikes/.env.local`, run one repo-level command from `docs/spikes` for the likely-cached profile matrix, and run one separate explicit command for the known-uncached provider-acquisition profile. The useful result is not “the code compiles”; the useful result is timestamped evidence under `docs/spikes/fixtures/generated/spike-1/run-artifacts/` that proves one Real-Debrid runtime flow across two product behaviors (`select all` and `subset selected`) and two provider-acquisition profiles (`likely-cached` and `known-uncached`).

A novice should be able to confirm the likely-cached CLI profile is working by running `just spike-1`, then opening the newest run directory and finding five run folders, a matrix summary, selection payload records, link-mapping records, and a download proof that only the chosen file was fetched. The later known-uncached profile should add provider-acquisition timelines showing `status`, `progress`, `speed`, `seeders`, and first link-ready timing over a long-running Real-Debrid wait. Those artifacts are the raw material for the required post-run conclusion document `docs/spikes/outputs/spike-1-resolution.md`, which is intended to be the migration-facing Real-Debrid integration guide.

## Progress

- [x] (2026-03-06T00:00Z) Created this execution plan in `docs/spikes/apps/spike-1-cli/PLAN.md`.
- [x] (2026-03-06T12:00Z) Scaffolded the isolated Gradle/Kotlin JVM project in `docs/spikes/apps/spike-1-cli/` without wiring it into the root Android build.
- [x] (2026-03-06T12:15Z) Implemented env loading, config validation, workspace-path discovery, info-hash parsing, and matrix planning for the likely-cached Spike 1 inputs already defined in `docs/spikes/.env.example`.
- [x] (2026-03-06T12:35Z) Implemented the Real-Debrid client, selection mapping, bounded status polling, deterministic invalid-magnet handling, and trace capture for add, add-again, select, and unrestrict flows.
- [x] (2026-03-06T12:45Z) Implemented artifact writing under `docs/spikes/fixtures/generated/spike-1/run-artifacts/`, including timestamped invocation roots, per-run trace files, and selected-only download manifests.
- [x] (2026-03-06T17:18Z) Coordinated the repo-level `docs/spikes/justfile` recipes that build, run, clean, and delete current Spike 1 RD torrents for the Spike 1 workflow.
- [x] (2026-03-06T13:05Z) Validated the harness with the shared `just spike-1*` operator surface and a live Real-Debrid fixture, then authored `docs/spikes/outputs/spike-1-resolution.md` from the passing artifact bundle at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T185325Z/`.
- [x] (2026-03-06T17:55Z) Revised the harness contract after the first live multi-file mismatch: verify selection by provider-selected IDs, persist post-selection state on failure, and treat returned links as downstream units rather than assuming file identity from link count or order.
- [x] (2026-03-06T18:15Z) Reworked the lifecycle proof so Run A and Run C add the same magnet again while earlier provider torrents still exist, then reran live Spike 1. Result: Run A proved add-again changed selection, while Run C still failed because the exact-zip selection never produced returned links within the current poll window.
- [x] (2026-03-06T21:15Z) Extended the fixture contract and generated readiness docs for the uncached provider-acquisition phase inputs: `SPIKE1_UNCACHED_MAGNET` and `SPIKE1_UNCACHED_SELECTED_PATH`.
- [x] (2026-03-06T21:15Z) Added the explicit known-uncached provider-acquisition profile command, profile-aware CLI parsing, timeline artifacts, lifecycle markers, and resume checkpoint handling without treating long-running provider work as a short-timeout failure.
- [x] (2026-03-06T21:10Z) Executed the known-uncached provider-acquisition characterization against the user-provided uncached fixture, proved the resume-aware path at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T205927Z/`, and folded the result back into `docs/spikes/outputs/spike-1-resolution.md`.

## Surprises & Discoveries

- Observation: the repository root Gradle build is currently Android-only and includes only `:app`, so Spike 1 must stay as a nested, isolated Gradle build rather than a new root subproject.
  Evidence: `settings.gradle.kts` contains `include(":app")` and nothing for `docs/spikes/apps/spike-1-cli`.

- Observation: the spike workspace already has a clear local-input and generated-artifact contract, so the CLI must consume `docs/spikes/.env.local` and write only under `docs/spikes/fixtures/generated/` instead of inventing a second config or output location.
  Evidence: `docs/spikes/.env.example`, `docs/spikes/fixtures/README.md`, and `docs/spikes/justfile` already define that workflow.

- Observation: the Spike 1 spec names runs `A` through `E`, but the safest execution order is not alphabetical because the initial add flow should seed the later add-again characterization and the downstream reuse proof.
  Evidence: `docs/spikes/specs/spike-1-resolution-enumeration.md` requires initial add, changed-selection characterization, and downstream reuse proof, while the current env schema supplies one magnet and one set of selection inputs.

- Observation: the exact-zip add-again path can remain alive in `queued` and `downloading` longer than the original 60-second link-ready window without reaching a terminal failure state.
  Evidence: live run artifacts under `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T183110Z/run-c-add-exact-zip/` and the user's direct Real-Debrid account observation that the files later became downloadable.

- Observation: Real-Debrid `progress` can arrive as a decimal value, and transient malformed or truncated poll responses can occur during long-running provider acquisition.
  Evidence: the first known-uncached live run failed on JSON parsing until `progress` was widened from `Int` to `Double`, and later polling hardening was needed before the successful resume-aware run at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T205927Z/`.

- Observation: a nested build with `dependencyResolutionManagement.repositoriesMode = FAIL_ON_PROJECT_REPOS` cannot also declare `repositories { mavenCentral() }` in `build.gradle.kts`.
  Evidence: the first nested Gradle verification failed until the project-level `repositories` block was removed.

- Observation: the host machine did not provide a discoverable local Java 17 toolchain, but Gradle could still build the nested CLI cleanly by compiling JVM 17 bytecode with the available Java 21 runtime.
  Evidence: `./gradlew -p docs/spikes/apps/spike-1-cli --no-daemon test installDist` initially failed with `Cannot find a Java installation ... matching {languageVersion=17}`, then passed after replacing `jvmToolchain(17)` with JVM 17 target compatibility settings.

## Decision Log

- Decision: keep this plan at `docs/spikes/apps/spike-1-cli/PLAN.md` instead of the normal agent plan area.
  Rationale: the user explicitly asked for a self-contained Spike 1 app-local draft plan, and the future implementer should find the plan in the same folder as the harness README.
  Date/Author: 2026-03-06 / USER

- Decision: treat this as an executable plan for a disposable desktop harness, not production app code and not an Android module.
  Rationale: Spike 1 is a bounded feasibility exercise under `docs/spikes`, and `docs/architecture/SPIKE.md` explicitly says Spike 1 should run as a desktop CLI.
  Date/Author: 2026-03-06 / USER

- Decision: lock the runtime stack to Kotlin/JVM 17, `kotlinx-coroutines-core`, `okhttp`, `retrofit`, `kotlinx-serialization-json`, and `retrofit2-kotlinx-serialization-converter`, with no CLI parsing library, no dependency-injection framework, and no persistence layer.
  Rationale: the user locked these choices up front; the plan must remove dependency ambiguity rather than reopen it.
  Date/Author: 2026-03-06 / USER

- Decision: implement Spike 1 as a nested single-project Gradle build rooted at `docs/spikes/apps/spike-1-cli/`, and do not edit the root `settings.gradle.kts` or root `build.gradle.kts`.
  Rationale: the spike harness must stay isolated from the Android app while still remaining inside the repository.
  Date/Author: 2026-03-06 / CODE

- Decision: make `docs/spikes/justfile` the primary operator surface. The intended day-to-day command is `cd docs/spikes && just spike-1`.
  Rationale: the harness README already says repo-level operator commands should be the default path, and that keeps the spike workflow aligned with the existing `just fixtures` setup.
  Date/Author: 2026-03-06 / CODE

- Decision: keep shared spike workspace files such as `docs/spikes/justfile` under coordinator ownership rather than Spike-1-agent ownership.
  Rationale: Spike 1, Spike 2, and Spike 3 all need shared workspace command wiring. Centralizing those edits avoids agent collisions while still letting this plan define the command contract Spike 1 needs.
  Date/Author: 2026-03-06 / CODE

- Decision: satisfy the matrix in provider-safe execution order: run `B` (initial add + directory scope) first, then run `A` (add again + root scope with different selection), then run `C` (add again + exact path), then run `D` (reuse the exact-path torrent for selected-only download proof), and finally run `E` (deterministic failure).
  Rationale: one valid magnet is enough if the first add establishes the account state, later runs characterize add-again behavior under an already-existing hash, and the final download proof reuses the exact-path torrent without changing selection. The artifact manifest must record both the spec label and the actual execution order.
  Date/Author: 2026-03-06 / CODE

- Decision: use the exact-zip selection for run `D` so the selected-only download proof is small, easy to inspect, and cheap to rerun.
  Rationale: `docs/spikes/.env.example` already provides `SPIKE1_EXACT_ZIP_PATH`, and a one-file proof makes it obvious that no unselected file was unrestrict-called or downloaded.
  Date/Author: 2026-03-06 / CODE

- Decision: do not add hidden mismatch-recovery behavior such as delete-and-readd when selected files and returned links do not align.
  Rationale: this spike is supposed to capture evidence. If link mapping is ambiguous or unstable, the harness should record the mismatch and continue non-download runs by operating on returned links directly instead of inventing a recovery flow or pretending the mapping is proven.
  Date/Author: 2026-03-06 / CODE

- Decision: treat `files[].selected` after polling as the source of truth for whether the provider accepted the requested selection, and treat `links[]` as a separate downstream stage.
  Rationale: the first live multi-file run showed that selected provider file count and returned link count can diverge even when selection succeeds. The harness must therefore verify selection and link-land separately to preserve the migration lesson.
  Date/Author: 2026-03-06 / CODE

- Decision: before trying provider delete-and-readd semantics for changed selections, first characterize whether adding the same magnet again while earlier provider torrents already exist is enough to create a fresh usable selection state.
  Rationale: the user explicitly asked to try the non-destructive provider path first. The spike should prove or disprove add-again behavior before it escalates to explicit delete semantics.
  Date/Author: 2026-03-06 / USER

- Decision: keep the add-again lifecycle for Spike 1 and deepen only the exact-zip characterization by extending the bounded link-ready wait to 120 polls at 2 seconds each while persisting the last polled torrent info and polling summary on timeout.
  Rationale: live evidence now shows run `C` remained active beyond the prior 60-second window, so the next step is to lengthen the bounded wait rather than switch to delete-and-readd.
  Date/Author: 2026-03-06 / USER

- Decision: formalize the Real-Debrid lifecycle in three stages: selection, provider acquisition, and link handling.
  Rationale: the likely-cached profile proved selection and returned links are separate stages, and the next known-uncached characterization needs an explicit middle stage instead of treating link readiness as immediate.
  Date/Author: 2026-03-06 / USER

- Decision: keep `just spike-1` as the default likely-cached profile command and place the known-uncached provider-acquisition characterization behind a separate explicit operator command.
  Rationale: the runtime model is the same, but the likely-cached matrix should remain quick and repeatable while the known-uncached profile may stay active for hours and needs its own operator control and artifact semantics.
  Date/Author: 2026-03-06 / USER

- Decision: treat the Real-Debrid 72-hour auto-delete rule as a working runtime assumption with `[USER]` provenance from the Real-Debrid UI, not as an official API guarantee.
  Rationale: the user directly observed the warning in the Real-Debrid UI, and the long-running provider-acquisition docs need an upper bound even though the official API docs inspected so far do not state it.
  Date/Author: 2026-03-06 / USER

- Decision: keep `select all` as one provider torrent and model subset selection as one provider torrent per selected file for migration planning, while allowing internal grouping of provider file IDs if later evidence requires it for one user-selected file.
  Rationale: this user-approved strategy maximizes the chance that narrowly-selected content builds reusable provider cache over time while avoiding same-id reselection assumptions, without forcing the product behavior to mirror every provider-specific file-ID quirk.
  Date/Author: 2026-03-06 / USER

- Decision: keep explicit operational guidance limited to the official Real-Debrid API rate limit of 250 requests per minute.
  Rationale: the docs should warn against abusive polling, but they should not invent speculative concurrency policy before the known-uncached profile is actually characterized.
  Date/Author: 2026-03-06 / USER

- Decision: exit with code `0` only when runs `A` through `D` pass, run `E` records the expected deterministic failure, and the gate summary can answer every required evidence question or mark it `UNCONFIRMED` for a stated reason.
  Rationale: the operator needs a single success signal that matches the spec’s pass/fail gate.
  Date/Author: 2026-03-06 / CODE

- Decision: compile the nested project to JVM 17 bytecode without requiring a local Java 17 toolchain.
  Rationale: the spike stack remains Kotlin/JVM 17, but the available host runtime was Java 21 without a discoverable Java 17 installation. Switching from Gradle toolchain enforcement to JVM 17 target compatibility kept the owned project buildable without touching host machine state.
  Date/Author: 2026-03-06 / CODE

## Outcomes & Retrospective

The owned Spike 1 folder now contains a working nested Kotlin/JVM CLI project with offline unit tests and a verified `installDist` output. The implemented code covers env loading, workspace-path discovery, info-hash parsing, matrix planning, Real-Debrid API integration, selection resolution, trace capture, artifact generation, gate evaluation, and selected-only download verification. After the first live multi-file run, the harness was also revised so it verifies selection through the provider-selected file set and treats returned links as downstream units instead of assuming selected-file-to-link count equality.

The likely-cached and known-uncached profiles are now both proven at the harness level. The known-uncached live run confirmed the resumable provider-acquisition path and final link-ready transition at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T205927Z/`. That successful uncached fixture resumed into a final `downloaded/100%` state, so the artifact bundle proves resume semantics and final readiness. Any deeper Real-Debrid characterization would be follow-on research, not unfinished work in this completed spike plan.

## Context and Orientation

Spike 1 is defined by `docs/spikes/specs/spike-1-resolution-enumeration.md`. That spec owns the behavior contract: the goal, the inputs, the required Real-Debrid endpoint sequence, the evidence questions, the run matrix, the trace artifacts, and the pass/fail gate. The harness README in `docs/spikes/apps/spike-1-cli/README.md` owns the intended workspace location, the desired build shape, the preferred dependency shape, the repo-level command surface, and the artifact root.

The fixture workflow already exists. `docs/spikes/.env.example` defines the operator inputs, `docs/spikes/fixtures/README.md` explains how `.env.local` and generated fixture material work, and `docs/spikes/justfile` already exposes `init-env` and `fixtures`. The CLI must fit into that workflow rather than replacing it. The likely-cached profile uses the existing Spike 1 fields. The known-uncached profile extends that same env contract with `SPIKE1_UNCACHED_MAGNET` and `SPIKE1_UNCACHED_SELECTED_PATH`.

The current `docs/spikes/apps/spike-1-cli/` directory now contains the nested Gradle build, Kotlin sources, and offline tests for the disposable CLI. The generated run evidence belongs under `docs/spikes/fixtures/generated/spike-1/run-artifacts/`. The human-written post-run conclusion belongs in `docs/spikes/outputs/spike-1-resolution.md` after a successful live spike run.

The root Android build is not the place to wire this spike. `settings.gradle.kts` includes only `:app`, so this plan keeps the spike as a separate Gradle build with its own `settings.gradle.kts` and `build.gradle.kts` inside `docs/spikes/apps/spike-1-cli/`.

The implementation guided by this plan should touch these repository paths:

- `docs/spikes/apps/spike-1-cli/settings.gradle.kts`
- `docs/spikes/apps/spike-1-cli/build.gradle.kts`
- `docs/spikes/apps/spike-1-cli/gradle.properties`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/Main.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/config/Spike1Config.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/config/EnvFileLoader.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/config/WorkspacePaths.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/model/RealDebridModels.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/model/RunModels.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/MatrixPlanner.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/SelectionPlanner.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/MagnetInfoHash.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/RealDebridApi.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/RealDebridClient.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/ArtifactWriter.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/JsonSupport.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/GateEvaluator.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/DownloadVerifier.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/Spike1Exceptions.kt`
- `docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/Spike1Runner.kt`
- `docs/spikes/apps/spike-1-cli/src/test/kotlin/com/romulus/spikes/spike1/EnvFileLoaderTest.kt`
- `docs/spikes/apps/spike-1-cli/src/test/kotlin/com/romulus/spikes/spike1/SelectionPlannerTest.kt`
- `docs/spikes/apps/spike-1-cli/src/test/kotlin/com/romulus/spikes/spike1/GateEvaluatorTest.kt`
- `docs/spikes/apps/spike-1-cli/src/test/kotlin/com/romulus/spikes/spike1/ArtifactWriterTest.kt`
- `docs/spikes/apps/spike-1-cli/README.md`

If additional files become necessary, keep them inside `docs/spikes/apps/spike-1-cli/` unless the file is part of the already-approved spike operator surface under `docs/spikes/`.

## Plan of Work

### Milestone 1: Scaffold an isolated Kotlin/JVM CLI build

Create a nested Gradle build rooted at `docs/spikes/apps/spike-1-cli/`. Add `settings.gradle.kts` with a small project name such as `spike-1-cli`. Add `build.gradle.kts` that applies `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.serialization`, and `application`. Target JVM 17 bytecode, set `mainClass` to `com.romulus.spikes.spike1.MainKt`, and keep runtime dependencies limited to the locked stack.

Do not add this project to the root build. The root wrapper already exists at `/Users/nskaria/projects/romulus/gradlew`, so the spike can be built with `./gradlew -p docs/spikes/apps/spike-1-cli ...` from the repo root or `../../gradlew -p apps/spike-1-cli ...` from `docs/spikes`.

Use a conventional JVM layout under `src/main/kotlin` and `src/test/kotlin`. The first acceptance point for this milestone is simple: the nested build should answer `tasks`, run `test`, and produce an installable distribution without touching the Android app.

### Milestone 2: Load `.env.local`, validate inputs, and plan the matrix

Implement a tiny env loader because the locked stack excludes a CLI parsing or config library. It only needs to support `.env` lines shaped like `KEY=value` or `KEY="value"`, plus blank lines and `#` comments. Load from `docs/spikes/.env.local` by default when the CLI is invoked from `docs/spikes/apps/spike-1-cli/`. Allow one optional positional argument to override the env-file path for manual debugging, but keep `just spike-1` as the main operator entry.

Create a validated config object from the existing Spike 1 fields in `docs/spikes/.env.example`: `RD_API_TOKEN`, `SPIKE1_MAGNET`, `SPIKE1_ROOT_SELECTED_PATHS`, `SPIKE1_DIRECTORY_SCOPE`, `SPIKE1_DIRECTORY_SELECTED_PATHS`, and `SPIKE1_EXACT_ZIP_PATH`. Preserve the fixture workflow’s existing meaning that blank values are blank, not missing. Fail fast with a readable error if required fields for a run are empty.

The matrix planner should translate the config into five named run cases that match the spec labels while also recording actual execution order. The planner must schedule `run-b-add-directory` first, `run-a-add-root` second, `run-c-add-exact-zip` third, `run-d-selected-only-download` fourth, and `run-e-deterministic-failure` last. The planner must also precompute the local path scope for each run: root means every provider file path is eligible, directory means only provider file paths under `SPIKE1_DIRECTORY_SCOPE` are eligible, and exact path means only the provider file whose `files[].path` exactly equals `SPIKE1_EXACT_ZIP_PATH` is eligible.

### Milestone 3: Implement the Real-Debrid client and selection logic

Add Retrofit interfaces and serialization models for the exact upstream endpoints the spec requires: `GET /torrents`, `GET /torrents/availableHosts`, `POST /torrents/addMagnet`, `GET /torrents/info/{id}`, `POST /torrents/selectFiles/{id}`, and `POST /unrestrict/link`. Configure the base URL as `https://api.real-debrid.com/rest/1.0/`, send the bearer token in every request, and set `Json { ignoreUnknownKeys = true }` so upstream fields can expand without breaking the spike.

Implement a small client service that owns all live provider behavior. It should parse the info hash from `SPIKE1_MAGNET` and then perform the add, add-again, selection, provider-acquisition, and unrestrict flow directly. The Spike 1 contract no longer requires a clean-account preflight blocker. Existing matching torrents for the same hash are compatible with the add-again lifecycle the spike already proved.

For run `B`, fetch available hosts, choose `availableHosts.first().host`, cache that exact host in invocation state, add the magnet, and then poll `GET /torrents/info/{id}` with one bounded immediate-readiness retry loop only. Three attempts with short delays are enough. If no hosts are returned, fail the run immediately and record that failure in the trace.

After run `B` creates the first provider torrent, run `A` and run `C` must add the same magnet again rather than trying to change selection on the existing torrent id. This is how the plan characterizes whether add-again is enough to obtain a different selected state while earlier provider torrents for the same hash still exist in the account. Run `A` must intentionally choose a selected-path set that differs from run `B` so the artifact bundle proves changed selection rather than a no-op. Run `D` should then reuse the exact-path torrent produced by run `C` without changing selection.

Build the file-selection logic from `files[].path`, not from filename heuristics. Root scope matches all files. Directory scope matches exact directory-prefix membership. Exact path matches one file path exactly. For every run, record the full file list before selection, the candidate subset for the run scope, the desired selected paths from the env file, and the final provider file IDs to send to `selectFiles`. For subset runs, always submit the explicit comma-separated provider file IDs. Do not send `files=all` unless the selected candidate set truly contains every visible file under the chosen scope.

After selection, poll `GET /torrents/info/{id}` until the torrent is either ready to unrestrict or has reached a terminal failure state. Record every observed status transition. Verify selection success by comparing the requested provider file IDs with the post-poll provider-selected file set from `files[].selected`. Only that comparison proves whether selection took effect. Once returned `links[]` exist, treat them as the downstream unit. If selected-file count and returned-link count differ, record that mismatch in the artifacts but do not fail non-download runs on that basis alone. For non-download runs, unrestrict the returned links directly. For run `D`, prove selected-only behavior by showing that the verified one-file exact-path selection is the active selected state and that only the links returned from that state are unrestrict-called and downloaded.

Run `E` should not depend on new user input. The CLI should generate a deliberately malformed magnet string locally and submit it through `POST /torrents/addMagnet` using the same cached host that run `B` already proved. Do not call `GET /torrents/availableHosts` again for run `E`. If run `B` never cached a host because the add branch failed earlier, mark run `E` as `SKIPPED_DEPENDENCY`, fail the matrix, and explain that deterministic invalid-magnet proof depends on the earlier successful add setup. Accept provider code `30` or an equivalent explicit invalid-magnet response as success for the failure-proof run, but always write the exact HTTP status, provider code, and message into the run artifacts.

### Milestone 4: Write evidence artifacts and prove selected-only download behavior

Every CLI invocation must create a new timestamped directory under `docs/spikes/fixtures/generated/spike-1/run-artifacts/`. Use a format like `20260306T153000Z/`. Never overwrite an older invocation directory. Overwrite only a tiny pointer file such as `docs/spikes/fixtures/generated/spike-1/run-artifacts/latest-run.txt` so an operator can quickly open the newest run.

Inside the timestamped directory, always write `matrix-summary.md`, `matrix-summary.json`, and `sanitized-inputs.md`. Then create one subdirectory per run with stable names: `run-a-add-root`, `run-b-add-directory`, `run-c-add-exact-zip`, `run-d-selected-only-download`, and `run-e-deterministic-failure`.

Each run directory must contain at least these files:

- `trace.jsonl`: one sanitized event per line, including timestamp, stage name, endpoint path, request method, status code, provider code when present, and a short result summary.
- `provider-files-before-selection.json`: the raw provider file list snapshot before local filtering.
- `candidate-files.json`: the locally filtered candidate set for the run scope.
- `selection-payload.json`: the desired selected paths, the resolved provider file IDs, and the exact form payload string sent to `selectFiles`.
- `provider-files-after-selection.json`: the provider file list after selection and later polling.
- `last-polled-torrent-info.json`: the full last `GET /torrents/info/{id}` snapshot when available, including timeout cases.
- `polling-summary.json`: attempt count, elapsed time, first non-preselection status timing, and link-ready timing or timeout.
- `link-mapping.json`: the requested selected provider file IDs, the provider-selected file set after polling, the returned restricted links, the mapping status, and the exact unrestrict calls made.
- `result.md`: a short human-readable explanation of what this run proved or why it failed.

Add run-specific extras where needed. `run-b-add-directory` should also include `available-hosts.json` and `add-magnet.json`. `run-e-deterministic-failure` should include `error.json`. `run-d-selected-only-download` should include a `downloads/` directory plus `download-manifest.json` listing every file written locally with size and SHA-256.

The selected-only proof is important enough to be explicit. Run `D` must first show that the provider-selected file set after polling still matches the exact one-file request chosen from `SPIKE1_EXACT_ZIP_PATH`. It must then call `unrestrict/link` only for the links returned from that verified state and download only those unrestricted URLs, sequentially, with `okhttp`. Do not download files for the other runs. The proof succeeds only when `download-manifest.json` contains exactly the files fetched from those returned links and no extra files appear under `downloads/`.

### Milestone 5: Wire the repo-level operator surface and update harness docs

Coordinate with the shared spike workspace so `docs/spikes/justfile` can build and run the harness from the spike workspace rather than from the app folder. The required recipe contract is:

- `spike-1-build`: run the nested JVM tests and build tasks with the root wrapper, using `../../gradlew -p apps/spike-1-cli --no-daemon test installDist` from `docs/spikes`.
- `spike-1`: first run `just fixtures`, then run the CLI with the root wrapper, using `../../gradlew -p apps/spike-1-cli --no-daemon run` from `docs/spikes`.
- `spike-1-clean`: delete only local generated Spike 1 outputs under `fixtures/generated/spike-1/run-artifacts/` and the nested build output under `apps/spike-1-cli/build/`.

Keep `spike-1` as the advertised default. Raw Gradle invocation remains allowed for debugging, but the README should describe it as secondary. Update `docs/spikes/apps/spike-1-cli/README.md` once the CLI exists so it stops describing a hypothetical harness and starts describing the real command surface, file layout, and artifact map.

### Milestone 6: Add the uncached provider-acquisition phase

Keep the existing likely-cached matrix intact and add the known-uncached work as the same runtime flow under a different provider-acquisition profile. Extend the env contract with `SPIKE1_UNCACHED_MAGNET` and `SPIKE1_UNCACHED_SELECTED_PATH`. Treat the uncached input as one selected file under a known uncached torrent. Do not overload the existing likely-cached fields for this work.

The known-uncached profile must model three distinct stages:

- selection stage: add the magnet, enumerate provider files, resolve the selected file to provider file IDs, and submit `selectFiles`;
- provider-acquisition stage: poll `GET /torrents/info/{id}` over time and persist `status`, `progress`, `speed`, `seeders`, `ended`, and link readiness;
- link stage: only once returned links are present, call `unrestrict/link` and proceed.

Do not treat long-running provider acquisition as a short-timeout failure. Use a resumable artifact model instead. A future coding agent should add a separate explicit operator command, not because the application path is different, but because the known-uncached characterization profile needs different operator control and artifact semantics. That flow should preserve intermediate provider snapshots so the operator can stop and resume later if the Real-Debrid side remains active for a long time. The docs should treat the 72-hour auto-delete rule as a working upper bound with `[USER]` provenance from the Real-Debrid UI.

The known-uncached profile should also surface the only operational guidance we currently want to own: respect the official Real-Debrid rate limit of 250 requests per minute and avoid abusive polling. The artifact bundle should make it easy to compare the CLI’s recorded progress against the Real-Debrid website during long runs.

## Concrete Steps

The future implementer should work in this order so the project stays easy to verify:

1. Create `settings.gradle.kts`, `build.gradle.kts`, and `gradle.properties` in `docs/spikes/apps/spike-1-cli/`.
2. Add the main package tree under `src/main/kotlin/com/romulus/spikes/spike1/` and create the entry point, config loader, models, matrix planner, Real-Debrid client, selection planner, artifact writer, gate evaluator, and download verifier.
3. Add offline unit tests under `src/test/kotlin/com/romulus/spikes/spike1/` for env parsing, path matching, selection payload construction, artifact writing, and gate evaluation. Keep live provider calls out of `test`.
4. Coordinate the shared `docs/spikes/justfile` changes for `spike-1-build`, `spike-1`, and `spike-1-clean`.
5. Update `docs/spikes/apps/spike-1-cli/README.md` so the implementation and the docs say the same thing.
6. Run the validation commands in the next section, first offline and then against the real account fixture.
7. After a successful live run, write `docs/spikes/outputs/spike-1-resolution.md` from the generated matrix summary and run artifacts.
8. Extend the env contract and artifact contract for the uncached provider-acquisition phase.
9. Add the separate explicit known-uncached operator flow, then run it against a known uncached fixture and update `docs/spikes/outputs/spike-1-resolution.md` so it covers one runtime path across both provider-acquisition profiles.

In this repository, `gradle` and `./gradlew` commands require user approval when executed through Codex because of sandbox rules. A future coding agent should request that approval before running the build commands below.

## Validation and Acceptance

First prove the nested build is healthy without touching Real-Debrid:

    cd /Users/nskaria/projects/romulus/docs/spikes
    ../../gradlew -p apps/spike-1-cli --no-daemon test installDist

Expected evidence: the command exits successfully, unit tests pass, and the distribution appears under `docs/spikes/apps/spike-1-cli/build/install/spike-1-cli/`.

Then prove the repo-level operator surface works once coordinator-owned shared wiring exists:

    cd /Users/nskaria/projects/romulus/docs/spikes
    just spike-1-build

Expected evidence: `just` delegates to the nested Gradle build and succeeds without requiring any manual path juggling. As of 2026-03-06 this step remains blocked because `docs/spikes/justfile` is coordinator-owned and was intentionally not edited from this folder.

Next run the live spike:

    cd /Users/nskaria/projects/romulus/docs/spikes
    just init-env
    just fixtures
    just spike-1

Before `just spike-1`, make sure `.env.local` contains a real `RD_API_TOKEN`, one valid `SPIKE1_MAGNET`, non-empty `SPIKE1_ROOT_SELECTED_PATHS`, a non-empty `SPIKE1_DIRECTORY_SCOPE`, non-empty `SPIKE1_DIRECTORY_SELECTED_PATHS`, and one valid `SPIKE1_EXACT_ZIP_PATH` that points at an archive file in the provider file list.

Expected terminal evidence is short and legible, for example:

    run-b-add-directory: PASS
    run-a-add-root: PASS
    run-c-add-exact-zip: PASS
    run-d-selected-only-download: PASS
    run-e-deterministic-failure: EXPECTED_FAILURE
    matrix gate: PASS
    artifacts: /Users/nskaria/projects/romulus/docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T153000Z

The planned known-uncached provider-acquisition profile is separate only as an operator surface and should not reuse the likely-cached success contract. Once implemented, it should produce long-running evidence rather than a quick matrix summary. Expected evidence should include:

    uncached-phase: IN_PROGRESS or PASS or TERMINAL_FAILURE or INCOMPLETE
    torrent id: <provider torrent id>
    selected file path: <one provider path>
    last status: queued or downloading or compressing or downloaded
    last progress: <percent>
    artifacts: /Users/nskaria/projects/romulus/docs/spikes/fixtures/generated/spike-1/run-artifacts/<timestamp>/uncached-provider-acquisition

Expected filesystem evidence is the newest timestamped run directory plus `latest-run.txt`. The directory should contain `matrix-summary.md`, `matrix-summary.json`, `sanitized-inputs.md`, and all five run folders.

Acceptance for each run is concrete:

- Run `B` passes only if `available-hosts.json`, `add-magnet.json`, `selection-payload.json`, and `link-mapping.json` prove the add branch completed from magnet submission through verified provider selection and returned-link unrestrict.
- Run `A` passes only if it adds the same magnet again while the run-`B` torrent still exists and `selection-payload.json` shows a different selected ID set than run `B`.
- Run `C` passes only if `candidate-files.json` and `selection-payload.json` prove that the local exact path mapped to one provider file ID.
- Run `D` passes only if `download-manifest.json` and the `downloads/` directory contain exactly the chosen file and no extras.
- Run `E` passes only if `error.json` records the expected invalid-magnet failure details.
- The whole CLI passes only if `matrix-summary.md` answers every evidence question from the spec or marks it `UNCONFIRMED` with a reason, and the gate evaluator returns `PASS`.
- The uncached provider-acquisition phase is complete only when it reaches returned links or a terminal provider failure with timeline evidence. If the operator stops it early, it must be marked `INCOMPLETE` and carried forward as partial evidence rather than converted into a false pass or failure.

The final human acceptance step is outside the CLI itself: the operator should use `matrix-summary.md` and the run folders to author `docs/spikes/outputs/spike-1-resolution.md`.

## Idempotence and Recovery

The CLI must be safe to rerun. Each invocation writes to a fresh timestamped directory, so rerunning `just spike-1` must not erase earlier evidence. Overwriting `latest-run.txt` is acceptable because it is only a pointer to the newest run.

The CLI must fail fast on invalid local input before it makes provider requests. Missing token, empty magnet, missing directory scope, empty selection lists, or an exact-path value that cannot be found in the provider file list should all stop the run with a clear error and a partial artifact directory explaining what happened.

If the live run fails halfway through, do not try to surgically reuse the half-written run directory. Leave it in place with a failing `matrix-summary.md`, then start a new timestamped directory on the next invocation. This preserves evidence.

`spike-1-clean` is for local generated files only. It must never call provider delete endpoints or silently mutate the user’s Real-Debrid account. If the operator needs to remove provider torrents created by Spike 1, use the explicit shared helper `just spike-1-delete`, which clears all current Spike 1 likely-cached and known-uncached magnets configured in `.env.local`.

## Artifacts and Notes

The most important artifact is the matrix summary because it ties the raw traces back to the spec. Keep it short and operator-friendly. A good summary layout is:

    Spike 1 Matrix Summary
    Invocation: 20260306T153000Z
    Spec labels executed in order: B, A, C, D, E
    Overall gate: PASS
    Add flow: PROVEN by run-b-add-directory
    Add-again changed selection: PROVEN by run-a-add-root
    Exact path mapping: PROVEN by run-c-add-exact-zip
    Reuse without selection change: PROVEN by run-d-selected-only-download
    Selected-only download proof: PROVEN by run-d-selected-only-download/download-manifest.json
    Deterministic failure: PROVEN by run-e-deterministic-failure/error.json
    Transient failure observation: UNOBSERVED

The trace format should stay compact. One line per event is enough, for example:

    {"timestamp":"2026-03-06T15:30:01Z","stage":"get_torrents","method":"GET","path":"/torrents","status":200,"result":"ok","note":"0 matches before add"}
    {"timestamp":"2026-03-06T15:30:03Z","stage":"add_magnet","method":"POST","path":"/torrents/addMagnet","status":201,"result":"ok","note":"torrentId=abc123"}
    {"timestamp":"2026-03-06T15:30:09Z","stage":"select_files","method":"POST","path":"/torrents/selectFiles/abc123","status":204,"result":"ok","note":"files=4,7"}

Do not store the bearer token in any artifact. Redact authorization headers entirely. It is acceptable to keep provider torrent IDs, file IDs, restricted links, and unrestricted download URLs inside the ignored generated artifact tree because those values are part of the spike evidence, but the human-readable markdown summaries should avoid repeating long URLs when a file path and ID are enough.

## Interfaces and Dependencies

Use only the locked runtime stack. The CLI should be composed by ordinary constructors in `Main.kt`; there is no dependency-injection framework, no service locator, and no persistence layer.

In `docs/spikes/apps/spike-1-cli/build.gradle.kts`, declare a single JVM application with these runtime dependencies: `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `org.jetbrains.kotlinx:kotlinx-serialization-json`, `com.squareup.okhttp3:okhttp`, `com.squareup.retrofit2:retrofit`, and `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter`. Use only minimal test dependencies needed for offline unit tests.

Define these core files and responsibilities:

- `src/main/kotlin/com/romulus/spikes/spike1/Main.kt`: `fun main(args: Array<String>)` and a suspendable `runCli` entry that loads config, builds services, executes the matrix, prints a short summary, and returns the process exit code.
- `src/main/kotlin/com/romulus/spikes/spike1/config/Spike1Config.kt`: a data class that holds the env-backed inputs, plus validation helpers that explain missing or malformed fields in plain English.
- `src/main/kotlin/com/romulus/spikes/spike1/config/EnvFileLoader.kt`: `fun load(path: Path): Map<String, String>` and `fun loadConfig(path: Path): Spike1Config`.
- `src/main/kotlin/com/romulus/spikes/spike1/model/RealDebridModels.kt`: serializable response models for torrent summaries, available hosts, torrent info, torrent files, add-magnet response, and unrestrict response.
- `src/main/kotlin/com/romulus/spikes/spike1/model/RunModels.kt`: in-memory models for `RunCase`, `PathScope`, `SelectionDecision`, `TraceEvent`, `RunResult`, `MatrixSummary`, and `GateResult`.
- `src/main/kotlin/com/romulus/spikes/spike1/service/MatrixPlanner.kt`: `fun plan(config: Spike1Config): List<RunCase>` that emits the five stable run definitions and records both spec label and execution order.
- `src/main/kotlin/com/romulus/spikes/spike1/service/SelectionPlanner.kt`: `fun buildSelection(info: RdTorrentInfo, scope: PathScope, desiredPaths: List<String>): SelectionDecision` and helper functions that normalize provider paths, match directory prefixes, and construct the comma-separated `selectFiles` payload.
- `src/main/kotlin/com/romulus/spikes/spike1/service/RealDebridApi.kt`: the Retrofit interface with one method per required upstream endpoint.
- `src/main/kotlin/com/romulus/spikes/spike1/service/RealDebridClient.kt`: suspend functions such as `findExistingTorrentByHash`, `assertNoExistingTorrentForAdd`, `addMagnetWithFirstAvailableHost`, `pollInfoUntilReady`, `selectFiles`, and `unrestrictReturnedLinks`, each of which emits trace events instead of silently swallowing errors.
- `src/main/kotlin/com/romulus/spikes/spike1/service/ArtifactWriter.kt`: `fun beginInvocation(...)`, `fun writeRunArtifact(...)`, `fun writeMatrixSummary(...)`, and helpers for JSON, markdown, JSONL, and download manifests under `docs/spikes/fixtures/generated/spike-1/run-artifacts/`.
- `src/main/kotlin/com/romulus/spikes/spike1/service/DownloadVerifier.kt`: sequential download helpers for run `D` that save files, compute SHA-256, and build `download-manifest.json`.
- `src/main/kotlin/com/romulus/spikes/spike1/service/GateEvaluator.kt`: `fun evaluate(summary: MatrixSummary): GateResult` that applies the spec’s pass/fail gate and determines the CLI exit code.

Keep the interfaces boring and explicit. A novice should be able to open these files and see where config comes from, where provider calls happen, where file selection happens, and where artifacts are written.

## Plan Revision Notes

- (2026-03-06) Created the first draft execution plan in `docs/spikes/apps/spike-1-cli/PLAN.md`, using the Spike 1 spec, harness README, fixture workflow docs, research notes, and current Gradle layout to define a self-contained implementation path.
- (2026-03-06) Tightened the add-flow proof contract so the deterministic invalid-magnet run reuses the host proven by run `B` instead of re-querying available hosts.
- (2026-03-06) Moved shared spike workspace ownership such as `docs/spikes/justfile` out of the Spike 1 agent boundary so Spike 1-3 can be implemented in parallel without command-surface merge conflicts.
- (2026-03-06) Implemented the nested CLI, offline tests, and artifact pipeline entirely inside `docs/spikes/apps/spike-1-cli/`; verified `./gradlew -p docs/spikes/apps/spike-1-cli --no-daemon test installDist`; and coordinated the shared `docs/spikes/justfile` plus live-fixture follow-up work that was needed before final spike closeout.
- (2026-03-06) Revised the multi-file evidence model after the first live run: selection is now verified by provider-selected IDs, returned links are treated as downstream units, and mismatch between selected-file count and returned-link count is captured as evidence instead of automatic failure for non-download runs.
- (2026-03-06) Revised the lifecycle characterization after the reselection failure: runs `A` and `C` now add the same magnet again instead of reselecting on the existing torrent id. This intermediate rerun proved run `A` and exposed that the exact-zip path needed a longer bounded link-ready wait than the original 60-second poll window.
- (2026-03-06) After user review of the live result and direct RD account observation, extended the exact-zip characterization plan to use a 120x2s bounded link-ready wait and to persist the last polled torrent info plus polling summary on timeout so the next rerun produces stronger evidence.
- (2026-03-06) Verified the revised harness offline and then reran live Spike 1 successfully at `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T185325Z/`; all matrix runs passed except the intentionally expected deterministic failure run, which remained `EXPECTED_FAILURE`.
- (2026-03-06) Authored `docs/spikes/outputs/spike-1-resolution.md` from the passing live artifact bundle and captured the migration-relevant lessons: add-again lifecycle, stage separation between selected files and returned links, and longer bounded link-ready polling for exact-path cases.
