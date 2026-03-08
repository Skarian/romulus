# Spike 4 Spec - End-to-End Integration

## 1. Goal

1. Prove a focused Android app can execute the accepted Spike 1, Spike 2, and Spike 3 outcomes together on target runtime.
2. Prove selected-only download, archive-selection, optional unarchive, flattening, archive deletion, rename, and diagnostics remain coherent end to end.
3. Keep Spike 4 limited to externally meaningful execution shape and evidence, not harness build planning or broader product completeness.

## 2. Preconditions and Inputs

1. Spike 4 begins only after these post-run outputs are accepted:
   - `docs/spikes/outputs/spike-1-resolution.md`,
   - `docs/spikes/outputs/spike-2-unarchive.md`,
   - `docs/spikes/outputs/spike-3-remote-zip.md`.
2. Spike 4 composes those accepted Spike 1-3 outcomes; it does not re-decide their resolver, remote-zip, or unarchive baselines.
3. User-provided Spike 4 inputs come from local ignored file `docs/spikes/.env.local`.
4. The prepared Spike 4 fixture workspace comes from `docs/spikes/fixtures/generated/`, especially `docs/spikes/fixtures/generated/spike-4/user-input.md`.
5. Repo-provided archive fixtures used by the integrated flow are the generated copies under `docs/spikes/fixtures/generated/` prepared by the fixture workflow in `docs/spikes/fixtures/README.md`.

## 3. Execution Shape

1. The harness is a focused Android app.
2. In archive-selection mode, the outer `.zip` container is not a user-visible queue row.
3. File rows continue to display original names while final output names may differ after rename.
4. Stage order to validate is:
   - resolve the magnet to the desired provider file or exact `.zip` container,
   - remotely enumerate internal `.zip` entries when exact `.zip` path mode is configured,
   - apply ignore-glob filtering before user-visible selection,
   - queue and download only the selected file or internal-file outputs,
   - apply unarchive only when enabled,
   - delete each successfully extracted archive after its successful extraction pass,
   - apply rename only to final non-archive outputs,
   - persist diagnostics and run evidence.
5. Stage failures must remain diagnosable with stage label and context.
6. Harness location, intended command surface, and artifact paths live in [`../apps/spike-4-android/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-4-android/README.md).

## 4. Run Matrix

1. Run A: archive-selection happy path using exact `.zip` path, selected internal subset, `unarchive` on, and rename on.
2. Run B: the same integrated flow with `unarchive` off so selected-only copied archives remain visible without extraction.
3. Run C: deterministic stage failure with diagnostics enabled so failure evidence is explicit.

## 5. Required Artifacts

1. The harness must write run artifacts in a form that keeps Run A, Run B, and Run C distinguishable.
2. Required run artifacts are:
   - end-to-end stage timeline from resolve through final output manifest,
   - selection and identity trace across provider file, internal entry when applicable, queue task, and final output,
   - output manifests before unarchive, after the extraction pass when unarchive is enabled, and at final completion,
   - cleanup evidence showing archive deletion after successful extraction passes,
   - diagnostics evidence showing stage labels, outcomes, and failure context.
3. After Spike 4 execution completes, write `spike-4-integration.md` under [`../outputs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/README.md).
4. That output doc is post-run only and must follow the output contract in [`../outputs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/README.md).

## 6. Pass or Fail Gate

1. The focused Android app reproduces the integrated flow with explicit evidence for each required stage.
2. Cross-stage identity and selected-only behavior remain traceable and correct.
3. Successful extraction-pass deletion and final-file rename ordering are proven.
4. Diagnostics capture stage-labeled success and failure events.
5. Recursive unarchive is out of scope for Spike 4 and is not a pass or fail requirement.
6. Integration blockers and architecture-impacting unknowns are explicit.

## 7. Primary References

1. [`/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md`](/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md)
2. [`/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md)
3. [`/Users/nskaria/projects/romulus/docs/spikes/apps/spike-4-android/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-4-android/README.md)
4. [`/Users/nskaria/projects/romulus/docs/behavior/source.md`](/Users/nskaria/projects/romulus/docs/behavior/source.md)
5. [`/Users/nskaria/projects/romulus/docs/behavior/files.md`](/Users/nskaria/projects/romulus/docs/behavior/files.md)
6. [`/Users/nskaria/projects/romulus/docs/behavior/archive-selection.md`](/Users/nskaria/projects/romulus/docs/behavior/archive-selection.md)
7. [`/Users/nskaria/projects/romulus/docs/behavior/downloads.md`](/Users/nskaria/projects/romulus/docs/behavior/downloads.md)
8. [`/Users/nskaria/projects/romulus/docs/behavior/naming.md`](/Users/nskaria/projects/romulus/docs/behavior/naming.md)
9. [`/Users/nskaria/projects/romulus/docs/behavior/diagnostics.md`](/Users/nskaria/projects/romulus/docs/behavior/diagnostics.md)
