# Spike 1 v2 - Real-Debrid Implementation Handoff

## Purpose

This document is the implementation handoff for the Real-Debrid resolution and selection flow. It is not a spike receipt. Its job is to tell the implementation agent what runtime model to build, what API behavior to trust, what not to assume, and which parts remain uncertain.

## Runtime Model

Spike 1 proved one runtime flow with three stages:

1. Selection
   - add or add-again the magnet
   - read provider files from `GET /torrents/info/{id}`
   - translate local path intent into provider file IDs
   - call `POST /torrents/selectFiles/{id}`
   - verify the provider-selected file set after polling
2. Provider acquisition
   - continue polling `GET /torrents/info/{id}`
   - treat `status`, `progress`, `speed`, `seeders`, `ended`, and `links` as the provider-acquisition surface
   - do not treat missing `links[]` as failure by itself
3. Link handling
   - once `links[]` is non-empty, treat returned links as the downstream unit
   - call `POST /unrestrict/link` on returned links only
   - download from unrestricted links only

The implementation should model these as explicit stages with persisted state transitions, not as one blocking procedure.

## API Contract

Base URL: `https://api.real-debrid.com/rest/1.0`

Auth:
- `Authorization: Bearer <token>`

Observed sequence to implement:
1. `GET /torrents/availableHosts`
2. choose `availableHosts.first().host`
3. `POST /torrents/addMagnet`
4. `GET /torrents/info/{id}` until the torrent becomes readable
5. `POST /torrents/selectFiles/{id}`
6. `GET /torrents/info/{id}` polling for selection verification and later provider acquisition
7. `POST /unrestrict/link` on returned `links[]`

Provider fields the runtime should preserve:
- `id`
- `hash`
- `status`
- `progress`
- `speed`
- `seeders`
- `ended`
- `links`
- `files`

## What The App Must Treat As Authoritative

1. `GET /torrents/info/{id}` is the canonical state source.
2. Provider `files[]` is the only source for selectable provider file identity.
3. Local `path` is application logic only. It is not a provider API concept.
4. Verified provider-selected file IDs are the authoritative selection state.
5. Returned `links[]` are the authoritative download inputs.

The implementation must keep selection identity and download identity separate.

## Selection Rules

1. Filter provider `files[].path` locally to derive candidate files.
2. For subset selection, send explicit comma-separated provider file IDs.
3. Use `files=all` only when intentionally selecting the full visible candidate set.
4. After `selectFiles`, poll until the provider-selected file set matches the requested IDs or a terminal outcome is reached.

Important proven constraint:
- same-id reselection is not reliable enough
- the spike observed HTTP-success `selectFiles` calls that did not actually change provider-selected file IDs on an already-selected torrent
- changed selection should therefore use add-again, creating a fresh provider torrent

## Product Behavior To Build

### All Selected

- one provider torrent
- select the full visible candidate set
- wait for provider acquisition
- unrestrict returned links
- download from unrestricted links

### Subset Selected

- one provider torrent per selected file
- changed selection means add-again, not reselection on an existing torrent
- each selected file gets its own provider-acquisition lifecycle

This is a product choice confirmed by the spike. If later evidence shows that one user-selected file maps to grouped provider file IDs internally, keep the product behavior the same and hide the grouping inside the provider layer.

## Provider-Acquisition Rules

Statuses observed as active:
- `magnet_conversion`
- `waiting_files_selection`
- `queued`
- `downloading`
- `compressing`

Status observed as link-ready:
- `downloaded`

Statuses to treat as terminal-failure candidates:
- `magnet_error`
- `error`
- `virus`
- `dead`

Practical readiness rule:
- file selection is ready when provider-selected file IDs are verified
- provider acquisition is ready when `links[]` is non-empty
- download is ready when unrestrict succeeds on those returned links

## Polling Rules

1. Use a narrow bounded readiness retry only for immediate post-add readability.
2. Treat selection verification polling and provider-acquisition polling as separate concerns.
3. Do not use a short fixed timeout to declare provider-acquisition failure.
4. Respect the documented provider limit of `250 requests per minute`.
5. Keep polling cadence deliberate and user-visible.

Observed spike result:
- a 60-second link-ready budget was too short for exact-path characterization
- the proven spike harness succeeded with `120` polls at `2` seconds each for the bounded wait path

Known-uncached rule:
- do not model uncached acquisition as one monolithic blocking wait
- it must be resumable from persisted state

## Data And Persistence Requirements

The implementation should persist enough state to resume safely:
- provider torrent ID
- provider magnet hash
- selected local path intent
- requested provider file IDs
- verified provider-selected file IDs
- provider-acquisition timeline
- first link-ready timestamp if observed
- returned restricted links when observed
- lifecycle markers for stage transitions
- retryable diagnostics from malformed poll payloads or malformed local resume state

The runtime should persist diagnostics separately from terminal provider failures.

Resume invariants proven by the spike harness:
- resume must refuse to continue if the persisted checkpoint magnet hash no longer matches the current magnet
- resume must refuse to continue if the persisted checkpoint selected path no longer matches the current selected path

High-value source references:
- resume entry and checkpoint invariants: [Spike1Runner.kt](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/Spike1Runner.kt#L116)
- persisted uncached progress artifacts: [ArtifactWriter.kt](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-1-cli/src/main/kotlin/com/romulus/spikes/spike1/service/ArtifactWriter.kt#L140)

Concrete artifact contract worth carrying forward:
- `checkpoint.json`
- `lifecycle-markers.json`
- `provider-acquisition-timeline.json`
- `link-mapping.json`

## Hardening Lessons From The Spike App

1. `progress` must be treated as decimal-capable.
   - live evidence observed `100.0`
   - integer-only parsing is wrong
2. Long-running provider polling can return malformed or truncated payloads.
   - these should be treated as retryable diagnostics, not immediate terminal failure
3. Malformed local resume state should also fail softly.
   - preserve diagnostics
   - continue or rebuild the polling loop when the provider state still indicates active work
4. Existing matching torrents in the account must not block the runtime.
   - add-again worked even while earlier matching torrents still existed
   - cleanup is an operator convenience, not a product requirement

## What The Implementation Must Not Assume

1. Do not assume one selected provider file always maps to one returned link.
2. Do not assume returned link order is stable file identity.
3. Do not assume successful `selectFiles` HTTP status means selection actually changed.
4. Do not assume `progress` is integer-shaped.
5. Do not assume uncached provider acquisition finishes within one foreground process lifetime.

## Errors And Failure Taxonomy

Deterministic failure proven:
- invalid magnet produced HTTP `400`
- provider code `30`
- provider message `torrent_file_invalid`

Observed/documented error classes worth carrying into implementation:
- provider codes `9`, `21`, `22`, `30`, `34`
- HTTP `400`, `401`, `429`, `502`, `503`, `504`

The implementation should distinguish:
1. provider terminal state
2. retryable provider transport or payload anomaly
3. local resume-state corruption
4. selection mismatch after `selectFiles`

## Cleanup And Account State

1. Successful runtime behavior must not depend on deleting old provider torrents first.
2. Cleanup tooling is useful for spike operation only.
3. Subset selection can create many provider torrents from one user action, so diagnostics and cleanup logic must tolerate fan-out.

## Proven Constraints

Adopt now:
- `GET /torrents/info/{id}` as the canonical state surface
- add-again for changed selection
- explicit provider file ID selection
- stage separation between selection, provider acquisition, and link handling
- returned links as downstream download units
- resumable uncached acquisition

Still uncertain:
- whether selected provider files can ever be rejoined to returned links more strongly than the current stage split
- whether a selected file always maps cleanly to one raw provider file ID
- broader uncached timing behavior outside the current fixture

## Evidence Roots

Likely-cached proof:
- `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T185325Z/`

Known-uncached proof:
- `docs/spikes/fixtures/generated/spike-1/run-artifacts/20260306T205927Z/`
