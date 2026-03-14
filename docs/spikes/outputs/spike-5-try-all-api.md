# Spike 5 `tryAll` API Validation Failure

## 1. Run context

1. Latest folder-unrestrict run timestamp: `2026-03-14T03:21:26Z` through `2026-03-14T03:21:33Z`.
2. Latest folder-unrestrict artifact root: `docs/spikes/fixtures/generated/spike-5/run-artifacts/20260314T032126Z`.
3. Prior same-torrent reselection artifact root: `docs/spikes/fixtures/generated/spike-5/run-artifacts/20260314T025923Z`.
4. Prior cached-only proof artifact root: `docs/spikes/fixtures/generated/spike-5/run-artifacts/20260314T023908Z`.
5. Inputs came from local ignored env file `docs/spikes/apps/spike-5-try-all-api/.env.local`.
6. Sanitized magnet identity used for this run set: info hash `6bb5b6cc69a34c9b75b40cde4d8b5bc97279c146`.
7. References:
   - [`../specs/spike-5-try-all-api.md`](../specs/spike-5-try-all-api.md)

## 2. Evidence summary

1. The cached-only proof run deleted 15 existing matching-account torrents and confirmed the fresh whole-torrent path reached cached success immediately after `selectFiles("all")`.
2. The same-torrent reselection rerun deleted the one remaining matching torrent from the earlier cached-only proof, repeated the same whole-torrent check, and failed to narrow the provider-selected set after `selectFiles(id, "577")`.
3. The latest folder-unrestrict rerun deleted the one remaining matching torrent from the reselection run, repeated the whole-torrent cached proof, and then probed `POST /unrestrict/folder` on the returned whole-torrent link.
4. Across all three runs, the fresh `addMagnet` result exposed files in `waiting_files_selection` state before selection.
5. Across all three runs, `POST /torrents/selectFiles/{id}` with `files=all` succeeded.
6. In the folder-unrestrict run, the immediate post-selection `GET /torrents/info/{id}` again already reported:
   - `status: downloaded`
   - `progress: 100.0`
   - `links: 1`
7. The observed state sequence for the whole-torrent probe remained:
   - pre-selection: `waiting_files_selection`
   - post-selection: `downloaded`
8. The bounded poll loop still did not need extra retries because the cached verdict was already true on the first post-selection info read.
9. The folder-unrestrict probe used the one returned whole-torrent link and received exactly one child entry:
   - `filename: XGLCM2TVQH42K1C2`
   - `link: https://real-debrid.com/d/XGLCM2TVQH42K1C2`
10. That folder response did not preserve the configured target filename `/ROMs/Nintendo - Game Boy Advance.zip` or any other obvious child-file identity, so the spike found `0` candidate child links and correctly treated the selected-file proof as `FAIL`.

## 3. Decision proposal

1. Keep the planned `tryAll` gate of `status == downloaded` and `progress == 100` as the positive whole-torrent cached verdict.
2. Keep the planned `selectFiles(id, "all")` implementation for the whole-torrent preflight.
3. Do not assume a fully selected cached torrent can be safely narrowed by same-torrent reselection or by `unrestrict/folder` into one target-file download path. The tested magnet supported neither route.
4. Treat this spike as a failure for the stronger `tryAll` goal. The tested Real-Debrid behaviors did not support “one added magnet serves every later file resolution” for this cached magnet.

## 4. Risk register

1. Non-blocking risk: this spike proves the cached-whole-torrent path, one same-torrent reselection failure, and one folder-unrestrict failure for one known cached magnet, not every possible Real-Debrid torrent shape.
2. Non-blocking risk: fallback deletion behavior when the whole torrent is not cached still belongs to app implementation and app tests.
3. Open unknown: `UNCONFIRMED` whether some cached magnets require more than one post-selection poll before they surface `downloaded / 100`, even though this case did not.
4. Open unknown: `UNCONFIRMED` whether any Real-Debrid path can map a reusable whole-torrent link back to stable child-file identity without adding another torrent.

## 5. Architecture implications

1. The active `tryAll` ExecPlan can treat `status == downloaded` and `progress == 100` as an evidence-backed gate for at least one live cached-whole-torrent case.
2. The app implementation should probe whole-torrent selection before it creates narrower browse or targeted provider torrents for `tryAll=true` entries.
3. The reusable whole-torrent registry remains necessary because the successful probe still created a new provider torrent after the previous matching torrents were deleted; the app needs durable reuse to avoid repeating that add in steady state.
4. The production implementation should not depend on same-torrent reselection or `unrestrict/folder` to recover one target-specific file from the tested cached whole torrent. Neither spike path produced a usable child-file mapping.
