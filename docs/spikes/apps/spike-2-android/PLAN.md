# Implement Spike 2 Android Harness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `.agent/PLANS.md`, but per explicit user direction it lives at `docs/spikes/apps/spike-2-android/PLAN.md` beside the Spike 2 harness README instead of the default ExecPlan location.

- Plan ID: EP-2026-03-06__spike-2-android-harness
- Status: DONE
- Created: 2026-03-06
- Last Updated: 2026-03-06
- Owner: UNCONFIRMED

## Purpose / Big Picture

Spike 2 exists to prove that the target Android runtime can unarchive local `.zip`, `.rar`, and `.7z` files on-device with `7-Zip-JBinding-4Android`, and that the caller-owned policies we care about for Romulus are implementable around that library. The current first device gate is intentionally narrower than the full research surface: prove runtime initialization, single-archive extraction for `zip`, `rar`, and `7z`, flattened outputs, recursion on and off behavior, rename ordering, cleanup outcomes, and at least one deterministic corruption-style failure case. That failure case may be produced by a run-local intentionally broken copy when the checked-in corrupt sample is not deterministic on the target runtime.

The user-visible outcome is not a production feature. It is a disposable Android harness rooted under `docs/spikes/apps/spike-2-android/` that can be built with the repo Gradle wrapper, executed on a connected Android device or emulator, and used to produce evidence under `docs/spikes/fixtures/generated/spike-2/run-artifacts/` for the later post-run output doc `docs/spikes/outputs/spike-2-unarchive.md`.

## Progress

- [x] (2026-03-06) Created this draft plan with locked stack decisions, exact repo paths, intended operator surface, artifact layout, validation commands, and recovery guidance.
- [x] (2026-03-06) Scaffolded the standalone Android project under `docs/spikes/apps/spike-2-android/` and verified it is runnable through `./gradlew -p docs/spikes/apps/spike-2-android`.
- [x] (2026-03-06) Added the minimal Android `app` module, host activity, and instrumentation test entrypoint for the Spike 2 matrix.
- [x] (2026-03-06) Implemented fixture staging from `docs/spikes/fixtures/generated/spike-2/input/` into the test APK and device workspace without touching the production `:app` module.
- [x] (2026-03-06) Implemented `7-Zip-JBinding-4Android` runtime initialization, archive inspection, extraction, flattening, recursion, rename ordering, collision handling, path safety, cleanup recording, and exploratory multipart callbacks.
- [x] (2026-03-06) Wrote host-visible run artifacts under `docs/spikes/fixtures/generated/spike-2/run-artifacts/` with one run-scoped evidence set per matrix entry.
- [x] (2026-03-06) Coordinated the intended operator recipe in `docs/spikes/justfile` and added local Gradle tasks for fixture sync, connected test execution, and artifact pull.
- [x] (2026-03-06) Verified local build wiring with `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:lintDebug`.
- [x] (2026-03-06) Fixed the first connected-device failures by reading fixtures from the instrumentation APK assets, isolating run-local copied inputs, and making `:app:spike2ConnectedMatrix` fail hard when instrumentation reports a real JUnit failure.
- [x] (2026-03-06) Replaced the non-deterministic checked-in corruption sample with a run-local intentionally broken `.7z` copy for Run D so the first-gate failure proof is stable on the target runtime.
- [x] (2026-03-06) Executed the narrowed connected-device matrix with the direct Gradle path `./gradlew -p docs/spikes/apps/spike-2-android :app:spike2ConnectedMatrix`, inspected the pulled host artifact tree under `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/`, and authored `docs/spikes/outputs/spike-2-unarchive.md`.

## Surprises & Discoveries

- Observation: the generated Spike 2 fixtures were correctly packaged into the `androidTest` APK, but the initial device run still failed because fixture staging read assets from the target app context instead of the instrumentation context.
  Evidence: the first connected run failed with `java.io.FileNotFoundException: input` from `Spike2FixtureStager.copyAssetsRecursively`, and the fix was to read assets from `InstrumentationRegistry.getInstrumentation().context`.

- Observation: the generated Spike 2 workspace already contains the simple, nested, corrupt, multipart, and encrypted fixtures needed for both the required first gate and exploratory follow-up work, but the current generated set does not include traversal-attempt or unsupported-format samples.
  Evidence: `docs/spikes/fixtures/generated/spike-2/fixtures.md` on 2026-03-06 lists `simple.zip`, `unicode_file_names.zip`, `simple.7z`, `simple.rar`, multipart 7z, multipart rar, `nested-archive.zip`, `encrypted.rar`, `encrypted.7z`, and `corrupt.7z`.

- Observation: the repo root owns the only checked-in Gradle wrapper, so the disposable Spike 2 project must be addressable through `./gradlew -p docs/spikes/apps/spike-2-android` rather than through a local wrapper.
  Evidence: `find . -maxdepth 3 \( -name gradlew -o -name settings.gradle.kts -o -name build.gradle.kts \) | sort` on 2026-03-06 showed root `./gradlew` and no wrapper under the Spike 2 app folder.

- Observation: the Spike 2 spec requires on-device execution, while the canonical fixture and artifact locations live in the host repo tree, so the harness cannot read or write those paths directly at runtime.
  Evidence: `docs/spikes/specs/spike-2-unarchive-runtime.md` requires an on-device Android run, and `docs/spikes/apps/spike-2-android/README.md` names host-side fixture and artifact paths as the source of truth.

- Observation: the checked-in `corrupt.7z` sample was not a deterministic failure on the verified Samsung Android 16 runtime; it extracted successfully and therefore could not serve as the first-gate failure proof.
  Evidence: the intermediate device run artifact `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/run-d-corruption-failure/passes/pass-01/items.json` recorded successful extraction with `operationResult = OK`, and the run only became stable after mutating a run-local copied `.7z` into garbage bytes before open.

- Observation: the custom Gradle device task needed explicit failure parsing because the raw `adb shell am instrument` path could still leave the enclosing task green after JUnit reported a failed test.
  Evidence: the first failed device execution printed `INSTRUMENTATION_CODE: -1` but still ended with `BUILD SUCCESSFUL` until `spike2RunInstrumentation` started checking the runner output for failure markers and missing `OK (...)`.

## Decision Log

- Decision: Keep this plan at `docs/spikes/apps/spike-2-android/PLAN.md`.
  Rationale: The user explicitly requested a self-contained draft plan in the Spike 2 app folder. Future agents should find the harness README and the harness plan together.
  Date/Author: 2026-03-06 / Codex

- Decision: Build Spike 2 as a standalone disposable Android project rooted under `docs/spikes/apps/spike-2-android/`, with one `app` module and no dependency on the production `:app` module.
  Rationale: `docs/spikes/apps/spike-2-android/README.md` already frames Spike 2 as an isolated harness. Keeping it separate limits risk and makes later deletion straightforward.
  Date/Author: 2026-03-06 / Codex

- Decision: Use `7-Zip-JBinding-4Android` only for archive runtime behavior.
  Rationale: This stack decision is locked by the user, matches the Spike 2 spec, and keeps the spike focused on the Android-targeted archive library already researched in `docs/spikes/research/7-zip-jbinding-4android.md`.
  Date/Author: 2026-03-06 / Codex

- Decision: The first connected-device gate is limited to `zip`, `rar`, and `7z` extraction, flattening, recursion, rename ordering, cleanup, and one corruption-style failure.
  Rationale: The user explicitly narrowed the must-prove scope for the first device run. Multipart, encrypted/password, traversal-attempt, and unsupported cases remain useful exploratory follow-up work, but they should not block the first go or no-go read on the Android unarchive runtime.
  Date/Author: 2026-03-06 / Codex

- Decision: Do not introduce a second archive library and do not use platform unzip helpers as fallback logic.
  Rationale: The spike must answer whether the locked library is viable on its own, plus whether caller-owned policy can be implemented around it. Adding another archive implementation would invalidate that result.
  Date/Author: 2026-03-06 / Codex

- Decision: Run the full matrix on-device through the custom instrumentation entrypoint (`spike2RunInstrumentation` / `spike2ConnectedMatrix`) instead of through a desktop JVM or a rich Android UI flow.
  Rationale: On-device runtime behavior is a locked requirement, and instrumentation gives the smallest Android surface that can still read staged fixtures, call the JNI-backed library, and emit precise evidence.
  Date/Author: 2026-03-06 / Codex

- Decision: Stage host fixtures into generated `androidTest` assets before installation, then copy them into a per-run device workspace at test start.
  Rationale: The device cannot read repo paths directly. Using generated test assets keeps the harness self-contained while preserving the host fixture tree as the canonical source.
  Date/Author: 2026-03-06 / Codex

- Decision: Make the run matrix fully data-driven with explicit fixture-family and expected-outcome fields instead of branching on `runId`.
  Rationale: Multipart, failure, and rename coverage should be readable from the run definitions themselves. Hiding those expectations in runner conditionals would make the artifact contract harder to trust and easier to drift.
  Date/Author: 2026-03-06 / Codex

- Decision: keep shared spike workspace files such as `docs/spikes/justfile` under coordinator ownership rather than Spike-2-agent ownership.
  Rationale: Spike 1, Spike 2, and Spike 3 all need shared workspace command wiring. Centralizing those edits avoids agent collisions while still letting this plan define the command contract Spike 2 needs.
  Date/Author: 2026-03-06 / Codex

- Decision: Write run artifacts first into app-specific external storage on the device, then pull them back to `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/` as a finalized host snapshot.
  Rationale: App-specific external storage can be written without extra permissions and is straightforward to copy back with adb. This keeps the host artifact tree deterministic and easy to inspect after each run.
  Date/Author: 2026-03-06 / Codex

- Decision: Keep the Spike 2 run matrix as Kotlin definitions in source code instead of parsing `fixtures.md` at runtime.
  Rationale: The current fixture set is small and controlled. Hard-coded relative fixture paths are easier to validate, less fragile than Markdown parsing, and keep the artifact-writing code focused on evidence instead of document scraping.
  Date/Author: 2026-03-06 / Codex

## Outcomes & Retrospective

As of 2026-03-06, the narrowed Spike 2 first gate is complete. The Android harness builds, runs on-device, pulls host-visible artifacts, and now has a post-run handoff doc at `docs/spikes/outputs/spike-2-unarchive.md` based on a successful connected-device execution.

The key connected-device outcomes are now explicit in the artifact bundle under `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/`. Runtime initialization succeeded on a Samsung `SM-S916U` running Android `16`; Run A proved single-level `.zip`, `.rar`, `.7z`, and unicode ZIP extraction; Run B and Run C proved recursion-off versus recursion-on behavior and rename ordering; and Run D captured an expected `SevenZipException` against a run-local intentionally broken `.7z` copy.

This plan now has three durable outcomes:

1. An isolated Android harness under `docs/spikes/apps/spike-2-android/` that a novice can build and inspect without prior chat context.
2. A narrowed first-device acceptance contract focused on `zip`, `rar`, `7z`, flattening, recursion, rename ordering, cleanup, and corruption-style failure reporting.
3. An evidence-backed post-run output at `docs/spikes/outputs/spike-2-unarchive.md` that records the verified device run and the resulting architecture implications.

Exploratory multipart, encrypted/password, traversal-attempt, and unsupported-format work remains available inside the harness, but it is intentionally outside this plan's done gate.

## Context and Orientation

Spike 2 is the unarchive-runtime spike. The behavior contract lives in `docs/spikes/specs/spike-2-unarchive-runtime.md`. That spec defines the goal, run matrix, evidence questions, required artifacts, and pass/fail gate. This plan translates the spec into a concrete disposable Android project shape, file map, task wiring, and execution procedure.

The harness guidance already in place lives in `docs/spikes/apps/spike-2-android/README.md`. It establishes four non-negotiable boundaries: keep the harness under this folder, keep it isolated from the production app, use `7-Zip-JBinding-4Android` only, and invoke the project through the repo wrapper with `./gradlew -p docs/spikes/apps/spike-2-android`.

Fixture preparation and inventory live under `docs/spikes/fixtures/README.md`, `docs/spikes/justfile`, `docs/spikes/fixtures/generated/spike-2/input/`, and `docs/spikes/fixtures/generated/spike-2/fixtures.md`. `just fixtures` is the authoritative way to refresh the generated workspace before a Spike 2 run. The generated input tree is the canonical input set. The future harness must treat it as read-only source material.

Behavior constraints that the harness must prove are spread across two behavior docs. `docs/behavior/downloads.md` defines the flattening, recursive unarchive, deletion-after-success, and destination-subfolder behavior. `docs/behavior/naming.md` defines the rename rule boundary: rename applies only to final non-archive outputs, never to the archive filenames themselves, and collisions suffix with ` (n)`.

The architecture reason for doing this spike first is captured in `docs/architecture/SPIKE.md`: the spike outcome feeds later architecture work and should produce evidence, not migration implementation code.

The library behavior that motivates the project shape is documented in `docs/spikes/research/7-zip-jbinding-4android.md` and in the checked-in reference module at `references/7-Zip-JBinding-4Android/`. That research already shows that runtime initialization, extraction callbacks, multipart open callbacks, and archive item inspection are available, while flattening, recursion, collision handling, path safety, and cleanup remain caller-owned.

For this plan, use the following terms consistently:

- Harness: the disposable Android project rooted at `docs/spikes/apps/spike-2-android/`.
- Matrix run: one named execution from the Spike 2 spec, such as single-level extraction or multipart extraction.
- Session: one full invocation of the matrix command.
- Run artifact set: the files written for one matrix run, including runtime, manifests, cleanup, and failure evidence.
- Final output tree: the extracted non-archive files that remain after the run finishes.
- Destination subfolder: the logical output root for a run. For the spike, set it to the run id so outputs stay isolated.

The future implementation should touch these exact repo paths and avoid broadening beyond them:

- `docs/spikes/apps/spike-2-android/settings.gradle.kts`
- `docs/spikes/apps/spike-2-android/build.gradle.kts`
- `docs/spikes/apps/spike-2-android/gradle.properties`
- `docs/spikes/apps/spike-2-android/app/build.gradle.kts`
- `docs/spikes/apps/spike-2-android/app/proguard-rules.pro`
- `docs/spikes/apps/spike-2-android/app/src/main/AndroidManifest.xml`
- `docs/spikes/apps/spike-2-android/app/src/main/java/com/romulus/spikes/spike2/Spike2HostActivity.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2MatrixInstrumentedTest.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2MatrixRunner.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2Types.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2RunDefinitions.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2FixtureStager.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipArchiveExtractor.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipMultipartCallbacks.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2OutputPolicy.kt`
- `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2ArtifactWriter.kt`
- `docs/spikes/fixtures/generated/spike-2/run-artifacts/`

Do not touch the production Android module at `app/` while implementing this spike. Do not increment the schema version. Do not move Spike 2 code outside `docs/spikes/apps/spike-2-android/`.

## Plan of Work

The standalone Android build inside `docs/spikes/apps/spike-2-android/` is already scaffolded with local `settings.gradle.kts`, `build.gradle.kts`, and `gradle.properties` that mirror the root Android toolchain versions already in use by this repo: Android Gradle Plugin `8.13.2`, Kotlin `2.2.21`, Java 17, `compileSdk = 36`, `minSdk = 33`, and `targetSdk = 36`. The harness remains a single `app` module because `connectedDebugAndroidTest` expects an installable target app.

Inside `docs/spikes/apps/spike-2-android/app/build.gradle.kts`, define three categories of behavior. First, declare the Android app configuration and the minimal runtime dependencies: `androidx.core:core-ktx`, `androidx.appcompat:appcompat`, `org.jetbrains.kotlinx:kotlinx-serialization-json`, AndroidX instrumentation dependencies, and the locked archive dependency `com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03`. The nested build must declare `google()`, `mavenCentral()`, and `jitpack.io`; do not assume root repositories bleed through. Second, register build tasks that stage fixtures from `docs/spikes/fixtures/generated/spike-2/input/` into a generated `androidTest` assets directory under `build/generated/spike-2/androidTestAssets/`, then register that directory explicitly in the Android `androidTest.assets` source set. Third, register host-side tasks that clear stale host artifacts, run the instrumentation suite, and pull the device artifact directory back into `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/`.

The `app` module should contain exactly one tiny runtime entrypoint in `src/main`: `Spike2HostActivity.kt`. It only needs to exist so the application installs and can host instrumentation. Do not build UI beyond the minimum manifest-valid activity. All spike behavior belongs in `src/androidTest` because the real operator surface is the connected test run.

Implement the run orchestration in `src/androidTest` with a small set of focused Kotlin files. `Spike2MatrixInstrumentedTest.kt` is the single JUnit entrypoint with one `runFullMatrix()` test that fails if any required run cannot produce evidence. `Spike2Types.kt` defines the shared execution and artifact contracts so the runner, extractor, callbacks, stager, and writer all exchange the same concrete types rather than ad hoc maps or per-file models. `Spike2RunDefinitions.kt` holds the current required matrix definitions: Run A for single-level archive extraction, Run B for nested archive with recursion off, Run C for nested archive with recursion on and rename on, and Run D for deterministic corruption failure observation. Run D should derive its failing input from a valid fixture through a run-local mutation so the failure remains deterministic even if the checked-in corrupt sample happens to extract on the target runtime. Multipart callbacks remain implemented in the harness as exploratory follow-up capability, but they are not part of the first connected-device gate.

`Spike2FixtureStager.kt` should copy staged asset files from the test APK into a per-session device workspace under the app sandbox before the library touches them. Keep the device workspace structure parallel to the host fixture tree so artifact manifests can reference both host-relative and device-relative paths cleanly. Fail fast if required fixtures are missing.

`SevenZipArchiveExtractor.kt` should own all library interaction. It must record runtime initialization through `SevenZip.getSevenZipVersion()`, `SevenZip.getSevenZipJBindingVersion()`, and `SevenZip.isInitializedSuccessfully()`. It should open archives with `SevenZip.openInArchive(...)`, inspect item properties through `IInArchive.getProperty(...)` and `getArchiveFormat()`, and extract items through `IInArchive.extract(...)` with an `IArchiveExtractCallback` implementation that records per-item ask mode, output path, bytes written, and `ExtractOperationResult`.

`SevenZipMultipartCallbacks.kt` implements the two multipart pathways documented by the reference module. For `.7z` multipart runs, it uses `VolumedArchiveInStream` plus an `IArchiveOpenVolumeCallback` that records every requested filename and whether it resolved. For `.rar` multipart runs, it implements `IArchiveOpenVolumeCallback` and `IArchiveOpenCallback` together, records `PropID.NAME`, and captures each requested volume name plus resolved or missing state. This callback layer remains available for exploratory follow-up work, but it is not part of the first required device gate.

`Spike2OutputPolicy.kt` should implement the caller-owned policies that the library does not provide. This file should normalize output paths, reject path traversal outside the destination root, flatten extracted directories so there is no archive-named parent folder in the final output tree, apply rename rules only to final non-archive leaf outputs, preserve archive filenames until they are either recursively opened or deleted after success, and suffix output collisions with ` (n)` in the format described by `docs/behavior/naming.md`.

`Spike2MatrixRunner.kt` should orchestrate multi-pass behavior. For each run definition, it should create a fresh destination root, initialize the runtime envelope, execute the first extraction pass, detect recursively supported archive outputs when recursion is enabled, execute additional bounded passes until no supported archives remain, and record per-pass input and output manifests. Run B and Run C should use the same nested fixture so the final artifact trees make the recursion difference obvious.

`Spike2ArtifactWriter.kt` should write the on-device evidence tree. Write artifacts into app-specific external storage first, under a deterministic device root such as `/sdcard/Android/data/<applicationId>/files/spike-2/run-artifacts/latest/`, so adb can pull it back to the repo after the test run. The host-side pull task should replace `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/` atomically by pulling into a temporary directory and renaming it into place only after success.

The host artifact tree must look like this after a successful pull:

    docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/
      session.json
      run-a-single-level/
        run.json
        runtime-init.json
        passes/pass-01/input-manifest.json
        passes/pass-01/items.json
        passes/pass-01/output-manifest.json
        cleanup.json
        final-output-tree/
      run-b-nested-no-recursion/
        run.json
        runtime-init.json
        passes/pass-01/input-manifest.json
        passes/pass-01/items.json
        passes/pass-01/output-manifest.json
        cleanup.json
        final-output-tree/
      run-c-nested-recursive-rename/
        run.json
        runtime-init.json
        passes/pass-01/input-manifest.json
        passes/pass-01/items.json
        passes/pass-01/output-manifest.json
        passes/pass-02/input-manifest.json
        passes/pass-02/items.json
        passes/pass-02/output-manifest.json
        cleanup.json
        final-output-tree/
      run-d-corruption-failure/
        run.json
        runtime-init.json
        passes/pass-01/input-manifest.json
        failure.json
        cleanup.json

`session.json` should summarize the whole command: started and finished timestamps, device model, Android release, ABI, app id, library versions, selected device serial when available, and one status line per matrix run. `run.json` should restate the run definition so a later reviewer does not need source code to understand the evidence. `runtime-init.json` should capture the initialization envelope for that run. `items.json` should record archive item metadata and per-item extraction results. `output-manifest.json` should record every output path written in that pass, along with whether the file is still an archive candidate for the next pass. `cleanup.json` should state which archive files were deleted after successful extraction, which were retained, and why. `failure.json` should capture the thrown exception, message, callback result state, and fixture identity for any failing run. These files should serialize the shared types from `Spike2Types.kt` directly or through trivially equivalent DTOs; do not let each writer invent its own shape independently.

Finally, wire the operator surface. Coordinate with the shared spike workspace so `docs/spikes/justfile` exposes a preferred recipe named `spike-2-android`. That recipe should ensure the generated fixture workspace exists, then invoke the repo wrapper against the local project. The low-level command surface should remain available for debugging, but the intended operator path should be one `just` command from `docs/spikes`. Because this repo requires escalation for both Gradle and adb, every documented run command must explicitly note that the operator needs to approve escalation before execution. Device selection must be deterministic: if `ANDROID_SERIAL` is set, use that device; otherwise require exactly one connected device or emulator and fail fast with a clear message.

## Concrete Steps

All commands below assume the current working tree is the repo root `/Users/nskaria/projects/romulus` unless a different directory is stated. Every Gradle or adb command in this repo requires user escalation.

1. Refresh the fixture workspace.

   Working directory: `/Users/nskaria/projects/romulus/docs/spikes`

   Command:

       just fixtures

   Expected evidence:

       generated/spike-2/input/zip/simple.zip
       generated/spike-2/input/7z/simple.7z
       generated/spike-2/input/rar/simple.rar
       generated/spike-2/fixtures.md

2. Scaffold the standalone Android project in `docs/spikes/apps/spike-2-android/`.

   Working directory: `/Users/nskaria/projects/romulus`

   Create these files before adding Kotlin source:

       docs/spikes/apps/spike-2-android/settings.gradle.kts
       docs/spikes/apps/spike-2-android/build.gradle.kts
       docs/spikes/apps/spike-2-android/gradle.properties
       docs/spikes/apps/spike-2-android/app/build.gradle.kts
       docs/spikes/apps/spike-2-android/app/proguard-rules.pro
       docs/spikes/apps/spike-2-android/app/src/main/AndroidManifest.xml

3. Add the minimal app and the instrumentation runner implementation.

   Working directory: `/Users/nskaria/projects/romulus`

   Create these source files:

       docs/spikes/apps/spike-2-android/app/src/main/java/com/romulus/spikes/spike2/Spike2HostActivity.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2MatrixInstrumentedTest.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2MatrixRunner.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2RunDefinitions.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2FixtureStager.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipArchiveExtractor.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipMultipartCallbacks.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2OutputPolicy.kt
       docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2ArtifactWriter.kt

4. Add task wiring in the local build and coordinate the preferred operator recipe separately.

   Working directory: `/Users/nskaria/projects/romulus`

   Update:

       docs/spikes/apps/spike-2-android/app/build.gradle.kts

   The intended low-level Gradle command is:

       ./gradlew -p docs/spikes/apps/spike-2-android :app:spike2ConnectedMatrix

   The intended preferred operator command is:

       cd docs/spikes
       just spike-2-android

   Expected transcript after implementation:

       > Task :app:syncSpike2AndroidTestAssets
       > Task :app:spike2RunInstrumentation
       > Task :app:pullSpike2Artifacts
       BUILD SUCCESSFUL

5. Inspect the pulled artifact tree.

   Working directory: `/Users/nskaria/projects/romulus`

   Commands:

       find docs/spikes/fixtures/generated/spike-2/run-artifacts/latest -maxdepth 3 -print | sort
       sed -n '1,120p' docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/session.json

   Expected evidence:

       docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/run-a-single-level
       docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/run-b-nested-no-recursion
       docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/run-c-nested-recursive-rename
       docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/run-d-corruption-failure

6. Use the artifacts to write the post-run result doc only after the matrix has completed successfully enough to answer the spec.

   Working directory: `/Users/nskaria/projects/romulus`

   Output target:

       docs/spikes/outputs/spike-2-unarchive.md

## Validation and Acceptance

Validation is complete only when the operator can reproduce the run from the working tree and the evidence answers the Spike 2 spec without needing chat context.

Run these checks in order:

1. Fixture readiness.
   Run `cd /Users/nskaria/projects/romulus/docs/spikes && just fixtures` and confirm that `docs/spikes/fixtures/generated/spike-2/input/` contains the exact fixture families listed in `docs/spikes/fixtures/generated/spike-2/fixtures.md`.

2. Harness build wiring.
   Run `./gradlew -p docs/spikes/apps/spike-2-android :app:tasks --all` from `/Users/nskaria/projects/romulus` and confirm the Spike 2 project exposes the expected tasks, including `syncSpike2AndroidTestAssets`, `spike2RunInstrumentation`, `spike2ConnectedMatrix`, and `pullSpike2Artifacts`. This command requires user escalation.

3. Full matrix execution.
   Run `./gradlew -p docs/spikes/apps/spike-2-android :app:spike2ConnectedMatrix` from `/Users/nskaria/projects/romulus` and confirm `BUILD SUCCESSFUL`. This command requires user escalation.

4. Session summary evidence.
   Inspect `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/session.json` and confirm it contains: device model, Android release, ABI, archive library versions, started and finished timestamps, and status entries for `run-a-single-level`, `run-b-nested-no-recursion`, `run-c-nested-recursive-rename`, and `run-d-corruption-failure`.

5. Run A acceptance.
   Confirm `run-a-single-level/final-output-tree/` contains extracted files from zip, rar, and 7z fixtures, including the unicode-name zip, and that `run-a-single-level/passes/pass-01/output-manifest.json` shows no archive-named wrapper directory in the final paths.

6. Run B acceptance.
   Confirm `run-b-nested-no-recursion/final-output-tree/` preserves the nested archive as a file when recursion is disabled, and that `run-b-nested-no-recursion/cleanup.json` explains which outer archive was deleted or retained.

7. Run C acceptance.
   Confirm `run-c-nested-recursive-rename/final-output-tree/` differs from Run B by extracting supported nested archives, that at least one final non-archive output has the rename rule applied, and that no archive filename in the manifests was renamed before extraction.

8. Run D acceptance.
   Confirm `run-d-corruption-failure/failure.json` records the intentionally broken archive failure with a concrete exception or non-OK extraction result, and that the cleanup evidence shows whether the failing archive was retained or removed.

9. Cleanup acceptance.
    Confirm every successful run records whether extracted archive files were deleted after success, and every failed run records retention or cleanup outcome explicitly. The harness must prove behavior, not silently assume it.

10. Post-run doc readiness.
    Confirm the pulled artifact tree is detailed enough to write `docs/spikes/outputs/spike-2-unarchive.md` with the sections required by `docs/spikes/specs/spike-2-unarchive-runtime.md`: evidence summary, capability matrix, caller-owned policy constraints, cleanup and failure constraints, architecture implications, and follow-up tests.

## Idempotence and Recovery

The future implementation should be safe to rerun. `just fixtures` is already repeatable and should remain the first recovery step whenever the input workspace looks stale.

The harness run should also be repeatable. `syncSpike2AndroidTestAssets` must replace the generated test-assets directory completely on every invocation so stale files cannot survive from older runs. The instrumentation runner must delete and recreate its device working root before beginning a new session. The host pull step must replace only `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/`; it must never modify `docs/spikes/fixtures/generated/spike-2/input/`.

If the instrumentation test succeeds on-device but pulling artifacts back to the host fails, the operator should be able to rerun only the pull step:

    ./gradlew -p docs/spikes/apps/spike-2-android :app:pullSpike2Artifacts

This command requires user escalation. The task should fail fast if the expected device artifact root no longer exists.

If a device disconnect or adb failure interrupts the run before artifacts are copied back, reconnect the device and rerun the full command. If preserving the previous `latest/` evidence matters, manually rename that directory before rerunning. The default path should optimize for deterministic reruns, not archival history.

If dependency resolution for `7-Zip-JBinding-4Android` fails, verify that the local Spike 2 build still includes `jitpack.io` and the documented coordinate `com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03`, then rerun the Gradle command. Do not solve that failure by introducing a different archive library.

If a traversal-attempt fixture is added later under `docs/spikes/fixtures/generated/spike-2/input/failure/`, the existing path-safety logic should reject it without any structural changes to the harness or artifact tree. This is the reason to keep failure runs generic and data-driven even though the current fixture set is smaller.

## Artifacts and Notes

The first implementation should make the artifact tree readable without custom tooling. Prefer JSON for machine-readable records and plain copied files for final extracted outputs.

A good `session.json` shape is:

    {
      "sessionId": "latest",
      "device": {
        "serial": "emulator-5554",
        "model": "sdk_gphone64_arm64",
        "androidRelease": "16",
        "abi": "arm64-v8a"
      },
      "library": {
        "sevenZipVersion": "16.02",
        "jbindingVersion": "16.02-2.03",
        "initialized": true
      },
      "runs": [
        { "runId": "run-a-single-level", "status": "passed" },
        { "runId": "run-b-nested-no-recursion", "status": "passed" },
        { "runId": "run-c-nested-recursive-rename", "status": "passed" },
        { "runId": "run-d-corruption-failure", "status": "observed-failure" }
      ]
    }

A good `run.json` shape is:

    {
      "runId": "run-c-nested-recursive-rename",
      "fixtureInputs": [
        "input/nested/nested-archive.zip"
      ],
      "destinationSubfolder": "run-c-nested-recursive-rename",
      "unarchive": true,
      "recursiveUnarchive": true,
      "renameRule": {
        "pattern": "^(.+)$",
        "replacement": "renamed-$1"
      }
    }

Exploratory follow-up multipart runs may emit a `volume-resolution.json` record like:

    [
      { "requested": "multipart-7z.7z.001", "resolved": true },
      { "requested": "multipart-7z.7z.002", "resolved": true },
      { "requested": "multipart-rar.part4.rar", "resolved": true }
    ]

A good `cleanup.json` record is:

    {
      "deletedAfterSuccess": [
        "device-work/run-c-nested-recursive-rename/pass-01/nested-archive.zip"
      ],
      "retained": [],
      "notes": "Run C deleted intermediate supported archives only after their successful recursive extraction."
    }

Keep the artifact examples concise in the final implementation, but do not hide the details needed to debug runtime failures.

## Interfaces and Dependencies

Keep the harness package at `com.romulus.spikes.spike2`. Use Kotlin throughout the local project.

Mirror the root Android toolchain versions already present in `/Users/nskaria/projects/romulus/build.gradle.kts` and `/Users/nskaria/projects/romulus/app/build.gradle.kts`:

- Android Gradle Plugin `8.13.2`
- Kotlin Android plugin `2.2.21`
- Java 17
- `compileSdk = 36`
- `minSdk = 33`
- `targetSdk = 36`

Use these library categories and no extra archive library:

- `com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03`
- AndroidX core/appcompat only for the minimal host app
- AndroidX instrumentation test libraries for `connectedDebugAndroidTest`
- `org.jetbrains.kotlinx:kotlinx-serialization-json` for artifact files

Do not add Zip4j, Apache Commons Compress, or any second archive runtime to this harness.

Create these classes and functions with these responsibilities:

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2MatrixInstrumentedTest.kt`, define:

    @RunWith(AndroidJUnit4::class)
    class Spike2MatrixInstrumentedTest {
        @Test
        fun runFullMatrix()
    }

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2RunDefinitions.kt`, define:

    data class Spike2RenameRule(
        val pattern: String,
        val replacement: String,
    )

    enum class Spike2ArchiveFamily {
        ZIP,
        RAR,
        SEVEN_Z,
        UNKNOWN,
    }

    enum class Spike2ExpectedOutcome {
        SUCCESS,
        OBSERVED_FAILURE,
        SUCCESS_OR_UNSUPPORTED_WITH_REASON,
    }

    data class Spike2FixtureInput(
        val relativePath: String,
        val archiveFamily: Spike2ArchiveFamily,
        val multipartGroup: String?,
    )
    
    data class Spike2RunDefinition(
        val runId: String,
        val fixtureInputs: List<Spike2FixtureInput>,
        val destinationSubfolder: String,
        val unarchive: Boolean,
        val recursiveUnarchive: Boolean,
        val renameRule: Spike2RenameRule?,
        val expectedOutcome: Spike2ExpectedOutcome,
        val expectedPassCount: Int,
        val expectedFailureKinds: List<String>,
    )
    
    object Spike2RunDefinitions {
        fun defaultMatrix(): List<Spike2RunDefinition>
    }

`defaultMatrix()` must return exactly these four required runs and no others until the spec changes:

- `run-a-single-level`
- `run-b-nested-no-recursion`
- `run-c-nested-recursive-rename`
- `run-d-corruption-failure`

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2Types.kt`, define the shared data contracts used by the runner, stager, extractor, callbacks, and artifact writer. At minimum define shapes equivalent to:

    data class Spike2RuntimeEnvelope(...)
    data class Spike2FixtureRef(...)
    data class Spike2VolumeResolutionEvent(...)
    data class Spike2ExtractRequest(...)
    data class Spike2ExtractItemResult(...)
    data class Spike2PassResult(...)
    data class Spike2FailureRecord(...)
    data class Spike2RunResult(...)
    data class Spike2SessionResult(...)

These types must own the artifact schema. `session.json`, `run.json`, `runtime-init.json`, `items.json`, `output-manifest.json`, `volume-resolution.json`, `cleanup.json`, and `failure.json` should map directly back to these contracts so the implementation does not fork the shape in multiple places.

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2MatrixRunner.kt`, define:

    class Spike2MatrixRunner(
        private val appContext: Context,
    ) {
        fun runMatrix(definitions: List<Spike2RunDefinition>): Spike2SessionResult
    }

`Spike2SessionResult` should carry session metadata plus one `Spike2RunResult` per run. Fail the test if required evidence files cannot be written.

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2FixtureStager.kt`, define:

    class Spike2FixtureStager(
        private val assetManager: AssetManager,
        private val appContext: Context,
    ) {
        fun stageSessionFixtures(definitions: List<Spike2RunDefinition>): Spike2StagedFixtureSet
    }

This class must copy fixtures out of test assets into the device workspace and return host-relative plus device-relative references for artifact recording.

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipArchiveExtractor.kt`, define:

    class SevenZipArchiveExtractor(
        private val outputPolicy: Spike2OutputPolicy,
    ) {
        fun captureRuntimeEnvelope(): Spike2RuntimeEnvelope
        fun extract(request: Spike2ExtractRequest): Spike2ExtractResult
    }

This class must call the locked library directly and must not delegate archive work to any other implementation.

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipMultipartCallbacks.kt`, keep callback helpers for both multipart families available for exploratory follow-up work:

    class Spike2SevenZipVolumeCallback : IArchiveOpenVolumeCallback
    class Spike2RarVolumeCallback : IArchiveOpenVolumeCallback, IArchiveOpenCallback

Both callbacks must expose recorded volume-resolution events back to the matrix runner for `volume-resolution.json`.

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2OutputPolicy.kt`, define:

    class Spike2OutputPolicy {
        fun resolveFinalOutputPath(
            destinationRoot: File,
            archiveItemPath: String,
            renameRule: Spike2RenameRule?,
            existingOutputs: Set<String>,
        ): File
    
        fun shouldRecursivelyUnarchive(path: String): Boolean
    }

`resolveFinalOutputPath(...)` must enforce all caller-owned policy: flattening, traversal rejection, rename ordering, and collision suffixing.

In `docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/Spike2ArtifactWriter.kt`, define:

    class Spike2ArtifactWriter(
        private val appContext: Context,
    ) {
        fun resetSessionRoot(): File
        fun writeSession(result: Spike2SessionResult)
        fun writeRun(result: Spike2RunResult)
    }

The writer must target device external files storage first, under `spike-2/run-artifacts/latest/`, so the Gradle pull task knows exactly what to copy back.

In `docs/spikes/apps/spike-2-android/app/build.gradle.kts`, register these tasks or equivalent names that make the operator surface obvious:

- `syncSpike2AndroidTestAssets`
- `clearSpike2HostArtifacts`
- `spike2RunInstrumentation`
- `spike2ConnectedMatrix`
- `pullSpike2Artifacts`

Make `spike2RunInstrumentation` depend on `syncSpike2AndroidTestAssets`, `installDebug`, and `installDebugAndroidTest`, and have it finalize with `pullSpike2Artifacts` so host evidence is still collected when the instrumentation session reports a failure. `spike2ConnectedMatrix` should clear stale host artifacts first, then delegate to that instrumentation task.
The same build file must also register the generated assets directory in `android.sourceSets["androidTest"].assets.srcDir(...)`, declare `google()`, `mavenCentral()`, and `jitpack.io`, and make `pullSpike2Artifacts` use `ANDROID_SERIAL` when set or fail fast if more than one device is connected and no serial is provided.

Coordinate with the shared spike workspace so `docs/spikes/justfile` exposes a preferred recipe with this exact name:

- `spike-2-android`

That recipe is shared workspace infrastructure and should be coordinated centrally. Its contract is to call the repo wrapper against `docs/spikes/apps/spike-2-android` and note in its help text or echo output that Gradle and adb will need user escalation in this repo.

## Plan Revision Notes

- (2026-03-06) Created the first draft execution plan from the Spike 2 spec, harness README, fixture workflow, behavior docs, architecture spike guidance, and 7-Zip Android research. The user explicitly directed this plan to live in `docs/spikes/apps/spike-2-android/PLAN.md`.
- (2026-03-06) Added explicit shared data contracts, richer run-definition fields, and concrete standalone Android build and adb-pull rules so future implementers do not have to infer matrix behavior or device wiring from `runId` branches.
- (2026-03-06) Moved shared spike workspace ownership such as `docs/spikes/justfile` out of the Spike 2 agent boundary so Spike 1-3 can be implemented in parallel without command-surface merge conflicts.
