# Spike 2 v2 - Android Unarchive Implementation Handoff

## Purpose

This document tells the implementation agent how to build the Android local-unarchive branch proven by Spike 2. It focuses on runtime shape, archive policy ownership, and operational constraints, not on replaying the spike run.

## Runtime Model

Spike 2 proved that local unarchive is an on-device Android branch that begins only after a concrete local file already exists in app storage.

The branch should be modeled as:
1. runtime initialization
2. extraction pass
3. output-policy application
4. recursive pass controller
5. cleanup and final handoff

This is not one library call. The archive library extracts bytes, but Romulus owns policy and orchestration around it.

## Library Decision

Use `7-Zip-JBinding-4Android` only.

Adopted result from the spike:
- no second archive library
- no platform unzip fallback for this branch
- no hidden delegation of flattening, recursion, rename, collision handling, path safety, or cleanup to the library layer

## Supported Runtime Surface

Proven on device:
- `.zip`
- `.rar`
- `.7z`
- unicode ZIP filenames

The implementation should treat these as the proven archive families for the first migration slice.

## Input Contract

The local-unarchive branch should accept:
- concrete local file path
- destination `subfolder`
- unarchive enabled or disabled
- recursive unarchive enabled or disabled
- optional rename rule

Branch behavior:
1. if unarchive is disabled, save the file normally
2. if unarchive is enabled but the file is not a supported archive, save normally without extraction
3. if unarchive is enabled and the file is a supported archive, enter the archive pipeline

## Extraction Rules

1. Initialize `7-Zip-JBinding-4Android` and capture its runtime envelope when first used or when initialization fails.
2. Open the local archive from app storage.
3. Extract into a working destination already mapped to the queue entry `subfolder`.
4. Apply Romulus output policy as files are materialized.

The implementation should point downstream state at final extracted outputs, not at archive containers that have already been consumed.

High-value source references:
- runtime initialization envelope and archive open path: [SevenZipArchiveExtractor.kt](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipArchiveExtractor.kt#L23)
- per-item target resolution and finalization behavior: [SevenZipArchiveExtractor.kt](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-2-android/app/src/androidTest/java/com/romulus/spikes/spike2/SevenZipArchiveExtractor.kt#L215)

## Output Policy Ownership

Romulus owns these rules:

### Flattening

- final non-archive outputs land directly under the destination subfolder
- do not create an archive-named wrapper folder in the final output tree

### Rename Ordering

- rename applies only to final non-archive outputs
- rename does not apply to archive filenames
- inner archives keep their original names until and unless they are themselves opened

### Collision Handling

- when output names collide, suffix with ` (n)`

### Path Safety

- reject writes that would escape the destination root
- keep this as app-owned policy even though traversal-attempt proof is still pending

### Cleanup

- delete an archive only after that archive's extraction pass succeeds
- retain a failed archive for inspection and error reporting

### Per-Item Finalization

- folders are never extracted as output files
- target path resolution happens before bytes are finalized
- rename, collision, and path-rejection decisions happen before promotion into the final output tree
- extracted bytes are written to temp files first
- only `ExtractOperationResult.OK` is promoted into the final destination
- non-OK item results must become structured failure evidence even if archive open itself succeeded

## Recursive Unarchive Controller

Recursive unarchive is not a library primitive. It should be implemented as a bounded controller loop.

Rules:
1. if recursion is off, stop after the first pass
2. if recursion is on, scan outputs from the current pass for supported archive candidates
3. feed those candidates into the next bounded pass
4. stop when no supported archive candidates remain
5. stop immediately on failure

Proven behavior from the spike:
- recursion off left the extracted inner archive in place
- recursion on consumed that inner archive on a second bounded pass

## Failure Handling

The implementation should produce structured archive-open and archive-extract failures.

Proven deterministic failure shape:
- a run-local intentionally broken `.7z` copy failed with `SevenZipException: Archive file (format: 7z) can't be opened`

Required behavior:
1. stop recursion immediately on the failed pass
2. retain the failed archive
3. surface which pass failed
4. preserve outputs already written by earlier successful passes
5. if individual extracted items return non-OK operation results, treat that as a real extraction failure, not as a soft warning

## Diagnostics Contract

Persist at least:
- runtime initialization envelope
- per-pass input archive identity
- archive family
- recursive pass number
- per-item extraction outcomes
- final outputs written
- cleanup decision per archive
- structured failure artifact when present

The spike artifact shape is a good minimum contract:
- `session.json`
- `runtime-init.json`
- per-pass input and output manifests
- per-item extraction results
- `cleanup.json`
- `failure.json`

The implementation should preserve enough evidence to explain:
- why recursion stopped
- which archive failed
- which outputs were already created
- which archives were deleted vs retained

## Proven Constraints

Adopt now:
- `7-Zip-JBinding-4Android` as the archive runtime
- caller-owned output policy around the library
- flattening into the destination subfolder
- bounded recursive controller
- rename applied only to final non-archive outputs
- successful-archive deletion and failed-archive retention
- collision suffixing with ` (n)`

## Still Pending

Not yet explicitly characterized:
- unarchive-disabled branch
- unsupported-file branch where save should proceed without extraction
- explicit traversal-attempt rejection evidence
- encrypted or password-protected archives
- multipart archives
- broader Android-device coverage
- large real-world archive performance and memory behavior

These are follow-up items, not blockers for the first migration slice.

## Architecture Implications

The implementation should keep archive extraction and archive policy as separate modules:
- library layer: archive open and byte extraction
- app layer: flattening, rename ordering, recursion, collision handling, path safety, cleanup, and failure reporting

This separation is not stylistic. It was directly validated by the spike.

## Evidence Root

Latest passing artifact root:
- `docs/spikes/fixtures/generated/spike-2/run-artifacts/latest/`
