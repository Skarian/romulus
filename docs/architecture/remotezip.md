# `remotezip/` Package Architecture

## Purpose

`remotezip/` owns exact-container ZIP probing, metadata enumeration, stable archive-entry identity, and selected-entry copy.
It exists so archive-selection mode can operate on one remote ZIP entry without downloading the whole container.

## Responsibilities

- Probe remote archive URLs for usable range support.
- Validate that range responses are structurally safe for ZIP metadata reads.
- Enumerate ZIP metadata remotely from the exact outer-container URL.
- Assign duplicate-safe archive-entry identity.
- Copy one selected entry into a caller-provided sink, including resumable copy from a saved offset.

## Boundaries

- `remotezip/` does not own source snapshots or ignore filtering.
- `remotezip/` does not own queue state or retry policy.
- `remotezip/` does not own final output naming or extraction.
- `remotezip/` does not own Real-Debrid link resolution.

## Public Seams

- Remote ZIP enumeration.
- Selected archive-entry copy.

## Durable Invariants

1. The package never falls back to whole-container download.
2. Archive-entry identity must survive duplicate filenames.
3. Selected-entry copy rematches by stable identity before moving bytes.
4. Resume support must continue from a caller-provided offset without degrading into whole-file fallback.

## Key Flows

### Enumerate

1. Probe the exact container URL for range support.
2. Read ZIP metadata remotely.
3. Return stable entry descriptors to `source/`.

### Copy

1. Receive one archive-entry identity from queue-owned execution state.
2. Rematch that identity against the remote archive.
3. Copy only that entry into the provided output sink, reporting progress as needed.
