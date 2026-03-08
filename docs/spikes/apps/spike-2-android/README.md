# Spike 2 Android Harness

## 1. Purpose

1. This doc owns the harness location, build shape, command surface, and local workspace paths for Spike 2.
2. [`../../specs/spike-2-unarchive-runtime.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-2-unarchive-runtime.md) owns the behavior contract, run matrix, evidence questions, and pass or fail gate.

## 2. Recommended Location

1. Keep the disposable Spike 2 Android harness rooted at `docs/spikes/apps/spike-2-android/`.
2. Keep it isolated from the production `:app` module so Spike 2 can evolve or be deleted without changing the migration implementation workspace.

## 3. High-Level Build Shape

1. Use a standalone Android harness build rooted in this directory.
2. Invoke that build from repo root with the repo wrapper and `-p docs/spikes/apps/spike-2-android` so the harness stays outside the main app build.
3. Keep the harness minimal:
   - one Android harness module,
   - one on-device entrypoint that runs the full Spike 2 matrix,
   - local workspace reads from `docs/spikes/fixtures/generated/spike-2/input/`,
   - local workspace writes to `docs/spikes/fixtures/generated/spike-2/run-artifacts/`.
4. The standalone harness project and its instrumentation runner already exist in this folder, and the narrowed first device gate has been verified on a connected Android device.
5. The current post-run handoff for migration work lives in [`../../outputs/spike-2-unarchive.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/spike-2-unarchive.md).

## 4. Dependency Baseline

1. Use `7-Zip-JBinding-4Android` only.
2. The current required device gate is narrower than the full research surface: prove `zip`, `rar`, and `7z` extraction, flattening, recursion, rename ordering, and one corruption-style failure.
3. Multipart, encrypted/password, traversal-attempt, and unsupported archive-format fixtures remain exploratory follow-up paths, not blockers for the first device run.
4. The same research also shows flattening, recursion, collision handling, path-safety checks, and cleanup are caller-owned policies, so the harness implements those policies around this library instead of introducing a second archive dependency.
5. Do not broaden the Spike 2 library choice beyond `7-Zip-JBinding-4Android`.

## 5. Fixture and Artifact Paths

1. Refresh the generated spike workspace with `cd docs/spikes && just fixtures`.
2. Spike 2 input fixtures come from `docs/spikes/fixtures/generated/spike-2/input/`.
3. The local fixture manifest comes from `docs/spikes/fixtures/generated/spike-2/fixtures.md`.
4. Spike 2 run artifacts and extracted outputs belong under `docs/spikes/fixtures/generated/spike-2/run-artifacts/`.
5. The shared fixture workflow is workspace-owned, not Spike-2-owned. This harness consumes that workspace; it does not define or own `just fixtures`.

## 6. Default Command Surface

1. The default operator command is `cd docs/spikes && just spike-2-android`.
2. The low-level Gradle path remains available for debugging, but it is secondary.
3. The preferred command installs or launches the minimal Android harness on a connected device or emulator, runs the current Spike 2 matrix, and materializes results under `docs/spikes/fixtures/generated/spike-2/run-artifacts/`.
4. Keep any extra knobs narrow and optional; the default path should remain one command for the full on-device matrix.
5. This command requires Gradle and adb escalation in this repo, plus a connected device or emulator selected by `ANDROID_SERIAL` or by the single-connected-device rule.

## 7. Boundary With the Spec

1. The spec answers what Spike 2 must prove.
2. This README answers where the disposable harness lives, what it depends on, how it is launched, and where it reads and writes local workspace data.
3. Keep behavior and evidence requirements in the spec, and keep harness/build wiring in this README.

## 8. Primary References

1. [`../../specs/spike-2-unarchive-runtime.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-2-unarchive-runtime.md)
2. [`../../research/7-zip-jbinding-4android.md`](/Users/nskaria/projects/romulus/docs/spikes/research/7-zip-jbinding-4android.md)
3. [`../../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md)
