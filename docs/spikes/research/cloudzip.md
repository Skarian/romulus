# Cloudzip Research Dump (module SHA `a84e32fd0917c92c3470b53569867333388b2446`)

Module root: [references/cloudzip](/Users/nskaria/projects/romulus/references/cloudzip)

## Spike mapping

1. Spike 3 (`remote zip enumeration and selective internal download`):
   - Central-directory discovery/parsing and selected entry reads are implemented in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go) and consumed by CLI commands [ls.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/ls.go), [info.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/info.go), [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go), and [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go).
   - Transport abstraction and range-header construction are in [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go), with concrete backends in [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go), [s3.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/s3.go), [lakefs.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/lakefs.go), [kaggle.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/kaggle.go), [local.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/local.go).
2. Spike 4 (`integration`):
   - Cloudzip contributes only the remote ZIP listing/read slice. It does not implement resolver flow, policy ordering across ignore/rename/unarchive, recursive extraction orchestration, or output-manifest lifecycle from the Spike 4 spec.
   - Mount subsystem ([cmd/mount_server.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/mount_server.go), [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go)) shows a reusable pattern for one-time parse + lazy selected-entry fetch + local cache.

## Source map (key files/functions)

1. Parser core:
   - [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go): `CentralDirectoryParser`, `GetCentralDirectory`, `Read`, `ReadCDR`, `ReaderForRecord`, EOCD/EOCD64 structs, ZIP64 extra parsing.
   - [go_zip_vendored.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/go_zip_vendored.go): DOS timestamp conversion + mode conversion helpers used by `ReadCDR`.
   - [remote.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/remote.go): `StorageAdapter` bridging `remote.Fetcher` to parser `OffsetFetcher`.
2. Transport and object factory:
   - [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go): generic range string building.
   - [dynamic.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/dynamic.go): scheme dispatch (`s3`, `file`, `http`, `kaggle`, `lakefs`) and optional logger injection.
   - [errors.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/errors.go): `ErrInvalidURI`, `ErrDoesNotExist`.
3. CLI entrypoints and behavior:
   - [common.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/common.go): shared open/list flow (`getCdr`), logging setup, stderr exit behavior.
   - [ls.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/ls.go), [info.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/info.go), [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go), [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go).
4. Parse/read proofs and fixture coverage:
   - [parser_test.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser_test.go): regular, zip64, large central-directory fixture (`big_directory.zip`), uncompressed; benchmark for repeated `GetCentralDirectory`.
   - test fixtures in [/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata).
5. Parse reuse + entry cache pattern (mount mode):
   - [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go) + [cache.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/commonfs/cache.go).

## Remote ZIP algorithm details (EOCD/CD parsing, range requests, selected-entry fetch)

1. Tail prefetch for EOCD lookup:
   - `getEOCDBuffer` requests trailing bytes with `Fetch(nil, &bufSize)` where `bufSize=65536` (`EOCDPrefetchBufferSize`) in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - For HTTP this becomes `Range: bytes=-65536` via `buildRange` in [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go) and [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go).
2. EOCD / ZIP64 branch:
   - `getCDLocation` scans the prefetched tail for `EOCDSignature` using `bytes.LastIndex`, parses `EOCD`, and checks sentinel max values (`0xffff`, `0xffffffff`) to detect ZIP64 in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - ZIP64 path calls `getCD64Location`, which scans the same tail buffer for `EOCD64Signature`, then parses `EOCD64` and returns 64-bit CD offset/size.
3. Central directory range read:
   - `parseCDR` requests the CD byte range with `Fetch(offset(loc.Offset), offset(loc.Offset+loc.SizeBytes))` and reads all bytes into memory, then iterates records with `ReadCDR` from a `bytes.Reader` in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - Range end is passed as `offset+size`; with inclusive range semantics this asks for one extra byte. Parsing still terminates by `pos < loc.SizeBytes`.
4. CDR record parse behavior:
   - `ReadCDR` reads raw metadata struct, then variable-length filename/extra/comment.
   - If raw size/offset fields are `0xffffffff`, ZIP64 values are taken from extra field header `0x0001` (`parseZip64ExtraFields`); if required ZIP64 fields are missing, returns `ErrInvalidZip`.
   - Directory names ending with `/` are normalized to directory mode and trailing slash is removed.
5. Selected-entry fetch path (`Read` / `ReaderForRecord`):
   - `Read(fileName)` always calls `GetCentralDirectory`, then exact string-matches `f.FileName == fileName` in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - For a match, `ReaderForRecord` fetches from `LocalFileHeaderOffset` to `offset + compressedSize + localHeaderSizeHeuristic(name)` in one range request, parses local header, skips `FileNameLength + ExtraFieldLength`, then limits stream to `CompressedSizeBytes`.
   - Decompression behavior is method-specific: `zip.Deflate` uses `flate.NewReader`; all other methods are returned as-is from the limited stream.

## API/signature-level behavior of key components

1. Core transport and parser contracts:

```go
type Fetcher interface {
    Fetch(ctx context.Context, startOffset *int64, endOffset *int64) (io.ReadCloser, error)
}

type OffsetFetcher interface {
    Fetch(start, end *int64) (io.Reader, error)
}

type CentralDirectoryParser struct { reader OffsetFetcher }
func NewCentralDirectoryParser(reader OffsetFetcher) *CentralDirectoryParser
func (p *CentralDirectoryParser) GetCentralDirectory() ([]*CDR, error)
func (p *CentralDirectoryParser) Read(fileName string) (io.Reader, error)
func ReaderForRecord(f *CDR, fetcher OffsetFetcher) (io.Reader, error)
```

Source: [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go), [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).

2. Factory and backend selection:

```go
func Object(uri string, opts ...ObjectOpt) (Fetcher, error)
```

- Scheme routing: `s3`, `file`/`local`, `http`/`https`, `kaggle`, `lakefs` in [dynamic.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/dynamic.go).
- Unknown/invalid schemes return wrapped `ErrInvalidURI` from [errors.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/errors.go).

3. Range construction semantics:

```go
func buildRange(offsetStart *int64, offsetEnd *int64) *string
```

- start+end => `bytes=start-end`
- start only => `bytes=start-`
- end only => `bytes=-end`
- nil,nil => no Range header

Source: [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go).

4. Data model available from CDR:
   - `CDR` fields include `FileName`, `LocalFileHeaderOffset`, compressed/uncompressed sizes, `CRC32Uncompressed`, `CompressionMethod`, `Modified`, `Mode`, `ExtraFields`, `FileComment` in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).

5. CLI behavior surfaces:
   - `cz ls` prints mode/compressed size/uncompressed size/modified time/name from CDR (`Run` in [ls.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/ls.go)).
   - `cz info` aggregates non-directory compressed/uncompressed totals (`Run` in [info.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/info.go)).
   - `cz cat <zip> <internalPath>` streams selected entry bytes to stdout (`Run` in [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go)).
   - `cz http` serves `GET /path/to/archive.zip?filename=entry` through same parser/read path (`Run` in [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go)).

## Performance/optimization shape (request counts, parse reuse)

1. Request count shape from code path:
   - `GetCentralDirectory`: 2 fetches (tail prefetch + central-directory range) via [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - `Read(fileName)`: 3 fetches total (same 2 + entry range) via [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
2. Repeated selected reads in CLI:
   - `cat` reconstructs parser and calls `Read` per invocation; no in-process CDR cache in [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go).
   - `http` handler does the same per request in [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go).
3. Mount mode parse reuse and content cache:
   - `BuildZipTree` calls `GetCentralDirectory` once at mount startup and builds an in-memory index in [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go).
   - Per-file content is cached on disk by key `sha1(zipPath, filename, crc)` in [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go) and [cache.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/commonfs/cache.go).
   - Cache miss path fetches only selected file body (`ReaderForRecord`) and validates bytes copied against expected uncompressed size in [cache.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/commonfs/cache.go).
4. Parse-size evidence from tests:
   - `TestCentralDirectoryParser_GetCentralDirectory` expects 150,000 entries from `big_directory.zip` in [parser_test.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser_test.go).
   - Benchmark `BenchmarkCentralDirectoryParser_Read` repeatedly measures `GetCentralDirectory` over in-memory archive bytes in [parser_test.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser_test.go).

## Error handling and diagnostics behavior

1. ZIP parser errors:
   - `ErrInvalidZip` and `ErrFileNotFound` are defined in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - Missing EOCD/EOCD64 signature, parse failures, missing required ZIP64 extra fields, short local-header/body reads map to `ErrInvalidZip`.
   - Not-found selected entry maps to `ErrFileNotFound`.
2. Remote object errors:
   - `ErrDoesNotExist` and `ErrInvalidURI` in [errors.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/errors.go).
   - HTTP/S3/Kaggle/LakeFS backends map 404/not-found cases to `ErrDoesNotExist`; other backend failures are returned as underlying errors ([http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go), [s3.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/s3.go), [kaggle.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/kaggle.go), [lakefs.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/lakefs.go)).
3. CLI/HTTP mapping:
   - CLI commands print human text to stderr and `os.Exit(1)` on errors in [common.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/common.go), [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go).
   - `http` command maps `ErrDoesNotExist` and `ErrFileNotFound` to HTTP 404; other read failures become 502 in [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go).
4. Diagnostics and logging:
   - Parser emits debug logs for central-directory read/parse durations in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - Backend fetchers log `range`, target, duration, and error class (`Debug`/`Warn`/`Error`) in backend files under [/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote).
   - `remote` package default logger is discard/error-level (`DummyLogger`) unless injected via `WithLogger` in [dynamic.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/dynamic.go).

## Assumptions/constraints and what is caller-owned

1. Archive-format assumptions encoded in parser:
   - EOCD/EOCD64 signatures must be discoverable in the last 64 KiB buffer fetched by `getEOCDBuffer` in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - `Read` selection key is exact `FileName` string equality in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - `ReaderForRecord` uses a local-header size heuristic (`30 + len(name) + 1024`), so oversized local-header variable region relative to this fetch window returns `ErrInvalidZip` in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
2. Compression-method handling constraints:
   - Only `zip.Deflate` gets transparent inflate; non-deflate methods are exposed as raw limited stream bytes in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - No explicit encryption/decryption path exists in parser/read code. Encrypted-entry behavior is `UNCONFIRMED` for spike fixtures.
3. Transport constraints:
   - Range request strings are generated, but fetchers do not enforce `206 Partial Content` or `Accept-Ranges` checks; non-404 non-network responses are not normalized into a dedicated range-capability error class in [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go).
4. Resource-lifecycle constraint:
   - Parser-facing interfaces return `io.Reader` (`OffsetFetcher`), not `io.ReadCloser`; parser/read call sites do not expose close control for response bodies in [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go) and [remote.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/remote.go).
5. Caller-owned responsibilities (not implemented in module):
   - Stable cross-stage entry identity schema for Spike 3/4 payloads.
   - Multi-entry selection orchestration, selected-vs-unselected output manifesting, and stage timeline instrumentation.
   - Resolver integration, recursive extraction policy, and post-extract cleanup semantics from Spike 4.

## Gaps/unknowns relative to spike specs

1. Spike 3 question: HTTP capability requirements and failure classes.
   - Code generates Range headers and handles 404, but no explicit capability probe or explicit handling for non-range-compliant upstreams.
   - Result: range support failure taxonomy is `UNCONFIRMED` without spike-run traces.
   - Evidence: [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go), [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go).
2. Spike 3 question: stable internal-entry identifiers.
   - Parser exposes offset/size/CRC/name metadata in `CDR`, but CLI interfaces use path-string selection (`cat`, `http filename=`) and do not emit a canonical ID payload.
   - Evidence: [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go), [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go), [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go).
3. Spike 3 question: selected-only subset download proof.
   - Single-entry read exists (`Read`/`cat`), but no built-in batch-subset API or output manifesting.
   - Evidence: [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go), [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go).
4. Spike 3 question: variant coverage.
   - Tests cover regular, zip64, uncompressed, large-directory fixtures; encrypted-entry coverage was not found in parser tests.
   - Evidence: [parser_test.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser_test.go).
5. Spike 4 integration requirements.
   - No resolver stage, no rename/ignore/unarchive policy pipeline, no recursion controller, and no integrated stage-labeled diagnostics pipeline found in this module.
   - Evidence: [cmd](/Users/nskaria/projects/romulus/references/cloudzip/cmd), [pkg](/Users/nskaria/projects/romulus/references/cloudzip/pkg).

## Reusable patterns for spike app (adopt/adapt/avoid)

1. Adopt:
   - Two-step remote ZIP strategy: tail scan -> central directory range -> selected local-header/body range from [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - Backend-agnostic fetcher interface and URI-based factory from [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go), [dynamic.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/dynamic.go).
   - Mount-mode one-time index + lazy selected read + persistent cache pattern from [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go), [cache.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/commonfs/cache.go).
2. Adapt:
   - Keep a persistent CDR map for multiple selected entries in one run (avoid reparsing per selected file as in `cat`/`http` handlers).
   - Define explicit entry identity payload using `CDR` fields (`FileName`, `LocalFileHeaderOffset`, `CompressedSizeBytes`, `CRC32Uncompressed`) from [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
   - Add transport wrappers that validate response status/range semantics and emit stage-tagged diagnostics around fetch calls (extend behavior around [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go)).
3. Avoid:
   - Name-only identity for cross-stage selection when duplicate names are possible.
   - Recreating parser for every selected entry when subset size >1.
   - Assuming non-deflate entries are transparently decompressed by `ReaderForRecord`.

## Evidence checklist additions for spike runs

1. Capture exact range requests and counts per stage:
   - tail prefetch range,
   - central-directory range,
   - per-selected-entry local-header/body range.
   - Anchors: [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go), [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go).
2. Persist enumerated CDR identity table for each run:
   - `FileName`, `LocalFileHeaderOffset`, `CompressedSizeBytes`, `UncompressedSizeBytes`, `CRC32Uncompressed`, `CompressionMethod`.
   - Anchor: [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go).
3. For subset runs, record selected vs unselected proof:
   - selection input,
   - fetched-entry list,
   - output manifest showing no unselected outputs.
   - Anchors: [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go), [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go).
4. Add failure matrix traces:
   - missing object,
   - missing internal file,
   - malformed zip metadata,
   - non-range/partial-content anomalies (`UNCONFIRMED` until traced).
   - Anchors: [errors.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/errors.go), [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go), [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go).
5. Add performance evidence:
   - entry count,
   - central-directory bytes,
   - parser duration logs,
   - requests per selected file with and without parse reuse.
   - Anchors: [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go), [parser_test.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser_test.go), [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go).

## Appendix: concise file/symbol index with absolute paths

1. [parser.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser.go)
   - `CentralDirectoryParser.getEOCDBuffer`, `getCDLocation`, `getCD64Location`, `parseCDR`, `GetCentralDirectory`, `Read`, `ReadCDR`, `ReaderForRecord`, `localHeaderSizeHeuristic`.
2. [remote.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/remote.go)
   - `StorageAdapter`, `Fetch`.
3. [go_zip_vendored.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/go_zip_vendored.go)
   - `msDosTimeToTime`, `unixModeToFileMode`, `msdosModeToFileMode`.
4. [fetcher.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/fetcher.go)
   - `Fetcher`, `buildRange`.
5. [dynamic.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/dynamic.go)
   - `Object`, `WithLogger`, `getObject`.
6. [http.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/http.go)
   - `HttpFetcher.Fetch`.
7. [s3.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/s3.go)
   - `NewS3ObjectFetcher`, `S3ObjectFetcher.Fetch`.
8. [lakefs.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/lakefs.go)
   - `NewLakeFSFetcher`, `Fetch`, `rangeRequest`, `directFetch`.
9. [kaggle.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/kaggle.go)
   - `NewKaggleFetcher`, `fetchDatasetUrl`, `Fetch`.
10. [local.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/local.go)
    - `NewLocalFetcher`, `Fetch`, `localParseUri`.
11. [errors.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/remote/errors.go)
    - `ErrInvalidURI`, `ErrDoesNotExist`.
12. [common.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/common.go)
    - `getCdr`, `setupLogging`, `die`, `expandStdin`.
13. [ls.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/ls.go)
    - `lsCmd.Run`.
14. [info.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/info.go)
    - `infoCmd.Run`.
15. [cat.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/cat.go)
    - `catCmd.Run`.
16. [http.go](/Users/nskaria/projects/romulus/references/cloudzip/cmd/http.go)
    - `httpCmd.Run` handler behavior.
17. [builder.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/builder.go)
    - `BuildZipTree`, `getOpenerFor`, cache key strategy.
18. [cache.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/mount/commonfs/cache.go)
    - `FileCache.Get`, `FileCache.Set`.
19. [parser_test.go](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/parser_test.go)
    - `TestCentralDirectoryParser_*`, `BenchmarkCentralDirectoryParser_Read`.
20. [README.md](/Users/nskaria/projects/romulus/references/cloudzip/README.md)
    - algorithm intent and command-level behavior narrative.
