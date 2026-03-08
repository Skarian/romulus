# Unchained Android Research Dump (module commit `3d926637645003b7d64f6bd27db05ba43ff8f812`)

- Module root: `/Users/nskaria/projects/romulus/references/unchained-android`
- Spike specs linked by this dump:
  - `/Users/nskaria/projects/romulus/docs/spikes/specs/spike-1-resolution-enumeration.md`
  - `/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`

## Spike mapping

1. Spike 1 (resolver + enumeration): strongly covered by add/select/info/list/unrestrict client paths in:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentsApi.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/TorrentsRepository.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`
2. Spike 4 (integration): partially covered through resolver -> selected links -> unrestrict -> folder/download UI handoff in:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/viewmodel/TorrentDetailsViewModel.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/folderlist/viewmodel/FolderListViewModel.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/UnrestrictRepository.kt`
3. Spike 4 unarchive/recursive-unarchive/rename/ignore ordering is not implemented in this module (`UNCONFIRMED` for those behaviors in this codebase), based on search in:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained` (no extraction pipeline symbols found).

## Source map (key files/functions)

1. API definitions:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentsApi.kt` (`getAvailableHosts`, `getTorrentInfo`, `addTorrent`, `addMagnet`, `getTorrentsList`, `selectFiles`, `deleteTorrent`)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApi.kt` (`getUnrestrictedLink`, `getUnrestrictedFolder`, `uploadContainer`, `getContainerLinks`)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/AuthenticationApi.kt` (`getToken` also used for refresh)
2. Repository boundary:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/BaseRepository.kt` (`eitherApiResult`, `safeApiCall`, `getToken`)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/TorrentsRepository.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/UnrestrictRepository.kt`
3. Resolver/select orchestration:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt` (`fetchAddedMagnet`, `fetchUploadedTorrent`, `fetchTorrentDetails`, `startSelectionLoop`)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt` (`download_all`, `manual_pick`, file/folder selection handlers)
4. Identity and tree mapping:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/TorrentItem.kt` (`TorrentItem`, `InnerTorrentFile`, `UploadedTorrent`, `AvailableHost`)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/model/TorrentFileStructureAdapter.kt` (`TorrentFileItem`, `getFilesNodes`)
5. Unrestrict and downstream selection:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/viewmodel/TorrentDetailsViewModel.kt` (`downloadTorrent`)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/folderlist/viewmodel/FolderListViewModel.kt` (`retrieveFolderFileList`, `retrieveFiles`)
6. Error/auth/token-expiry behavior:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/start/viewmodel/MainActivityViewModel.kt` (`refreshToken`, `onParseCallFailure`, `programTokenRefresh`, auth FSM transitions)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/base/MainActivity.kt` (observes `RefreshingOpenToken` and triggers refresh)
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/view/NewDownloadFragment.kt`, `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`, `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/lists/view/ListsTabFragment.kt` (API error code `8` handling)

## API surface and signatures used (endpoint/method details)

1. Torrent endpoints in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentsApi.kt`:
   - `GET torrents/availableHosts` -> `Response<List<AvailableHost>>`, header `Authorization`.
   - `GET torrents/info/{id}` -> `Response<TorrentItem>`, header `Authorization`, path `id`.
   - `PUT torrents/addTorrent` -> `Response<UploadedTorrent>`, header `Authorization`, body `RequestBody` (`application/octet-stream`), query `host`.
   - `POST torrents/addMagnet` (form) -> `Response<UploadedTorrent>`, header `Authorization`, fields `magnet`, `host`.
   - `GET torrents` -> `Response<List<TorrentItem>>`, query `offset/page/limit/filter`.
   - `POST torrents/selectFiles/{id}` (form) -> `Response<Unit>`, header `Authorization`, field `files` (`"all"` or comma-separated IDs).
   - `DELETE torrents/delete/{id}` -> `Response<Unit>`.
2. Unrestrict endpoints in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApi.kt`:
   - `POST unrestrict/link` (form) -> `Response<DownloadItem>`, fields `link`, optional `password`, optional `remote`.
   - `POST unrestrict/folder` (form) -> `Response<List<String>>` from `getUnrestrictedFolder`.
   - `PUT unrestrict/containerFile` -> `Response<List<String>>` with binary container body.
   - `POST unrestrict/folder` (form) -> `Response<List<String>>` from `getContainerLinks` (same HTTP path as folder method in code).
3. Auth endpoints used for refresh in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/AuthenticationApi.kt`:
   - `POST token` (form) -> `Response<Token>` via `getToken(client_id, client_secret, code, grant_type)`.
   - Same method is used for token refresh by `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/AuthenticationRepository.kt`.
4. Base URLs in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/Constants.kt`:
   - `BASE_URL = "https://api.real-debrid.com/rest/1.0/"`
   - `BASE_AUTH_URL = "https://api.real-debrid.com/oauth/v2/"`

## End-to-end resolver/select/unrestrict flows

1. Magnet resolver/select flow:
   - Entry accepts magnet and navigates to torrent processing in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/view/NewDownloadFragment.kt`.
   - `fetchAddedMagnet` gets hosts then calls `addMagnet` with `availableHosts.first().host` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`.
   - On upload success, torrent ID is cached in `SavedStateHandle`, then `getTorrentInfo(id)` runs.
   - If torrent is already beyond pre-selection statuses, navigation goes directly to details; otherwise file tree is rendered using `getFilesNodes(selectedOnly = false)` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`.
   - User chooses `download_all` or `manual_pick`; `manual_pick` serializes selected file IDs into comma-separated `files` string.
   - `startSelectionLoop(files)` calls `selectFiles` until accepted, then polls `getTorrentInfo` until status leaves `beforeSelectionStatusList = ["magnet_conversion","waiting_files_selection"]` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/Constants.kt`.
   - On transition out of pre-selection statuses, app navigates to torrent details.
2. Torrent file upload flow:
   - Torrent binary is read and submitted via `addTorrent` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`.
   - Remaining flow is the same as magnet flow (details -> selection -> poll).
3. Torrent -> unrestrict flow:
   - In details screen, when `torrent.links.size == 1`, `downloadTorrent` unrestricts each link sequentially and opens first success in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/viewmodel/TorrentDetailsViewModel.kt`.
   - When `torrent.links.size > 1`, navigation routes to folder list where each link is un-restricted in order in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/folderlist/viewmodel/FolderListViewModel.kt`.
4. Direct host/folder/container unrestrict flow:
   - Link classification in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/viewmodel/NewDownloadViewModel.kt`:
     - folder regex match -> `folderLiveData` path,
     - normal host link -> `getEitherUnrestrictedLink`,
     - container file bytes -> `uploadContainer`,
     - container URL -> `getContainerLinks`.
   - Folder retrieval path:
     - `getEitherFolderLinks(link)` returns list of links,
     - then each link goes through `getEitherUnrestrictedLink`.

## Selection semantics and identity handling

1. Torrent-level identity:
   - Primary torrent key is `TorrentItem.id: String` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/TorrentItem.kt`.
   - `hash` is used for magnet reconstruction (`magnet:?xt=urn:btih:{hash}`) in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/view/TorrentDetailsFragment.kt` and `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`.
2. File-level identity:
   - API file identity is `InnerTorrentFile.id: Int` plus `path` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/TorrentItem.kt`.
   - UI tree node identity uses `TorrentFileItem(id, absolutePath, name)` where folders use `TYPE_FOLDER = -1` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/model/TorrentFileStructureAdapter.kt`.
3. Selection payload generation:
   - Manual pick serializes selected non-folder node IDs with comma separators in BFS traversal order (`Node.traverseBreadthFirst`) in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`.
   - Download-all sends default `"all"` via `startSelectionLoop()` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`.
4. Local selection state:
   - Toggling file/folder selection mutates local tree nodes only; persisted server selection arrives later via refreshed `TorrentItem.files[].selected` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`.
5. Selected file -> link identity:
   - Client never builds explicit mapping from `InnerTorrentFile.id` to `torrent.links` URLs.
   - Downstream unrestrict operates only on `torrent.links` list in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/viewmodel/TorrentDetailsViewModel.kt` and `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/folderlist/viewmodel/FolderListViewModel.kt`.
   - Mapping fidelity is therefore `UNCONFIRMED` in this module.

## Retry/polling/error/token-expiry behavior

1. Polling:
   - Selection gate loop: 1.5s interval in `startSelectionLoop` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`.
   - Torrent details progress poll: 2s interval in `pollTorrentStatus` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/viewmodel/TorrentDetailsViewModel.kt`.
   - Background torrent monitor service: 5s when active, 30s when idle in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/service/ForegroundTorrentService.kt`.
2. Retry behavior:
   - `startSelectionLoop` keeps retrying `selectFiles` with no max-attempt counter; non-`EmptyBodyError` failures are logged and loop continues.
   - `UnrestrictRepository.getUnrestrictedLinkList` is sequential and inserts fixed delay (`callDelay`, default 100ms), but has no retry-on-failure logic in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/UnrestrictRepository.kt`.
   - `fetchTorrentDetails` makes one call and logs null results without retry in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`.
3. Error typing and propagation:
   - HTTP error body is parsed into `APIError(error, error_details, error_code)` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/BaseRepository.kt`.
   - Body-null success responses become `EmptyBodyError(code)` in `eitherApiResult`.
   - Network/call exceptions become `NetworkError`.
   - API conversion failures become `ApiConversionError`.
4. Token expiry handling:
   - API error code `8` is interpreted as bad token in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/Constants.kt` and UI handlers trigger `FSMAuthenticationEvent.OnExpiredOpenToken` in:
     - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/view/NewDownloadFragment.kt`
     - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt`
     - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/lists/view/ListsTabFragment.kt`
   - `MainActivity` reacts to `RefreshingOpenToken` by calling `refreshToken()` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/base/MainActivity.kt`.
   - `refreshToken()` updates stored credentials and schedules pre-expiry refresh with `delay(secondsDelay * 950L)` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/start/viewmodel/MainActivityViewModel.kt`.
5. Request replay after refresh:
   - No automatic replay of the failed resolver/unrestrict call is implemented in these flows (`UNCONFIRMED` for automatic retry-after-refresh behavior).

## Data mapping and assumptions

1. Authorization token format is consistently `"Bearer $token"` at repository/helper boundary:
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/TorrentsRepository.kt`
   - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/UnrestrictRepository.kt`
2. Host choice assumption:
   - `addMagnet` and `addTorrent` always use `availableHosts.first().host` without fallback or ranking in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt` and `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/viewmodel/NewDownloadViewModel.kt`.
3. Status gating assumption:
   - Pre-selection status is hard-coded to `magnet_conversion` and `waiting_files_selection` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/Constants.kt`.
4. Empty-body assumption:
   - `selectFiles` success is explicitly inferred from `EmptyBodyError` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt`.
5. Link mapping assumption:
   - Download/unrestrict stage uses only `torrent.links` and assumes those links represent selected content, but no file-ID-to-link join exists in client code (`UNCONFIRMED` mapping guarantee).
6. Folder detection assumption:
   - Folder link classification depends on regex sets from `HostsRepository` and DB-cached/custom regex patterns:
     - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/HostsRepository.kt`
     - `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/local/HostRegexDao.kt`
7. Container endpoint mapping assumption:
   - `getContainerLinks` and `getUnrestrictedFolder` target the same route `POST unrestrict/folder` in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApi.kt`.

## Gaps/unknowns relative to spike specs

1. Spike 1 question: exact `.zip` path routing behavior.
   - `UNCONFIRMED`: no path-scoped resolver API or `.zip` path routing abstraction exists in this module; flow is torrent file ID selection only.
2. Spike 1 question: selected-file -> returned-link mapping guarantees.
   - `UNCONFIRMED`: client does not persist or verify link/file correspondence; it consumes `torrent.links` as-is.
3. Spike 1 question: subset-selection mismatch patterns.
   - Partially covered: subset IDs are sent (`manual_pick` path), but mismatch detection logic is absent.
4. Spike 1 question: concurrent overlap behavior on same torrent.
   - Partially covered: one selection loop job per `TorrentProcessingViewModel`; cross-screen/process overlap controls are not explicit.
5. Spike 1 question: retry after transient failure.
   - Partially covered: selection loop retries implicitly; no bounded retries/backoff/stage timeout metadata.
6. Spike 4 integration question: identity from source selection through final extracted files.
   - `UNCONFIRMED`: module stops at unrestrict/download links and does not include local extraction pipeline.
7. Spike 4 integration question: deterministic ordering of ignore/selection/fetch/extract/rename.
   - `UNCONFIRMED`: ignore/rename/extract stages are absent.
8. Spike 4 integration question: recursive extraction bounds and cleanup.
   - `UNCONFIRMED`: no recursive extraction implementation in this module.
9. Spike 4 diagnostics requirement (stage-labeled failures).
   - Partially covered: exception classes and toasts exist; structured stage timeline artifacts are absent.

## Reusable patterns for spike app (adopt/adapt/avoid)

1. Adopt:
   - Explicit, small endpoint surface (`add*`, `info`, `selectFiles`, `unrestrict/link`) in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentsApi.kt` and `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApi.kt`.
   - Status-gated progression (`beforeSelectionStatusList`, `loadingStatusList`, `endedStatusList`) in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/Constants.kt`.
   - Typed error model (`APIError`, `NetworkError`, `EmptyBodyError`, `ApiConversionError`) in `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/APIError.kt`.
2. Adapt:
   - Host selection strategy: replace `availableHosts.first()` with deterministic candidate policy and fallback chain.
   - Selection payload generation: preserve stable order and emit trace metadata (selected IDs, status snapshots) around `manual_pick` flow.
   - Unrestrict fan-out: keep sequential option but add configurable concurrency and retry policy around `getUnrestrictedLinkList`.
   - Token refresh FSM: keep explicit state transitions but add request replay hooks for idempotent reads.
3. Avoid:
   - Treating `EmptyBodyError` as success signal in application logic.
   - Assuming `torrent.links` order/contents imply file identity without validation.
   - Infinite loops without timeout budget in selection/status polling.
   - UI-only error signaling for pipeline diagnostics when spike specs require trace artifacts.

## Evidence checklist additions for spike runs

1. Capture raw `selectFiles` payload string plus source selection set (file IDs and order) from the resolver stage.
2. Capture status transition timeline (`magnet_conversion` -> `waiting_files_selection` -> next state) with timestamps and poll intervals.
3. Capture pre-selection and post-selection `TorrentItem.files` snapshots including `{id, path, selected}`.
4. Capture `torrent.links` returned after selection and compare with selected ID set; mark mismatches explicitly.
5. Capture unrestrict request sequence and per-link result (`success/failure`, error code, elapsed time).
6. Capture token-expiry occurrences (`error_code == 8`) and subsequent refresh transitions (`OnExpiredOpenToken` -> `RefreshingOpenToken` -> `OnRefreshed`).
7. Capture whether failed operations are retried automatically or require manual user action.
8. Capture behavior when `availableHosts` is empty and when first host fails.
9. Capture container and folder flows separately because both map to list-of-links then unrestrict-per-link behavior.
10. For Spike 4 runs, explicitly log as `UNSUPPORTED IN REFERENCE MODULE` any unarchive/recursive-archive checks that cannot be evidenced from this source module.

## Appendix: concise file/symbol index with absolute paths

1. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentsApi.kt` - `TorrentsApi`
2. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApi.kt` - `UnrestrictApi`
3. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/AuthenticationApi.kt` - `AuthenticationApi`
4. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentApiHelper.kt` - `TorrentApiHelper`
5. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorrentApiHelperImpl.kt` - `TorrentApiHelperImpl`
6. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApiHelper.kt` - `UnrestrictApiHelper`
7. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/UnrestrictApiHelperImpl.kt` - `UnrestrictApiHelperImpl`
8. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/BaseRepository.kt` - `safeApiCall`, `eitherApiResult`, `getToken`
9. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/TorrentsRepository.kt` - `addMagnet`, `addTorrent`, `getTorrentInfo`, `selectFiles`
10. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/UnrestrictRepository.kt` - `getEitherUnrestrictedLink`, `getUnrestrictedLinkList`, `getEitherFolderLinks`
11. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/AuthenticationRepository.kt` - `refreshTokenWithError`
12. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/HostsRepository.kt` - `getFoldersRegex`, `getHostsRegex`
13. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/TorrentItem.kt` - `TorrentItem`, `InnerTorrentFile`, `UploadedTorrent`
14. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/DownloadItem.kt` - `DownloadItem`
15. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/APIError.kt` - `APIError`, `EmptyBodyError`, `NetworkError`, `ApiConversionError`
16. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/viewmodel/TorrentProcessingViewModel.kt` - `startSelectionLoop`
17. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentfilepicker/view/TorrentProcessingFragment.kt` - `download_all`, `manual_pick`, selection handlers
18. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/model/TorrentFileStructureAdapter.kt` - `getFilesNodes`, `TorrentFileItem`
19. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/torrentdetails/viewmodel/TorrentDetailsViewModel.kt` - `pollTorrentStatus`, `downloadTorrent`
20. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/folderlist/viewmodel/FolderListViewModel.kt` - `retrieveFolderFileList`, `retrieveFiles`
21. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/viewmodel/NewDownloadViewModel.kt` - `fetchUnrestrictedLink`, `fetchUploadedTorrent`
22. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/newdownload/view/NewDownloadFragment.kt` - input classification and code-8 handling
23. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/lists/viewmodel/ListTabsViewModel.kt` - `unrestrictTorrent`
24. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/lists/view/ListsTabFragment.kt` - token-expiry handling and torrent/download routing
25. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/start/viewmodel/MainActivityViewModel.kt` - auth FSM, token refresh scheduling
26. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/base/MainActivity.kt` - `RefreshingOpenToken` observer path
27. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/Constants.kt` - statuses, base URLs, error-code mapping
28. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/statemachine/authentication/FSMAuthentication.kt` - auth states/events
29. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/java/com/github/livingwithhippos/unchained/data/model/EmptyBodyInterceptor.kt` - empty-body HTTP normalization
30. `/Users/nskaria/projects/romulus/references/unchained-android/app/app/src/main/proto/credentials.proto` - credential storage schema
