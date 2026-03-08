# Spike 4 Android Harness

## 1. Purpose

1. This folder is the recommended workspace for the focused Android app used by Spike 4.
2. The execution contract lives in `docs/spikes/specs/spike-4-integration.md`; this README only defines harness workspace and high-level build guidance.
3. Spike 4 composes accepted Spike 1, Spike 2, and Spike 3 outcomes and should not re-decide them inside the harness.

## 2. Recommended Workspace

1. Keep the harness workspace rooted at `docs/spikes/apps/spike-4-android/`.
2. Keep it separate from migration implementation code and other spike harnesses.
3. Read user-provided inputs from `docs/spikes/.env.local` through the generated readiness material under `docs/spikes/fixtures/generated/`.

## 3. Build Shape

1. Keep the build shape to a small Android app or isolated Android build that is only large enough to execute the Spike 4 matrix on target runtime.
2. The harness now owns build, install, and run orchestration behind one repo-root operator command.
3. The current implementation is a standalone Android project with one `app` module, a minimal host activity under `src/main`, generated `androidTest` assets for live config staging, and an instrumentation-first matrix under `src/androidTest`.
4. The generated runtime config is built from `docs/spikes/.env.local` into the harness build directory only; it is not persisted under `docs/spikes/fixtures/generated/`.

## 4. Dependency Surface

1. Compose only the accepted Spike 1-3 outcomes needed for the integrated flow:
   - Spike 1 resolver and selected-file download behavior,
   - Spike 3 remote `.zip` enumeration and selected internal-file download behavior,
   - Spike 2 `7-Zip-JBinding-4Android` unarchive behavior.
2. Add only the Android runtime pieces required to run the matrix, write artifacts, and capture diagnostics.
3. Treat recursive unarchive as out of scope for this harness; Spike 4 proves single-pass archive-selection integration only.
4. Do not use Spike 4 to reopen dependency choices already accepted through Spike 1, Spike 2, or Spike 3 outputs.

## 5. Default Command Surface

1. The default operator command is `cd docs/spikes && just spike-4-android`.
2. That command runs fixture preparation, generates the temporary machine-readable runtime config from `docs/spikes/.env.local`, installs the harness and `androidTest` APKs, runs the connected instrumentation matrix, and pulls the latest device artifacts back into the repo workspace.
3. The lower-level Gradle path remains available for debugging: `./gradlew -p docs/spikes/apps/spike-4-android :app:spike4ConnectedMatrix`.
4. This command requires Gradle and adb escalation in this repo, plus a connected device or emulator selected by `ANDROID_SERIAL` or by the single-connected-device rule.

## 6. Artifact Paths

1. Read prepared human-readable Spike 4 readiness input from `docs/spikes/fixtures/generated/spike-4/user-input.md`.
2. Read the live machine-readable runtime config from generated `androidTest` assets under the harness build directory; that config is sourced from `docs/spikes/.env.local` and is not persisted under `docs/spikes/fixtures/generated/`.
3. Write per-run harness artifacts to app-specific external storage first, then pull the latest host copy to `docs/spikes/fixtures/generated/spike-4/run-artifacts/latest/`.
4. Write the post-run reviewed output doc to `docs/spikes/outputs/spike-4-integration.md` after the connected matrix completes and the pulled artifact bundle has been reviewed.

## 7. References

1. [`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md)
2. [`/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md`](/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md)
3. [`/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md)
