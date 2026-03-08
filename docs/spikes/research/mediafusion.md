# MediaFusion Research Dump (commit `2c415b8aa03ea25017cc896b0d10023a05c49a1e`)

Module path: `/Users/nskaria/projects/romulus/references/MediaFusion`

## Spike mapping

Target specs:
- `/Users/nskaria/projects/romulus/docs/spikes/specs/spike-1-resolution-enumeration.md`
- `/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`

Spike 1 (resolver/enumeration) mapping:
- Endpoint sequence and state transitions: implemented in Real-Debrid provider flow (`get_available_torrent` -> add/reuse -> status poll -> `selectFiles` -> `unrestrict/link`) in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py` and `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/client.py`.
- File identity + selection mapping: implemented by `select_file_index_from_torrent` and provider-specific link mapping in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/parser.py` and `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`.
- Reuse, retries, and provider error mapping: implemented in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/debrid_client.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/client.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`, validated in `/Users/nskaria/projects/romulus/references/MediaFusion/tests/test_provider_error_handling.py`.

Spike 4 (integration) mapping:
- Cross-boundary flow from stream listing to playback redirect: `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/stremio/stream.py` -> `/Users/nskaria/projects/romulus/references/MediaFusion/db/crud/stream_services.py` -> `/Users/nskaria/projects/romulus/references/MediaFusion/utils/parser.py` -> `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`.
- Cache and lock integration: `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/cache_helpers.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/utils/lock.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`.
- Identity propagation across DB models and route params: `/Users/nskaria/projects/romulus/references/MediaFusion/db/models/streams.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/media.py`.

## Source map (key files/functions)

- Router wiring:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/api/app.py` (`app.include_router(..., prefix="/streaming_provider")`)
  - `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/__init__.py` (`get_router`, `get_provider_router`)
- Stream listing entrypoint:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/stremio/stream.py` (`get_streams`)
- Stream list assembly + provider fanout:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/db/crud/stream_services.py` (`get_movie_streams`, `get_series_streams`)
  - `/Users/nskaria/projects/romulus/references/MediaFusion/utils/parser.py` (`filter_and_sort_streams`, `parse_stream_data`, `_build_stream_entries`)
- Playback resolution:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py` (`streaming_provider_endpoint`, `get_or_create_video_url`, `fetch_stream_or_404`, `cache_stream_url`)
- Provider dispatch map:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/mapper.py` (`GET_VIDEO_URL_FUNCTIONS`, `CACHE_UPDATE_FUNCTIONS`, `DELETE_*`, `USENET_*`)
- Real-Debrid transport + lifecycle:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/debrid_client.py` (`_make_request`, `_check_response_status`, `wait_for_status`)
  - `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/client.py` (REST/OAuth endpoints)
  - `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py` (`get_video_url_from_realdebrid`, `create_download_link`, `add_new_torrent`)
- File selection and metadata updates:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/parser.py` (`select_file_index_from_torrent`, `update_torrent_streams_metadata`)
- Cache API and cache sync client:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/cache.py`
  - `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/cache_helpers.py`
- Identity models:
  - `/Users/nskaria/projects/romulus/references/MediaFusion/db/models/streams.py`
  - `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/media.py`
  - `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/config.py`

## API surface and signatures used

MediaFusion HTTP endpoints (FastAPI):
- Stream listing:
  - `GET /{secret_str}/stream/{catalog_type}/{video_id}.json`
  - `GET /stream/{catalog_type}/{video_id}.json`
  - `GET /{secret_str}/stream/{catalog_type}/{video_id}:{season}:{episode}.json`
  - `GET /stream/{catalog_type}/{video_id}:{season}:{episode}.json`
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/stremio/stream.py`.
- Torrent playback:
  - `GET|HEAD /{secret_str}/playback/{provider_name}/{info_hash}`
  - `GET|HEAD /{secret_str}/playback/{provider_name}/{info_hash}/{filename}`
  - `GET|HEAD /{secret_str}/playback/{provider_name}/{info_hash}/{season}/{episode}`
  - `GET|HEAD /{secret_str}/playback/{provider_name}/{info_hash}/{season}/{episode}/{filename}`
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`.
- Usenet playback (parallel structure):
  - `GET|HEAD /{secret_str}/usenet/{provider_name}/{nzb_guid}[/{filename}|/{season}/{episode}[/{filename}]]`
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`.
- Cache endpoints:
  - `POST /streaming_provider/cache/status` with `CacheStatusRequest { service, info_hashes[] }` -> `CacheStatusResponse { cached_status: {hash: bool} }`
  - `POST /streaming_provider/cache/submit` with `CacheSubmitRequest { service, info_hashes[] }` -> `CacheSubmitResponse { success, message }`
  - Schemas in `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/cache.py`; routes in `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/cache.py`.
- Real-Debrid auth proxy:
  - `GET /streaming_provider/realdebrid/get-device-code`
  - `POST /streaming_provider/realdebrid/authorize` with `AuthorizeData.device_code`
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/api.py`.

Real-Debrid upstream REST/OAuth calls used by module:
- OAuth/device:
  - `GET https://api.real-debrid.com/oauth/v2/device/code` (`client_id`, `new_credentials=yes`)
  - `GET https://api.real-debrid.com/oauth/v2/device/credentials` (`client_id`, `code`)
  - `POST https://api.real-debrid.com/oauth/v2/token` (`client_id`, `client_secret`, `code`, `grant_type=http://oauth.net/grant_type/device/1.0`)
- Torrent/download lifecycle:
  - `POST https://api.real-debrid.com/rest/1.0/torrents/addMagnet` (`magnet`; adds `ip` when available)
  - `PUT https://api.real-debrid.com/rest/1.0/torrents/addTorrent` (binary torrent file; adds `ip` when available)
  - `GET https://api.real-debrid.com/rest/1.0/torrents/activeCount`
  - `GET https://api.real-debrid.com/rest/1.0/torrents` (`page`, `limit` optional)
  - `GET https://api.real-debrid.com/rest/1.0/torrents/info/{torrent_id}`
  - `POST https://api.real-debrid.com/rest/1.0/torrents/selectFiles/{torrent_id}` (`files=all` or one file id)
  - `POST https://api.real-debrid.com/rest/1.0/unrestrict/link` (`link`)
  - `DELETE https://api.real-debrid.com/rest/1.0/torrents/delete/{torrent_id}`
  - `GET https://api.real-debrid.com/rest/1.0/user`
  - `GET https://api.real-debrid.com/rest/1.0/disable_access_token`
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/client.py`.

## End-to-end flow(s) this module implements

Flow A: Stream enumeration to provider-specific playback URLs.
1. Stremio calls stream endpoint in `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/stremio/stream.py`.
2. Route delegates to `crud.get_movie_streams` / `crud.get_series_streams` in `/Users/nskaria/projects/romulus/references/MediaFusion/db/crud/stream_services.py`.
3. `parse_stream_data` in `/Users/nskaria/projects/romulus/references/MediaFusion/utils/parser.py`:
   - filters/sorts,
   - updates cached flags,
   - fans out over active providers,
   - emits playback URLs like `/streaming_provider/{secret_str}/playback/{provider_name}/{info_hash}[...]?stremio=1`.

Flow B: Playback request to resolved redirect URL.
1. Playback route in `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py` lowercases `info_hash`.
2. Resolves provider by `provider_name` then fallback to primary provider via `UserData.get_provider_by_name` / `get_primary_provider` in `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/config.py`.
3. Computes cache key from user IP + secret + info_hash + season + episode via `generate_cache_key`.
4. If cached URL exists, returns redirect immediately.
5. If not cached, acquires Redis lock (`acquire_redis_lock`) and resolves URL via provider function from `mapper.GET_VIDEO_URL_FUNCTIONS`.
6. Caches result URL + filename, stores info_hash in debrid cache, applies MediaFlow proxy if configured, then redirects.

Flow C: Real-Debrid resolver lifecycle.
1. `get_video_url_from_realdebrid` (`/streaming_providers/realdebrid/utils.py`) checks account torrent reuse by hash (`get_available_torrent`).
2. If missing, `add_new_torrent`:
   - checks active count limit,
   - adds magnet/file,
   - fetches `torrents/info/{id}` with unknown-resource retries and optional re-add.
3. If status is invalid (`magnet_error`, `error`, `virus`, `dead`), delete + fail.
4. If not already in `queued/downloading/downloaded`, waits for `waiting_files_selection`, submits `selectFiles` with `files=all`.
5. Waits for `downloaded`, then runs `create_download_link`:
   - chooses file index,
   - validates selected-files to links mapping,
   - on mismatch deletes + re-adds + selects only chosen file id,
   - calls `unrestrict/link`, validates MIME starts with `video`, returns final `download` URL.

Flow D: Cache check and cache sync.
1. `filter_and_sort_streams` in `/Users/nskaria/projects/romulus/references/MediaFusion/utils/parser.py` gets hash cache from Redis via `get_cached_status`.
2. For uncached hashes, calls provider cache updater once per provider+media marker TTL (`is_cache_check_done` / `mark_cache_check_done`).
3. Cached hashes are stored in Redis hash with expiry timestamp and optionally submitted to central MediaFusion via `/streaming_provider/cache/submit`.
4. Cache miss fallback to central MediaFusion via `/streaming_provider/cache/status` if sync enabled.

## Selection/link mapping semantics and invariants

- Selection entrypoint is `select_file_index_from_torrent` in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/parser.py`.
- Selection order is deterministic in code:
  1. exact basename filename equality if provided,
  2. season/episode match,
  3. largest video file fallback.
- Video classification uses extension checks (`is_video_file`) in `/Users/nskaria/projects/romulus/references/MediaFusion/utils/validation_helper.py`; archives like `.zip` are not treated as playable video.
- For trustable filename/index paths (`is_filename_trustable=True`), metadata is persisted and streams can be blocked if no video files found (`update_torrent_streams_metadata`, `_save_torrent_stream`).
- Real-Debrid mapping invariant in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`:
  - `relevant_file.selected` must be `1`, and
  - `len(selected_files)` must equal `len(torrent_info.links)`.
  - If violated, code deletes torrent and re-adds to rebuild mapping using single-file selection.
- Real-Debrid link index semantics:
  - normal path: link index = `selected_files.index(relevant_file)`.
  - mismatch-recovery path: link index forced to `0` after selecting exactly one file.
- DB linking invariants:
  - torrent identity unique by `TorrentStream.info_hash`.
  - per-stream file position unique by `(stream_id, file_index)` in `StreamFile`.
  - file-to-media link unique by `(file_id, media_id, season_number, episode_number)` in `FileMediaLink`.
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/db/models/streams.py`.

## Retry/rate-limit/error handling behaviors

HTTP + provider transport:
- `DebridClient._make_request` retries exactly once on `aiohttp.ClientConnectorError`.
- `DebridClient._check_response_status` maps:
  - `401 -> invalid_token.mp4`,
  - `429 -> too_many_requests.mp4`,
  - `502/503/504 -> debrid_service_down_error.mp4`.
- `DebridClient.wait_for_status` polls up to `max_retries`, sleeps `retry_interval` between attempts, fails on `status == error`.
- Source: `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/debrid_client.py`.

Real-Debrid specific:
- Service error-code mapping in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/client.py`:
  - `9 invalid token`, `22 IP not allowed`, `34 too many requests`, `21 torrent limit`, `30 invalid magnet`, etc.
- Unknown-resource retry branch in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`:
  - per creation: up to 3 `get_torrent_info` attempts,
  - creation attempts: up to 2,
  - sleeps `0.5, 1.0` seconds on retries plus `0.5` before re-add.
- Verified by tests in `/Users/nskaria/projects/romulus/references/MediaFusion/tests/test_provider_error_handling.py`.

Route-level behavior:
- Playback and cache endpoints are explicitly excluded from API rate limiting via `@wrappers.exclude_rate_limit`.
- Stremio stream listing endpoint has `@wrappers.rate_limit(20, 60*60, "stream")`.
- Source: `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/cache.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/stremio/stream.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/utils/wrappers.py`.

Observed fallback/suppression patterns:
- Multiple cache updater functions swallow `ProviderException` and return empty/no-op states (example: `update_rd_cache_status`, `fetch_downloaded_info_hashes_from_rd`).
- `get_or_create_video_url` currently passes `max_retries=1`, `retry_interval=0` to provider resolvers from playback route.
- Source: `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`.

## Data identity and matching rules

- Provider/account identity:
  - Active provider selection sorted by priority (`UserData.get_active_providers`).
  - Named provider lookup supports per-provider names and shorthand aliases (`rd`, `ad`, `pm`, etc.).
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/config.py`.
- Torrent identity:
  - Canonical key is lowercase `info_hash`.
  - Playback route lowercases route param before DB lookup.
  - DB uniqueness enforced at `TorrentStream.info_hash`.
- Stream/file identity:
  - Stream files carry `file_index`, `filename`, `size`, optional episode metadata.
  - `get_main_file` and `get_episode_files` are used for route filename fallback and series mapping.
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/media.py`.
- Cache identity:
  - URL cache key: hash of `{user_ip}_{secret_str}_{info_hash}_{season}_{episode}`.
  - Cache-check marker key is global or per-user depending on provider class (`GLOBAL_CACHE_CHECK_PROVIDERS`).
  - User hash key material is token or email+password hash prefix.
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`, `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/cache_helpers.py`.
- Media linking identity:
  - `FileMediaLink` models episode-level mapping from file to media.
  - `StreamFile` includes archive metadata fields (`is_archive`, `archive_contents`) but this resolver path does not execute extraction.
  - Source: `/Users/nskaria/projects/romulus/references/MediaFusion/db/models/streams.py`.

## Gaps/unknowns relative to spike specs

Spike 1 gaps (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-1-resolution-enumeration.md`):
- Root/directory/exact `.zip` path routing semantics are not implemented in this playback path. File selection is filename/episode/largest-video based, not path-scope based. `.zip` appears in non-video blocklist.
- Subset selection in resolver is effectively single-target playback selection (one chosen file), not an exposed general selected-ID subset API for callers.
- Cross-run file identity stability (same file id/order across re-add and polling) is UNCONFIRMED; code assumes stable indexing when it reuses `selected_file_index` after re-add.
- Explicit trace artifact emission (request/response timeline, mapping records) is not built into module; only logs/exceptions and redirect behavior are emitted.

Spike 4 gaps (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`):
- No download-to-local-file pipeline in this module for torrent playback; it resolves and redirects to remote provider URLs.
- No archive extraction runtime (`unarchive`, `recursiveUnarchive`) in this playback path.
- No ignore-glob/rename policy pipeline in playback resolver path.
- No built-in stage manifest showing before/after extraction output; stage diagnostics are primarily exception video mapping and logs.
- Recursive extraction bounds/cleanup behavior are UNCONFIRMED for this module because extraction runtime is not implemented here.

## Reusable patterns for spike app (adopt/adapt/avoid)

Adopt:
- Provider-agnostic function map dispatch (`GET_VIDEO_URL_FUNCTIONS`, `CACHE_UPDATE_FUNCTIONS`) from `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/mapper.py`.
- Redis lock around expensive resolve path keyed by request identity from `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py` and `/Users/nskaria/projects/romulus/references/MediaFusion/utils/lock.py`.
- Explicit mismatch-recovery branch before final link usage in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`.
- Pagination safety and duplicate guard in `_get_all_torrents` (`seen_ids`, hard page caps) in `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`.

Adapt:
- File selection strategy from `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/parser.py` should be adapted to include path-scope and archive-entry scope required by Spike 1/4.
- Cache-check marker TTL pattern (`debrid_checked:*`) from `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/cache_helpers.py` can be adapted for expensive resolver probes in spikes.
- Error taxonomy pattern (`ProviderException.message` + `video_file_name`) should be adapted to structured machine-readable stage codes for spike evidence outputs.

Avoid:
- Silent exception suppression in cache-updater paths (`except ProviderException: pass/return []`) because it obscures spike evidence quality.
- Tight provider retry override (`max_retries=1`, `retry_interval=0`) in playback route if spike runs need robust transient-failure characterization.
- Implicit assumption that selected file index remains valid across delete/re-add without post-readd re-resolution (currently UNCONFIRMED invariant in source).

## Evidence checklist additions for spike runs

Add these checks to spike output evidence collection:
- Capture full resolver state progression (`initial status`, `waiting_files_selection`, `downloaded`) and exact retry counters.
- Capture per-run file identity snapshots including `file_index`, provider file id, path/name, selected flag before and after re-add branches.
- Capture selected-files count vs links count and whether mismatch recovery branch executed.
- Capture provider API error-class mapping observed (`status code`, provider-specific code, mapped failure class).
- Capture cache behavior per media/provider key (`cache hit`, provider check skipped by marker, marker TTL path used).
- Capture lock behavior under concurrent requests for same and different key tuples (`user_ip`, `secret`, `info_hash`, `season`, `episode`).
- Explicitly record whether path-scoped (`root`, `directory`, exact `.zip`) requests are representable; mark `UNCONFIRMED` where unavailable.
- For Spike 4, explicitly record absence/presence of extraction policy stages; this module currently provides resolver redirect only.

## Appendix: concise file/symbol index with absolute paths

- `/Users/nskaria/projects/romulus/references/MediaFusion/api/app.py`: router registration for `/streaming_provider`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/__init__.py`: `get_router`, `get_provider_router`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/stremio/stream.py`: `get_streams`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/db/crud/stream_services.py`: `get_movie_streams`, `get_series_streams`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/utils/parser.py`: `filter_and_sort_streams`, `parse_stream_data`, `_build_stream_entries`, non-video blocklist.
- `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/playback.py`: `streaming_provider_endpoint`, `usenet_playback_endpoint`, `get_or_create_video_url`, `cache_stream_url`, `delete_all_watchlist`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/api/routers/streaming/cache.py`: `check_cache_status`, `submit_cached_hashes`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/cache.py`: `CacheStatusRequest`, `CacheSubmitRequest`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/config.py`: `StreamingProvider`, `UserData.get_active_providers`, `UserData.get_provider_by_name`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/db/schemas/media.py`: `StreamFileData`, `TorrentStreamData`, `get_main_file`, `get_episode_files`.
- `/Users/nskaria/projects/romulus/references/MediaFusion/db/models/streams.py`: `TorrentStream`, `StreamFile`, `FileMediaLink` constraints.
- `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/mapper.py`: provider function maps.
- `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/debrid_client.py`: common request/retry/status polling behavior.
- `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/client.py`: Real-Debrid upstream endpoint methods.
- `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/utils.py`: resolver lifecycle, selection-link mapping checks, cache/detail helpers.
- `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/realdebrid/api.py`: provider auth routes.
- `/Users/nskaria/projects/romulus/references/MediaFusion/streaming_providers/cache_helpers.py`: Redis hash cache, marker TTL, MediaFusion cache sync client.
- `/Users/nskaria/projects/romulus/references/MediaFusion/utils/network.py`: `get_user_public_ip` MediaFlow-aware IP logic.
- `/Users/nskaria/projects/romulus/references/MediaFusion/utils/lock.py`: Redis lock acquire/release helpers.
- `/Users/nskaria/projects/romulus/references/MediaFusion/tests/test_provider_error_handling.py`: retry/error mapping tests for debrid providers.
- `/Users/nskaria/projects/romulus/references/MediaFusion/tests/test_playback_cache_helpers.py`: playback cache pipeline fallback tests.
