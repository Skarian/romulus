# Spike 3 Spec - Remote Zip Enumeration and Selective Internal Download

## 1. Goal

1. Prove exact remote `.zip` archive-selection behavior over HTTP without relying on an explicit full-archive fallback path.
2. Prove we can remotely enumerate internal files, filter them to the user-visible candidate set, and download only selected internal files.
3. Keep Spike 3 focused on remote `.zip` handling only; Real-Debrid torrent resolution and local unarchive behavior are out of scope.

## 2. Inputs

1. The Spike 3 fixture workspace is prepared through [`../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md).
2. Each run case carries the remote zip URL, ignore-glob set, and selected internal-file set for that case.

## 3. Execution Shape

1. Execute Spike 3 as a disposable desktop JVM or Kotlin harness.
2. The harness includes a tiny local HTTP server that serves the Spike 3 test zips over plain HTTP.
3. Spike 3 does not use an Android harness.
4. Successful runs stay on the remote range-based path; this spike does not fall back to a full local archive download.
5. Harness location, intended command surface, and artifact paths live in [`../apps/spike-3-remote-zip/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-3-remote-zip/README.md).

## 4. Research-Grounded Constraints

1. Range-capable HTTP access is required for the intended remote enumeration path.
2. ZIP64 and large-central-directory archives are required coverage.
3. Duplicate names or nested paths are required coverage, so entry selection cannot rely on filename alone.
4. Malformed, encrypted, or otherwise unsupported zip cases are explicit failure coverage, not silent fallback coverage.
5. Large-directory enumeration must stay metadata-first: open the archive through Commons Compress without eagerly resolving every local file header, and resolve entry streams only for selected entries.

## 5. Run Matrix

1. Run A: enumerate internal files for a normal range-enabled zip.
2. Run B: enumerate a ZIP64 or large-central-directory fixture.
3. Run C: apply ignore-glob filtering, then select and download only chosen internal files from a fixture with duplicate basenames or nested paths.
4. Run D: capture deterministic failure when range support is missing or constrained.
5. Run E: capture deterministic failure with malformed, encrypted, or otherwise unsupported zip metadata.

## 6. Required Evidence

1. An HTTP trace that shows remote enumeration and selected-entry fetch activity.
2. A candidate-set record that shows enumerated entries, ignored entries, visible entries, and selected entries.
3. An output manifest that proves selected entries were written and unselected entries were not.
4. Failure records that identify the fixture, stage, and outcome for each failure run.
5. The large-directory case must record metadata-first parse evidence rather than devolving into one archive-body read per entry.

## 7. Pass or Fail Gate

1. Remote enumeration works for at least one canonical range-enabled fixture.
2. Entry selection survives duplicate-name or nested-path cases without collapsing to name-only identity.
3. Ignore-glob filtering and selected-only proof are present with explicit candidate and output evidence.
4. Successful selected-only runs do not use a non-range full-archive fallback path as the execution path.
5. Deterministic failures are captured for missing or constrained range support and for invalid or unsupported zip metadata.
6. After execution completes, write `spike-3-remote-zip.md` under [`../outputs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/README.md) as the post-run evidence handoff.

## 8. Primary References

1. [`/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md`](/Users/nskaria/projects/romulus/docs/architecture/SPIKE.md)
2. [`../research/cloudzip.md`](/Users/nskaria/projects/romulus/docs/spikes/research/cloudzip.md)
3. [`../research/commons-compress.md`](/Users/nskaria/projects/romulus/docs/spikes/research/commons-compress.md)
4. [`../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md)
