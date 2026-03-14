# Spike 5 Spec - `tryAll` Whole-Torrent API Validation

## 1. Goal

1. Validate the Real-Debrid API behavior that the `tryAll` ExecPlan depends on for a known cached magnet.
2. Confirm the add-magnet plus `selectFiles(id, "all")` flow returns provider state compatible with the planned `status == downloaded` and `progress == 100` gate.
3. Capture the whole-torrent provider status timeline as evidence for the `tryAll` implementation plan.

## 2. Execution Boundary

1. Spike 5 runs as a disposable desktop Kotlin or JVM CLI.
2. It is a narrow post-architecture-validation probe, not a production code path and not an Android harness.
3. Harness location, command surface, and artifact paths live in [`../apps/spike-5-try-all-api/README.md`](../apps/spike-5-try-all-api/README.md).

## 3. Inputs

1. One user-provided Real-Debrid API token.
2. One user-provided magnet that is expected to be fully cached on Real-Debrid.
3. One user-provided exact provider file path under that magnet to use for whole-torrent folder-unrestrict proof.
4. User-provided input comes from local ignored file `docs/spikes/apps/spike-5-try-all-api/.env.local`.

## 4. Required Flow

1. Derive the magnet info hash locally.
2. List all existing account torrents matching that hash.
3. Delete all matching torrents from the account before the probe starts.
4. Call `GET /torrents/availableHosts`.
5. Call `POST /torrents/addMagnet`.
6. Wait for `GET /torrents/info/{id}` to expose provider files.
7. Call `POST /torrents/selectFiles/{id}` with `files=all`.
8. Poll `GET /torrents/info/{id}` and record provider statuses until one of these outcomes occurs:
   - `status == downloaded` and `progress == 100`,
   - a terminal provider failure state is reached,
   - the bounded polling budget expires.
9. After whole-torrent cached success, take the whole-torrent link, call `POST /unrestrict/folder`, map the configured file path to one returned child link if possible, and prove that one file can be downloaded.

## 5. Evidence Questions

1. Does `selectFiles(id, "all")` succeed for the cached magnet without requiring file-id expansion?
2. What status sequence appears between add, whole-torrent selection, and the cached ready verdict?
3. Is `status == downloaded` with `progress == 100` sufficient to identify the reusable whole-torrent state for this case?
4. After deleting prior matching torrents, does the new probe produce exactly one provider torrent for that magnet?
5. After whole-torrent cached success, can the whole-torrent link be expanded through `unrestrict/folder` into a concrete downloadable child link for the configured file?

## 6. Required Trace Artifacts

1. Sanitized input summary with env path, info hash, and token presence only.
2. Matching-account-torrent snapshot before deletion.
3. Deletion results for the matching torrents.
4. Available-host response.
5. Added-magnet response.
6. Initial torrent info after add when files become visible.
7. Immediate post-selection torrent info.
8. Provider status timeline across polling.
9. Selected-file proof artifact with requested path, resolved file id, folder-unrestrict result, candidate-count result, and download result.
10. Final summary with cached verdict, final status, final progress, observed statuses, and folder-unrestrict proof result.

## 7. Pass or Fail Gate

1. The spike passes if the known cached magnet reaches `status == downloaded` and `progress == 100` after `selectFiles(id, "all")`, and the configured file can then be mapped and downloaded from `unrestrict/folder` without adding another torrent.
2. The spike fails if the provider reaches a terminal failure state, the bounded poll budget expires without the cached verdict, or the folder-unrestrict proof fails.
3. Any unexpected API contract mismatch must be recorded explicitly rather than inferred away.

## 8. Post-Run Output Required

1. After execution completes, maintain `spike-5-try-all-api.md` under [`../outputs/README.md`](../outputs/README.md).
2. The output doc must summarize the observed API sequence and state transitions without exposing the token, full magnet URI, or unrestricted links.
