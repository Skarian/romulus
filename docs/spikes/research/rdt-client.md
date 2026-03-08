# RDT-Client Research Dump

Module commit SHA: `b7090eeb3a9696ff0cab5e48d3194259648ff0c0` (`/Users/nskaria/projects/romulus/references/rdt-client`)

## Spike mapping

- Spike 1 (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-1-resolution-enumeration.md`): provider add/poll/select/download-link creation, file identity fields, subset selection behavior, rate-limit/retry handling are implemented across `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, and provider clients under `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients`.
- Spike 2 (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-2-unarchive-runtime.md`): host-side unpack lifecycle, supported archive types, queueing, completion/error timestamps, and cleanup behavior are in `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`, and `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/FileHelper.cs`.
- Spike 4 (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`): cross-stage flow from enqueue to provider completion, link unrestrict, host download, optional unpack, and finished-action deletion is split across `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/BackgroundServices/TaskRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/BackgroundServices/ProviderUpdater.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, and `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`.

## Source map (key files/functions)

- Core orchestrator service:
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`
  - Key methods: `AddMagnetToDebridQueue`, `DequeueFromDebridQueue`, `SelectFiles`, `CreateDownloads`, `UnrestrictLink`, `RetrieveFileName`, `UpdateRdData`, `RetryTorrent`, `RetryDownload`, `Delete`.
- Tick runner / lifecycle state machine:
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`
  - Key methods: `Initialize`, `Tick`, `SetRateLimit`; static state: `ActiveDownloadClients`, `ActiveUnpackClients`, `NextDequeueTime`.
- Provider abstraction:
  - Interface: `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/IDebridClient.cs`
  - Implementations:
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/AllDebridDebridClient.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/PremiumizeDebridClient.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/DebridLinkTorrentClient.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/TorBoxDebridClient.cs`
- Host download executor:
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DownloadClient.cs`
  - Downloaders:
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Downloaders/BezzadDownloader.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Downloaders/Aria2cDownloader.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Downloaders/DownloadStationDownloader.cs`
    - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Downloaders/SymlinkDownloader.cs`
- Unpack executor:
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`
- Identity and path mapping:
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Torrent.cs`
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Download.cs`
- Persistence transitions:
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/TorrentData.cs`
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`
- API edges (add/check/retry/delete/update):
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/TorrentsController.cs`
  - `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/QBittorrentController.cs`
- Client payload shaping (manual-file selection string):
  - `/Users/nskaria/projects/romulus/references/rdt-client/client/src/app/add-new-torrent/add-new-torrent.component.ts`
  - `/Users/nskaria/projects/romulus/references/rdt-client/client/src/app/torrent.service.ts`

## API/provider abstraction surface and signatures

### `IDebridClient` contract

Source: `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/IDebridClient.cs`

- `Task<IList<DebridClientTorrent>> GetDownloads()`
- `Task<DebridClientUser> GetUser()`
- `Task<String> AddTorrentMagnet(String magnetLink)`
- `Task<String> AddTorrentFile(Byte[] bytes)`
- `Task<String> AddNzbLink(String nzbLink)`
- `Task<String> AddNzbFile(Byte[] bytes, String? name)`
- `Task<IList<DebridClientAvailableFile>> GetAvailableFiles(String hash)`
- `Task<Int32?> SelectFiles(Torrent torrent)`
- `Task Delete(Torrent torrent)`
- `Task<String> Unrestrict(Torrent torrent, String link)`
- `Task<Torrent> UpdateData(Torrent torrent, DebridClientTorrent? torrentClientTorrent)`
- `Task<IList<DownloadInfo>?> GetDownloadInfos(Torrent torrent)`
- `Task<String> GetFileName(Download download)`

### Provider behavior at this SHA

- RealDebrid (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`):
  - `GetAvailableFiles` returns empty list.
  - `SelectFiles` sends provider file IDs (`torrent.Files[].Id`) to `Torrents.SelectFilesAsync`.
  - `GetDownloadInfos` returns `DownloadInfo` with `RestrictedLink` from provider `Links`; `FileName` is always `null` here.
  - `Unrestrict` calls provider unrestrict endpoint and requires non-null returned download URL.
- AllDebrid (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/AllDebridDebridClient.cs`):
  - `SelectFiles` is a count passthrough (`torrent.Files.Count`), no provider-side selection API call.
  - `GetDownloadInfos` recursively enumerates files/folders and applies `IDownloadableFileFilter`.
- Premiumize (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/PremiumizeDebridClient.cs`):
  - `SelectFiles` returns `1` (comment: torrent files not populated at select time).
  - `GetDownloadInfos` recursively walks folders and includes file names directly.
- DebridLink (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/DebridLinkTorrentClient.cs`):
  - `SelectFiles` count passthrough.
  - `GetDownloadInfos` uses provider file list `DownloadLink` per file.
- TorBox (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/TorBoxDebridClient.cs`):
  - `GetAvailableFiles` implemented for torrent and usenet availability APIs.
  - `GetDownloadInfos` generates synthetic `https://torbox.app/fakedl/{id}/{fileIdOrZip}` restricted links.
  - `Unrestrict` parses that synthetic path into `RequestDownloadAsync` parameters.

### Service/API surface above providers

- Core orchestration methods (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`):
  - `Add*ToDebridQueue`, `DequeueFromDebridQueue`, `SelectFiles`, `CreateDownloads`, `UnrestrictLink`, `RetrieveFileName`, `RetryTorrent`, `RetryDownload`.
- HTTP endpoints (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/TorrentsController.cs`):
  - Add: `UploadMagnet`, `UploadFile`, `UploadNzbLink`, `UploadNzbFile`
  - Availability checks: `CheckFiles`, `CheckFilesMagnet`, `VerifyRegex`
  - Operations: `Delete`, `Retry`, `RetryDownload`, `Update`
- qB-compatible API edge (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/QBittorrentController.cs`):
  - `torrents/add`, `torrents/files`, `torrents/delete`, etc.
  - `torrents/filePrio` currently returns `Ok()` without applying file priority.

## End-to-end flows (add/select/wait/unrestrict/download/unpack)

### 1) Add and queue

- UI/API submits torrent options and source (`/Users/nskaria/projects/romulus/references/rdt-client/client/src/app/torrent.service.ts`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/TorrentsController.cs`).
- `Torrents.AddMagnetToDebridQueue` or `AddFileToDebridQueue` parses source, applies banned-tracker checks, sets `RdStatus=Queued`, sets name/hash, and persists queued record (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`).

### 2) Dequeue to provider

- `TorrentRunner.Tick` collects torrents with `RdId == null`, `RdStatus == Queued`, and `FileOrMagnet != null`.
- It calls `Torrents.DequeueFromDebridQueue`, which is serialized by `RealDebridUpdateLock` and invokes provider `AddTorrentMagnet/AddTorrentFile/...` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`).

### 3) Poll and state refresh

- `ProviderUpdater` periodically calls `Torrents.UpdateRdData`, which fetches provider torrent list and merges into stored torrents (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/BackgroundServices/ProviderUpdater.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`).
- Each provider maps provider statuses into `TorrentStatus` and updates `RdFiles`, `RdProgress`, `RdEnded`, etc. (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/*.cs`).

### 4) Select files

- In `TorrentRunner.Tick`, selection runs when:
  - `RdStatus` is `WaitingForFileSelection` or `Finished`
  - `FilesSelected == null`
  - `Downloads.Count == 0`
- Then `Torrents.SelectFiles` calls `IDebridClient.SelectFiles`; afterwards `FilesSelected` timestamp is written (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`).

### 5) Wait for links and create download rows

- When `RdStatus == Finished` and `Downloads.Count == 0` and `FilesSelected != null`, `Torrents.CreateDownloads` calls provider `GetDownloadInfos`.
- For each `DownloadInfo`, dedupe key is `RestrictedLink` (`downloads.Get(torrentId, restrictedLink)`), then `downloads.Add` creates row with `Path=RestrictedLink`, `DownloadQueued=now`, `Link=null` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`).

### 6) Unrestrict before host download

- For queued downloads (`DownloadQueued != null`, `DownloadStarted == null`), runner calls `Torrents.UnrestrictLink` if `download.Link == null`.
- `UnrestrictLink` passes `download.Path` (restricted URL) to provider `Unrestrict` and stores unrestricted URL in `Download.Link`.
- If `FileName` is missing, runner calls `Torrents.RetrieveFileName` (`GetFileName`) and stores it (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`).

### 7) Host download execution

- Runner marks `DownloadStarted`, computes category-aware destination path, instantiates `DownloadClient`, and starts downloader (`Bezzad`, `Aria2c`, `DownloadStation`, `Symlink`) (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DownloadClient.cs`).
- On download completion:
  - Success: `DownloadFinished=now`, `UnpackingQueued=now`.
  - Failure: increment per-download retry until limit; else set `Error` + `Completed`.

### 8) Unpack + complete

- Runner dequeues unpack work where `UnpackingQueued != null`, `UnpackingStarted == null`, `Completed == null`, `Error == null`.
- If extension is not `.rar`/`.zip`, or downloader is `Symlink`, or `UnpackLimit == 0`, it marks unpack started+finished and download completed immediately.
- Otherwise it starts `UnpackClient`; when finished, runner sets `UnpackingFinished` (or `Error`) and always sets `Completed` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`).
- Torrent completion is based on all download rows `Completed != null`, then `Torrent.Completed` is set and finished-action deletion policy may run later (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).

## Selection semantics and identity mapping rules

- Manual selection payload shape:
  - Angular UI builds `torrent.downloadManualFiles` as comma-joined selected file names from availability results (`/Users/nskaria/projects/romulus/references/rdt-client/client/src/app/add-new-torrent/add-new-torrent.component.ts`).
- `Torrent.ManualFiles` parsing:
  - `DownloadManualFiles.Split(",")` with no trimming (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Torrent.cs`).
- RealDebrid manual matching:
  - `files = torrent.Files.Where(m => torrent.ManualFiles.Any(f => m.Path.EndsWith(f)))` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`).
  - This is suffix matching against provider file path strings, then provider file IDs (`m.Id`) are submitted.
- Filtering before provider selection:
  - `IDownloadableFileFilter` applies min-size/include/exclude; for RealDebrid + manual mode, min-size is bypassed (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DownloadableFileFilter.cs`).
- Link creation mapping:
  - RealDebrid `GetDownloadInfos` validates by count (`selected-file-count == link-count` or `manual-file-count == link-count`), not by file ID/path map (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`).
- Download row identity key:
  - `Download.Path` stores restricted link and is dedupe key (`downloads.Get(torrentId, path)`) (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`).
- Host path mapping rule:
  - `DownloadHelper` maps file path by filename against `torrent.Files.Where(m => m.Path.EndsWith(fileName))` and chooses first match (`matchingTorrentFiles[0]`) (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`).

## Retry/rate-limit/error behavior

### Rate limit + throttling

- Provider add methods convert `slow_down`/`rate limit exceeded` patterns into `RateLimitException` with `RetryAfter=2m` (`RealDebridDebridClient`, `AllDebridDebridClient`, `PremiumizeDebridClient`, `DebridLinkClient`, `TorBoxDebridClient` under `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients`).
- HTTP client resilience for named clients (`RdClient`, `TorBoxClient`): timeout + 429 retry + jitter + `Retry-After` handling (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/DiConfig.cs`).
- `RateLimitHandler` throws `RateLimitException` on HTTP 429 and timeout cases (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/RateLimitHandler.cs`).
- Queue pause mechanics:
  - `TorrentRunner.SetRateLimit` sets `NextDequeueTime` and broadcasts via websocket (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/RemoteService.cs`).

### Download and torrent retries

- Per-download retry:
  - Runner retries by resetting download row while `RetryCount < DownloadRetryAttempts`; otherwise sets terminal download error/completion (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`).
- Per-torrent retry:
  - `TorrentData.UpdateComplete(..., retry=true)` increments torrent retry counters when there is error and retry budget remains.
  - Runner processes torrents with `Retry != null` by calling `Torrents.RetryTorrent` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/TorrentData.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).
- Full retry flow (`RetryTorrent`): cancel active download/unpack clients, delete torrent+downloads+provider entry+local files (based on flags), re-add source, preserve requested retry count (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`).

### Error propagation

- Debrid/provider errors:
  - If `RdStatus == Error`, runner marks torrent complete with `Debrid error: {RdStatusRaw}.` and enables retry handling (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).
- Unrestrict failure:
  - download row gets `Error` + `Completed`, and runner returns from current tick (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).
- Unpack failure:
  - `UnpackClient.Error` is persisted on download, then `Completed` is set (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).

## Unarchive runtime behavior and lifecycle transitions

- Lifecycle timestamps on `Download`:
  - `DownloadQueued -> DownloadStarted -> DownloadFinished -> UnpackingQueued -> UnpackingStarted -> UnpackingFinished -> Completed`
  - model in `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Download.cs` and persistence in `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`.
- Queue/worker model:
  - active unpack workers tracked in `TorrentRunner.ActiveUnpackClients` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).
- Supported unpack formats in this path:
  - Runner only attempts unpack when extension is `.rar` or `.zip` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).
  - `UnpackClient` opens either `ZipArchive` or `RarArchive` (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`).
- Archive deletion behavior:
  - On successful extract path in `UnpackClient`, original archive file is deleted via `FileHelper.Delete(filePath)`.
  - If exception occurs before deletion call, archive is retained (no catch-time cleanup call).
- Multipart behavior in code:
  - If archive entry list contains `.r00`, it extracts to temp dir, then scans for `*.r00`, resolves paired `.rar`, and extracts paired `.rar` into final destination (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`).
  - Exact part resolution internals for `ArchiveFactory.GetFileParts(filePath)` are `UNCONFIRMED` because `ArchiveFactory` implementation is outside this repo (called from `UnpackClient`).
- TorBox-specific post-unpack move:
  - `TorBoxDebridClient.MoveHashDirContents` may move extracted files from hash dir structure into expected location (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/TorBoxDebridClient.cs`).
- Non-unpack path:
  - For non-archive files, symlink downloader, or unpack disabled (`UnpackLimit==0`), unpack timestamps are marked complete without extraction (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).

## Gaps/unknowns relative to spike specs

### Spike 1 gaps

- Path-scoped entry variants (root/directory/exact `.zip`) are not represented in this code path. APIs accept magnet/torrent/nzb, not a `path` selector for provider-file scoping (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/TorrentsController.cs`).
- RealDebrid selection/link mapping is count-based, not explicit file-id-to-link mapping; mismatch diagnosis for duplicate filenames is not explicit in-provider (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`).
- `GetAvailableFiles` for RealDebrid/AllDebrid/Premiumize/DebridLink returns empty list, so `CheckFiles*` pre-add enumeration only has implemented provider support in TorBox path (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/*.cs`).
- Race/overlap controls are partial: provider update/add serialized by `RealDebridUpdateLock`, but download/unpack loops are cooperative dictionaries and per-tick checks; no explicit cross-process lock evidence in repo (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).

### Spike 2 gaps

- No `.7z` unpack path in this runtime (`UnpackClient` only zip/rar).
- No recursive unpack pass over extracted outputs; unpack queue is tied to original download rows only (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`).
- Path traversal hardening behavior inside archive extraction is `UNCONFIRMED` at repo level (relies on SharpCompress internals, not explicit checks in this code).
- Encrypted-archive behavior is `UNCONFIRMED` in this repo path (no explicit password/decryption handling code in `UnpackClient`).

### Spike 4 gaps

- End-to-end identity proof from provider file IDs to final host files is incomplete: provider ID is used at select time, but later download records are keyed by restricted link and final filesystem placement is filename/suffix based (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`).
- Deterministic policy ordering for ignore/select/fetch/extract/rename is partly present (filter then select then create-downloads then host download then unpack), but there is no rename stage in this module.
- Diagnostics labels exist in logs and error strings, but structured per-stage telemetry schema is `UNCONFIRMED` (websocket update contains state snapshot, not stage event log) (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/RemoteService.cs`).

## Reusable patterns for spike app (adopt/adapt/avoid)

### Adopt

- Stage timestamp model per download (`DownloadQueued/Started/Finished`, `UnpackingQueued/Started/Finished`, `Completed`) from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Download.cs`.
- Explicit active-worker registries (`ActiveDownloadClients`, `ActiveUnpackClients`) from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`.
- Central provider interface (`IDebridClient`) with one orchestrator consuming it (`Torrents`) from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/IDebridClient.cs` and `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`.
- Explicit rate-limit state surfacing to UI (`RateLimitStatus`, `NextDequeueTime`) from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs` and `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/RemoteService.cs`.

### Adapt

- Keep provider-independent orchestration, but replace filename/suffix identity matching with stable source IDs across all stages (current code points: `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`, `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`).
- Keep queue pause on `RateLimitException`, but decouple retry budgeting by stage (provider add vs unrestrict vs host download) currently mixed between torrent and download counters.
- Keep unpack worker separation, but add recursive pass driver and explicit capability matrix (current path only covers zip/rar in `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`).

### Avoid (as-is)

- Relying on count-only selected-files vs links matching for correctness (`RealDebridDebridClient.GetDownloadInfos`).
- Silent error swallowing in key paths (`UpdateTorrentClientData` catch ignored; delete provider errors ignored) from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`.
- Tick-level early `return` inside per-download loop guard clauses, which defers remaining torrent processing to next tick (`/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`).

## Evidence checklist additions for spike runs

- Capture pre/post provider file snapshots (`Torrent.RdFiles`, `Torrent.FilesSelected`, `Torrent.RdStatus`, `Torrent.RdEnded`) from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Torrent.cs`.
- For manual/subset runs, persist exact `DownloadManualFiles` input string and matched provider file IDs selected by provider adapter (`RealDebridDebridClient.SelectFiles` path).
- Persist `GetDownloadInfos` outputs with link counts per poll tick until download rows are created.
- Store per-download identity tuple at each stage: `{DownloadId, Path(restricted), Link(unrestricted), FileName, computed host path}` from `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Download.cs` and `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`.
- Record rate-limit evidence: thrown `RateLimitException.RetryAfter`, `TorrentRunner.NextDequeueTime`, and resumed dequeue timestamp.
- For unarchive runs, log archive extension gate decision, unpack start/end/error timestamps, archive deletion outcome, and output directory manifest pre/post unpack.
- For integration runs, include one trace where `HostDownloadAction=DownloadNone` and one where host download + unpack are enabled, to show branch behavior in `TorrentRunner.Tick`.

## Appendix: concise file/symbol index with absolute paths

- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/IDebridClient.cs`
  - `IDebridClient`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/RealDebridDebridClient.cs`
  - `SelectFiles`, `GetDownloadInfos`, `Unrestrict`, `UpdateData`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DebridClients/TorBoxDebridClient.cs`
  - `GetAvailableFiles`, `GetDownloadInfos`, `Unrestrict`, `MoveHashDirContents`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/Torrents.cs`
  - `Add*ToDebridQueue`, `DequeueFromDebridQueue`, `SelectFiles`, `CreateDownloads`, `UnrestrictLink`, `RetryTorrent`, `Delete`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/TorrentRunner.cs`
  - `Initialize`, `Tick`, `SetRateLimit`, `ActiveDownloadClients`, `ActiveUnpackClients`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/DownloadClient.cs`
  - `Start`, `Cancel`, `Pause`, `Resume`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Services/UnpackClient.cs`
  - `Start`, `Unpack`, `GetArchiveFiles`, `Extract`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/DownloadHelper.cs`
  - `GetDownloadPath`, `GetFileName`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/Helpers/RateLimitHandler.cs`
  - `SendAsync`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/DiConfig.cs`
  - `RegisterHttpClients`, `ConfigureResiliencePipeline`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/BackgroundServices/ProviderUpdater.cs`
  - `ExecuteAsync`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service/BackgroundServices/TaskRunner.cs`
  - `ExecuteAsync`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Torrent.cs`
  - `Files`, `ManualFiles`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Models/Data/Download.cs`
  - `Download` fields and `DownloadInfo`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/DownloadData.cs`
  - `Add`, `Reset`, `Update*`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Data/Data/TorrentData.cs`
  - `UpdateComplete`, `UpdateFilesSelected`, `UpdateRdData`
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Web/Controllers/TorrentsController.cs`
  - `UploadMagnet`, `UploadFile`, `CheckFiles`, `CheckFilesMagnet`, `Retry`, `RetryDownload`
- `/Users/nskaria/projects/romulus/references/rdt-client/client/src/app/add-new-torrent/add-new-torrent.component.ts`
  - manual selection payload construction (`downloadManualFiles`)
- `/Users/nskaria/projects/romulus/references/rdt-client/client/src/app/torrent.service.ts`
  - API request surface used by UI
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service.Test/Helpers/DownloadHelperTest.cs`
  - path-mapping edge-case assertions
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service.Test/Helpers/RateLimitHandlerTest.cs`
  - 429 `RetryAfter` handling assertions
- `/Users/nskaria/projects/romulus/references/rdt-client/server/RdtClient.Service.Test/Services/TorrentClients/TorBoxDebridClientTest.cs`
  - fakedl link shape and unrestrict parsing assertions
