# Spike 1 Spec - Resolver, Enumeration, and Provider Acquisition

## 1. Goal

1. Prove one Real-Debrid runtime flow through add, add-again, selection verification, provider acquisition, link readiness, and unrestrict.
2. Preserve two distinct product behaviors in that one runtime flow:
   - `select all`,
   - `subset selected`.
3. Prove that changed selection should be modeled by adding the same magnet again while earlier provider torrents still exist, not by reselecting on an already-selected torrent id.
4. Formalize the Real-Debrid lifecycle as three stages:
   - selection stage,
   - provider-acquisition stage,
   - link stage.
5. Characterize that one runtime flow under two provider-acquisition profiles:
   - likely-cached,
   - known-uncached.

## 2. Execution Boundary

1. Spike 1 runs as a disposable desktop Kotlin or JVM CLI.
2. Do not turn Spike 1 into an Android harness.
3. Spike 1 documents one runtime flow with two provider-acquisition profiles.
4. Separate operator commands are allowed as spike convenience only; they do not imply different application paths.
5. Harness location, intended command surface, and artifact paths live in [`../apps/spike-1-cli/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/spike-1-cli/README.md).

## 3. Inputs

1. Likely-cached magnet fixture.
2. Selection variants covering:
   - root scope (`path` omitted, null, or blank means `/`),
   - directory scope,
   - exact `.zip` path scope.
3. Expected selected files for each likely-cached profile run.
4. One user-provided uncached magnet fixture for the long-running provider-acquisition phase.
5. One user-provided uncached selected-file path under that uncached magnet.
6. User-provided Real-Debrid input comes from local ignored file `docs/spikes/.env.local`.
7. `path` is not a Real-Debrid API input:
   - the spike uses local `path` input to filter provider `files[].path` values returned by `GET /torrents/info/{id}`,
   - the result of that filtering is the candidate set used to build the `selectFiles` payload.
8. The likely-cached profile matrix may run even when matching provider torrents for the same magnet hash already exist in the account.
9. Existing provider torrents for the same hash do not change the Spike 1 lifecycle model: add-again remains the way to create a fresh selected state.

## 4. Product Behavior Rules

1. After path traversal and ignore-glob filtering, if the user selects the full visible candidate set, treat that as `select all`.
2. `Select all` uses one provider torrent and selects the full visible candidate set in that provider torrent.
3. If the user selects fewer than the full visible candidate set, treat that as `subset selected`.
4. `Subset selected` uses one provider torrent per selected file.
5. Provider-facing implementation note:
   - if later evidence proves one user-selected file must be expressed as grouped provider file IDs, keep the product behavior as `per selected file` and perform the grouping internally.

## 5. Known API Baseline From Research

1. Base URL: `https://api.real-debrid.com/rest/1.0`.
2. Auth: `Authorization: Bearer <token>`.
3. Canonical endpoint sequence to validate:
   - `GET /torrents`,
   - `GET /torrents/availableHosts` when add is needed,
   - `POST /torrents/addMagnet`,
   - `GET /torrents/info/{id}`,
   - `POST /torrents/selectFiles/{id}`,
   - follow-up `GET /torrents/info/{id}` polling,
   - `POST /unrestrict/link`.
4. Official torrent-info fields relevant to this spike include:
   - `status`,
   - `progress`,
   - `speed`,
   - `seeders`,
   - `ended`,
   - `links`,
   - `files`.
5. Host policy for add flow:
   - call `GET /torrents/availableHosts`,
   - use `availableHosts.first().host`,
   - fail the run if no hosts are returned.
6. Selection payload rule:
   - send explicit provider file IDs for subset runs,
   - do not send `files=all` unless intentionally selecting all visible candidate files.
7. Bounded retry rule for Spike 1:
   - only retry immediate post-add `GET /torrents/info/{id}` readiness,
   - do not introduce broader hidden fallback behavior in this spike.
8. Observed and documented provider states relevant to this spike include:
   - `magnet_conversion`,
   - `waiting_files_selection`,
   - `queued`,
   - `downloading`,
   - `downloaded`,
   - `compressing`,
   - `magnet_error`,
   - `error`,
   - `virus`,
   - `dead`.
9. Observed error classes from research include:
   - provider codes `9`, `21`, `22`, `30`, `34`,
   - HTTP `400`, `401`, `429`, `502`, `503`, `504`.
10. Official rate-limit fact to keep in view:
    - Real-Debrid documents `250 requests per minute`.

## 6. Runtime Assumptions

1. `[USER]` Real-Debrid UI observation: if a torrent takes more than 72 hours to complete, Real-Debrid automatically deletes it.
2. Spike 1 should therefore treat long-running provider acquisition as normal up to that 72-hour bound.
3. Spike 1 should not use a short fixed timeout to decide that long-running provider acquisition has failed.

## 7. Evidence Questions

1. What endpoint sequence and state transitions are required from add, add-again, or reuse to downloadable link?
2. Which file identity fields remain stable across polling and are safe for selection mapping?
3. What happens when the torrent already exists in the user account but the desired files differ from the current selection?
4. How does provider behavior change when only a subset of file IDs is submitted?
5. How do returned links relate to the verified selected provider file set, and what mismatch patterns appear?
6. Does local path-scope filtering produce the correct provider file-ID selection payload for root, directory, and exact `.zip` inputs?
7. What provider-acquisition statuses and progress fields are necessary to explain long-running work to users?
8. Which states mean keep waiting, which states mean links are ready, and which states should be treated as terminal failure?
9. What deterministic failure looks cleanly reproducible, and what transient failure is naturally observed if any?
10. Which parts of the runtime flow stay the same across likely-cached and known-uncached provider-acquisition profiles, and which parts differ only by wait profile?

## 8. Likely-Cached Profile Matrix

1. Run A: add the same magnet again with root scope and a desired file set that differs from Run B.
2. Run B: add flow with directory scope and subset selection.
3. Run C: add the same magnet again and apply exact `.zip` path selection where the selected candidate is the outer archive file only.
4. Run D: selected-only proof showing that the exact-path torrent from Run C can be reused without changing selection, and then only the links returned from that verified state are unrestrict-called and downloaded.
5. Run E: deterministic terminal failure observation.
6. Optional observation:
   - if a transient failure occurs during normal runs, capture it,
   - if no transient failure is observed, record it as `UNOBSERVED`.

## 9. Known-Uncached Profile

1. This profile exercises the same runtime flow as the likely-cached matrix.
2. It may still use a separate explicit operator command for spike convenience.
3. It is not a different application path.
4. It uses:
   - one user-provided uncached magnet,
   - one user-provided uncached selected-file path.
5. It must prove the long-running provider-acquisition lifecycle for selected content that is not immediately link-ready.
6. Required behavior for this profile:
   - add magnet,
   - enumerate provider files,
   - resolve the selected file to provider file IDs,
   - submit `selectFiles`,
   - poll `GET /torrents/info/{id}` over time,
   - persist provider-acquisition status, progress, speed, seeders, and link readiness,
   - only unrestrict once returned links are actually available.
7. This profile must be resumable and artifact-driven:
   - do not rely on one monolithic blocking process,
   - preserve intermediate artifacts so the operator can stop and resume later if needed.
8. The operator may deliberately stop the long-running profile before terminal completion.
9. If the operator stops early, the profile must be marked `INCOMPLETE`, not silently treated as pass or failure.

## 10. Required Trace Artifacts

1. Per-run trace artifacts must exist for each likely-cached matrix run and for the known-uncached profile.
2. Sanitized request or response timeline per run.
3. File identity snapshot before selection and after polling.
4. Selection payload record showing submitted file IDs.
5. Link-land record showing submitted selected-file set, provider-selected file set after polling, returned links, mapping status, and actual unrestrict calls made.
6. Timeout records must include the last polled torrent info snapshot and polling summary with attempts, elapsed time, and last observed status.
7. The known-uncached profile must also capture:
   - status timeline,
   - progress timeline,
   - speed timeline,
   - seeder timeline,
   - first link-ready timestamp when it appears,
   - operator stop or resume markers when applicable.
8. Error sample records with stage and outcome.

## 11. Pass or Fail Gate

1. All likely-cached profile Evidence Questions are answered with run evidence or marked `UNCONFIRMED` with explicit reason.
2. Add, add-again, and downstream reuse branches are all proven with evidence.
3. The changed-selection case is proven with evidence by adding the same magnet again while earlier provider torrents already exist.
4. Subset-selection behavior is observed with explicit file-ID payload evidence and no accidental `files=all`.
5. Selected-only proof is present:
   - the provider-selected file set after polling matches the requested selected IDs for run D,
   - only the links returned from that verified selected state are unrestrict-called,
   - only those returned links are downloaded.
6. One deterministic terminal failure is captured with concrete API evidence.
7. If no transient failure appears during the likely-cached profile runs, it is marked `UNOBSERVED` rather than invented.
8. Existing matching torrents in the account do not block the spike and do not change the expected add-again lifecycle for changed selection.
9. The known-uncached profile is complete only when it either:
   - reaches provider link-ready success with timeline evidence, or
   - reaches a terminal provider failure with timeline evidence.
10. If the known-uncached profile is operator-stopped before either outcome, it must be marked `INCOMPLETE` and carried forward as partial evidence, not converted into a false pass or failure.

## 12. Post-Run Output Required

1. After Spike 1 execution completes, maintain `spike-1-resolution.md` under [`../outputs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/README.md).
2. That output doc is post-run only:
   - do not pre-create it as a planning or runbook document,
   - use this spec as the pre-run source of truth.
3. `spike-1-resolution.md` must serve as the migration-facing Real-Debrid integration guide and include:
   - exact endpoint sequence,
   - likely-cached profile evidence summary,
   - all-selected path guidance,
   - subset-selected path guidance,
   - provider-acquisition profile guidance,
   - provider-acquisition status and progress handling,
   - error states and handling,
   - rate-limit considerations,
   - architecture implications,
   - explicit `UNCONFIRMED` or pending areas that remain after the known-uncached evidence on hand.

## 13. Primary References

1. [`../research/mediafusion.md`](/Users/nskaria/projects/romulus/docs/spikes/research/mediafusion.md)
2. [`../research/unchained-android.md`](/Users/nskaria/projects/romulus/docs/spikes/research/unchained-android.md)
3. [`../research/rdt-client.md`](/Users/nskaria/projects/romulus/docs/spikes/research/rdt-client.md)
