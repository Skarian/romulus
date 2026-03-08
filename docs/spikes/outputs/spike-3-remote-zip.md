# Spike 3 v2 - Remote ZIP Implementation Handoff

## Purpose

This document tells the implementation agent how to build the remote-ZIP branch proven by Spike 3. It focuses on the runtime branch, transport contract, ZIP identity model, and explicit failure rules required by the migrated app.

## Runtime Model

Remote ZIP is an optional branch that begins only after an earlier resolver stage has already produced an exact remote `.zip` container URL.

The branch should be modeled as:
1. transport probe
2. ZIP enumeration
3. candidate filtering
4. selected-entry copy
5. downstream handoff

Later unarchive, rename, and cleanup logic should operate on selected local outputs, not on the remote ZIP container itself.

## Library And Transport Decision

Adopt now:
- `OkHttp` for `HEAD` and `Range` transport
- custom remote `SeekableByteChannel`
- `Commons Compress` with `ignoreLocalFileHeader=true`

Do not adopt:
- silent whole-archive fallback
- path-only entry identity
- direct dependency on `cloudzip` in the migrated app

## Transport Contract

Remote ZIP mode requires:
- concrete remote `.zip` URL
- usable archive size
- usable byte-range support

Rules:
1. probe transport before enumeration
2. fail remote ZIP mode immediately if byte ranges are not usable
3. keep the channel tail-aware because ZIP parsing is central-directory and end-of-file heavy
4. do not degrade into full-download fallback if the probe or a later range window fails

Range-read validation rules proven by the spike harness:
5. ranged reads must return HTTP `206`, not `200`
6. `206` responses must include a valid `Content-Range`
7. returned `Content-Range` start must match the requested range start
8. response body length must match the advertised range width
9. any violation of those invariants should fail remote ZIP mode as invalid transport behavior, not trigger a degraded fallback

Proven explicit failures:
- no range support -> `PROBE / RANGE_NOT_SUPPORTED`
- constrained range window -> `ZIP_ENUMERATION / RANGE_WINDOW_REJECTED`

High-value source references:
- range-read invariants and failure mapping: [OkHttpRangeClient.kt](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/http/OkHttpRangeClient.kt#L91)
- range-capability gate and metadata-first session setup: [RemoteZipSession.kt](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/zip/RemoteZipSession.kt#L20)

## ZIP Enumeration Contract

1. Open the archive through the remote range client and custom seekable channel.
2. Use `Commons Compress ZipFile` in metadata-first mode with `ignoreLocalFileHeader=true`.
3. Enumerate entries from central-directory metadata without opening streams for the full candidate set.
4. Preserve raw archive counts for diagnostics.

This path worked on:
- normal ZIP
- ZIP64
- large-directory ZIP

The large-directory proof enumerated `149999` visible files from `150000` raw archive entries.

## Candidate Filtering Rules

1. Remove directories from the visible candidate set.
2. Apply ignore globs before user-visible selection.
3. Preserve the ignored set separately for diagnostics.
4. Keep the visible candidate set and selected set explainable and artifactable.

Selected-only proof from the spike:
- ignored `sample.pdf` via `*.pdf`
- copied selected entries only
- did not copy visible but unselected duplicate entries

## Internal Entry Identity

Path alone is not sufficient identity.

The implementation should treat a remote ZIP entry as identified by:
- archive source
- `entryPath`
- `localHeaderOffset`
- compressed size
- uncompressed size
- CRC32 when available

Proven duplicate-name rule:
- `localHeaderOffset` was the disambiguator that made duplicate-name selection safe

The implementation should therefore not let the UI or queueing layer treat filename alone as entry identity.

## Selected-Entry Copy Rules

1. Resolve user selection against stable internal entry identity.
2. Open local headers and entry streams only for selected entries.
3. Copy only those selected entries to local outputs.
4. Name copied outputs in a duplicate-safe way.

The proven harness used zero-padded `localHeaderOffset` prefixes. The migrated app does not need to reuse that exact naming scheme, but it must preserve duplicate safety.

## Failure Taxonomy

Explicit failures proven:
- no-range server -> `PROBE / RANGE_NOT_SUPPORTED`
- constrained-range server -> `ZIP_ENUMERATION / RANGE_WINDOW_REJECTED`
- malformed archive -> `ZIP_ENUMERATION / INVALID_ZIP`
- strong encryption -> `DOWNLOAD / UNSUPPORTED_ENCRYPTION`

Implementation rule:
- fail with a stage-labeled error
- do not silently switch to whole-archive download
- preserve explicit evidence that no fallback was attempted

Encrypted ZIPs are not supported beyond explicit failure handling.

## Diagnostics Contract

Persist at least:
- transport probe evidence
- HTTP trace with `Range` header visibility
- raw archive counts
- ignored count
- visible count
- selected count
- ignore globs used
- readable sample of stable entry identities
- selected-download manifest
- stage-labeled failure artifacts

The implementation should also preserve a success-path signal proving the request path stayed on range semantics rather than silently downgrading to non-range behavior.

## What The App Must Not Assume

1. Do not assume remote ZIP mode is viable without byte ranges.
2. Do not assume filename alone identifies an internal entry.
3. Do not assume small successful fixtures prove zero full-file transfer bytes in every success case.
4. Do not assume remote ZIP mode should self-recover into whole-archive download.
5. Do not assume later local-file processing should operate on the remote ZIP container once selected entries have already been copied out.

## Proven Constraints

Adopt now:
- remote ZIP as an exact-container branch after outer resolution
- metadata-first enumeration through `Commons Compress`
- custom remote `SeekableByteChannel`
- ignore-glob filtering before selection
- duplicate-safe identity using `localHeaderOffset`
- selected-only internal copy
- explicit stage-labeled failures
- no silent full-download fallback

## Still Pending

Not yet explicitly characterized:
- Android runtime integration in the eventual Spike 4 app path
- performance against very large real-world remote ZIPs beyond fixtures
- resumable selected-entry copy over unstable remote hosts
- non-ZIP remote archive formats
- encrypted ZIP support beyond explicit failure handling

## Architecture Implications

The migrated app should separate these concerns:
- resolver produces exact remote container URL
- remote-ZIP module probes transport and enumerates internal entries
- selection layer chooses stable entry identities
- copy layer materializes selected local outputs
- later local-file stages operate on those outputs only

That separation is the main architectural payoff of Spike 3.

## Evidence Root

Latest passing artifact root:
- `docs/spikes/fixtures/generated/spike-3/run-artifacts/2026-03-06T21-01-11Z/`
