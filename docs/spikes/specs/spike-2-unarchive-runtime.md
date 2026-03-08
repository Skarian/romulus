# Spike 2 Spec - Unarchive Runtime

## 1. Goal

1. Prove the target Android runtime can execute local unarchive behavior through a minimal Android harness that uses `7-Zip-JBinding-4Android`.
2. Prove supported archive inputs (`.zip`, `.rar`, `.7z`) can be extracted into the final flattened outputs we want, with optional recursive passes.
3. Prove the harness can surface at least one deterministic corruption-style failure with concrete runtime evidence.
4. Keep Spike 2 focused on local on-disk archive handling only; resolver and download flow are out of scope.

## 2. Inputs

1. Fixture source is the generated Spike 2 input workspace prepared through [`../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md).
2. Fixture manifest is the generated Spike 2 fixture inventory from that workspace.
3. Fixture coverage must include:
   - single archives by type (`.zip`, `.rar`, `.7z`),
   - nested archives,
   - one deterministic corruption-style failure case, which may be a run-local intentionally broken copy derived from a valid fixture.
4. Exploratory follow-up fixture families may include multipart, encrypted, unsupported archive-format, or traversal-attempt archives, but they are not required for the first connected-device gate.
5. Per-run options:
   - unarchive enabled or disabled,
   - recursive unarchive enabled or disabled,
   - destination subfolder,
   - optional rename rule for final non-archive outputs.
6. Expected final output manifest for each run.

## 3. Execution Shape

1. Spike 2 runs through a minimal Android harness that uses `7-Zip-JBinding-4Android` only.
2. Spike 2 is an on-device run; desktop CLI execution is out of scope.
3. The harness consumes the generated fixture workspace and executes the full Spike 2 matrix.
4. Harness location, intended command surface, and artifact paths live in [`../apps/spike-2-android/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-2-android/README.md).

## 4. Runtime Boundary From Research

1. `7-Zip-JBinding-4Android` is the only library in scope for this spike.
2. Archive coverage to prove in the first connected-device gate is `.zip`, `.rar`, and `.7z` using the single-archive fixtures in the generated workspace.
3. The runtime must surface enough evidence to identify archive items and extraction outcomes for the required runs.
4. Caller-owned policy remains outside the library for:
   - flattening,
   - recursion,
   - collision handling,
   - path-safety checks,
   - archive cleanup after success or failure.
5. Corruption-style handling is a required failure-observation case in this spike, and a harness-generated broken copy is acceptable if shared checked-in samples are not deterministic on the target runtime.
6. Password-protected, unsupported archive-format, traversal-attempt, and multipart cases are exploratory follow-up cases, not part of the first gate.
7. Runtime initialization and runtime envelope must be captured for every execution.

## 5. Evidence Questions

1. Can target Android runtime initialize `7-Zip-JBinding-4Android` reliably in the harness?
2. Which of `.zip`, `.rar`, and `.7z` extract correctly for the generated fixture set?
3. Can caller-owned flattening produce final outputs with no archive-named folder?
4. What happens when recursion is off versus on for nested archives?
5. Does rename apply only to final non-archive outputs and never to archive filenames?
6. What deterministic failure surface appears for the intentionally broken archive input?
7. What archive retention or deletion behavior is observed after successful versus failed extraction?

## 6. Run Matrix

1. Run A: single-level extraction for `.zip`, `.rar`, and `.7z`.
2. Run B: nested archive fixture with recursion disabled.
3. Run C: same nested archive fixture with recursion enabled and rename enabled.
4. Run D: deterministic corruption failure observation using a run-local intentionally broken `.7z` copy.

## 7. Required Artifacts

1. Each run in the matrix writes one run-scoped artifact set.
2. Every run-scoped artifact set must include:
   - runtime initialization record,
   - per-pass input and output manifest,
   - per-item extraction evidence,
   - cleanup outcome,
   - failure record when the run fails.
3. Successful runs must preserve the final extracted destination tree inside the same run-scoped artifact set.
4. Exploratory multipart follow-up runs should include volume-resolution evidence when they are executed.

## 8. Pass or Fail Gate

1. The Android harness runs the full matrix on-device and records the runtime envelope.
2. `.zip`, `.rar`, and `.7z` coverage is proven with evidence or marked unsupported with explicit reason.
3. Flattening behavior is proven:
   - no archive-named folder is created,
   - extracted outputs are written directly into the destination subfolder.
4. Recursion off versus on is proven with explicit output-manifest difference and bounded termination.
5. Successful-pass archive deletion or retention behavior is proven with evidence.
6. Rename ordering is proven when rename is enabled:
   - only final non-archive outputs are renamed,
   - archive filenames are not renamed.
7. One deterministic corruption-style failure is captured with concrete runtime evidence.
8. Artifact output shape is complete enough to support the post-run output doc and architecture follow-up.

## 9. Post-Run Output Required

1. After Spike 2 execution completes, write `spike-2-unarchive.md` under [`../outputs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/README.md).
2. That output doc is post-run only:
   - do not pre-create it as a planning document,
   - use this spec as the pre-run source of truth.
3. Post-run output must include:
   - evidence summary,
   - final local unarchive model to build,
   - implementation sequence,
   - caller-owned policy constraints,
   - diagnostics expectations,
   - proven-vs-pending boundary,
   - architecture implications,
   - risk register.

## 10. Primary References

1. [`../apps/spike-2-android/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-2-android/README.md)
2. [`../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md)
3. [`../research/7-zip-jbinding-4android.md`](/Users/nskaria/projects/romulus/docs/spikes/research/7-zip-jbinding-4android.md)
4. [`/Users/nskaria/projects/romulus/docs/behavior/downloads.md`](/Users/nskaria/projects/romulus/docs/behavior/downloads.md)
5. [`/Users/nskaria/projects/romulus/docs/behavior/naming.md`](/Users/nskaria/projects/romulus/docs/behavior/naming.md)
