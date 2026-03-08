# Apache Commons Compress ZIP Research Dump (module SHA `5c6ab6388a79e7965ae33615478e93ba76c2a17d`)

## Spike mapping

1. **Spike 3 (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-3-remote-zip.md`)**
   - `ZipFile` is a random-access ZIP reader built on `SeekableByteChannel`; this is the extension point for remote ZIP reads (`/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipFile.java`).
   - The module provides entry enumeration, per-entry raw stream reads, decompressed stream reads, duplicate-name handling, and physical-order iteration; it does **not** ship a remote HTTP-range channel implementation.
2. **Spike 4 (`/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`)**
   - `ZipArchiveEntry` surfaces offsets, method, sizes, name source, and disk-start metadata that can be carried across stage boundaries (`/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipArchiveEntry.java`).
   - `ArchiveEntry.resolveIn(...)` provides built-in zip-slip-safe output path resolution for extraction integration (`/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/ArchiveEntry.java`).

## Source map (key files/functions)

1. Core ZIP file parser + reader:
   - `ZipFile.Builder` (`setIgnoreLocalFileHeader`, `setMaxNumberOfDisks`, `setUseUnicodeExtraFields`, `setSeekableByteChannel`): `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipFile.java`.
   - parse path: `positionAtEndOfCentralDirectoryRecord`, `positionAtCentralDirectory*`, `populateFromCentralDirectory`, `readCentralDirectoryEntry`, `resolveLocalFileHeaderData`, `setDataOffset`: same file.
   - access path: `getEntries`, `getEntriesInPhysicalOrder`, `getEntry`, `getInputStream`, `getRawInputStream`, `copyRawEntries`, `stream`: same file.
2. Entry model and identity fields:
   - `ZipArchiveEntry` (`getName`, `getRawName`, `getLocalHeaderOffset`, `getDataOffset`, `getDiskNumberStart`, `getNameSource`, `isStreamContiguous`, `equals/hashCode`): `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipArchiveEntry.java`.
3. Feature gating and unsupported handling:
   - `ZipUtil.canHandleEntryData`, `ZipUtil.checkRequestedFeatures`, `supportsEncryptionOf`, `supportsMethodOf`: `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipUtil.java`.
   - `UnsupportedZipFeatureException`: `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/UnsupportedZipFeatureException.java`.
4. Channel abstractions:
   - bounded reads for channel-backed entries: `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/utils/BoundedArchiveInputStream.java`, `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/utils/BoundedSeekableByteChannelInputStream.java`.
   - split/multi-channel support: `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipSplitReadOnlySeekableByteChannel.java`, `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/utils/MultiReadOnlySeekableByteChannel.java`.
5. Behavior proofs in tests:
   - `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/ZipFileTest.java`
   - `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/ZipFileIgnoringLocalFileHeaderTest.java`
   - `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/EncryptedArchiveTest.java`
   - `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/DataDescriptorTest.java`
   - `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/ZipTest.java`

## Zip parser/extractor API usage model (`ZipFile`, channels, entry access)

1. **Builder and source wiring**
   - `ZipFile.builder()` is the main construction path.
   - Builder defaults include UTF-8 charset and read open mode.
   - Builder allows either path-based opening (internal `openZipChannel(...)`) or direct channel injection (`setChannel(...)`, deprecated alias `setSeekableByteChannel(...)`) in `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipFile.java`.
2. **Constructor parse sequence (on `get()`)**
   - Reads EOCD by scanning backwards near file tail (`tryToLocateSignature` via `positionAtEndOfCentralDirectoryRecord`).
   - Resolves ZIP32 or ZIP64 central directory start (`positionAtCentralDirectory32/64`).
   - Parses central directory entries (`populateFromCentralDirectory`, `readCentralDirectoryEntry`) into an internal list.
   - By default, also resolves local header data for each entry (`resolveLocalFileHeaderData`) unless `ignoreLocalFileHeader=true`.
3. **Entry enumeration APIs**
   - `getEntries()` / `stream()` expose central-directory order.
   - `getEntriesInPhysicalOrder()` sorts by `(diskNumberStart, localHeaderOffset)`.
   - `getEntries(name)` returns all duplicates with same name; `getEntry(name)` returns first in central-directory order.
4. **Entry content APIs**
   - `getRawInputStream(entry)` returns bounded compressed bytes for that entry.
   - `getInputStream(entry)` wraps raw stream and dispatches decompression by method (`STORED`, `DEFLATED`, `BZIP2`, `ENHANCED_DEFLATED`, `UNSHRINKING`, `IMPLODING`, `ZSTD`, `XZ`; unsupported methods throw).
   - Streams implement `InputStreamStatistics` (validated in `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/ZipTest.java`).
5. **Entry type coupling**
   - `getInputStream` and `getRawInputStream` return `null` if the supplied entry is not the internal `ZipFile.Entry` type (guard `if (!(entry instanceof Entry))` in `ZipFile`).
6. **Preamble support**
   - `getFirstLocalFileHeaderOffset()` and `getContentBeforeFirstLocalFileHeader()` expose bytes before first LFH (tested by `testReadingOfExtraDataBeforeZip` in `ZipFileTest`).

## Selective-entry strategies and identity implications

1. **Selection strategy available in module**
   - Enumerate metadata first (`getEntries`, `stream`, or `getEntriesInPhysicalOrder`).
   - Filter chosen entries by caller predicate.
   - Retrieve chosen entries only with `getRawInputStream` (compressed bytes) or `getInputStream` (decompressed bytes).
   - `copyRawEntries(target, predicate)` provides built-in selected-entry ZIP reassembly without re-compression.
2. **Duplicate-name implications**
   - Duplicate entry names are supported and tested (`COMPRESS-227.zip`) in both default mode and `ignoreLocalFileHeader=true` mode.
   - `getEntry(name)` is not unique-safe because it returns first match only.
3. **Stable identity signals present in source**
   - `ZipArchiveEntry` exposes `getLocalHeaderOffset()`, `getDiskNumberStart()`, `getDataOffset()`, `getName()`, `getCompressedSize()`, `getSize()`, `getMethod()`.
   - `ZipFile.Entry.equals/hashCode` includes local-header/data offsets and disk number, not just name.
4. **Identity caveat for integration**
   - Since stream/read methods require internal entry objects, cross-stage selection payloads should carry reconstructable fields (at minimum name + offset/disk metadata), then remap to live `ZipArchiveEntry` objects in the active `ZipFile` instance.

## Remote-zip adapter requirements (what extra components are required)

1. **Mandatory adapter surface**
   - A read-only `SeekableByteChannel` implementation is required, because `ZipFile` parser logic uses `size()`, `position(long)`, `position()`, and `read(ByteBuffer)` heavily.
2. **Tail and random-read capability is mandatory**
   - EOCD lookup in `ZipFile.tryToLocateSignature(...)` performs many tiny, backward probes from end-of-file; adapter must support efficient random tail reads.
3. **Known total size is mandatory**
   - `ZipFile` parsing starts from `channel.size()` to compute tail scan bounds.
4. **Local-header seek support is mandatory for extraction**
   - Entry stream creation calls `setDataOffset(...)` / `getDataOffset(...)`, which seek to each entry’s LFH and compute data start.
5. **Concurrency behavior requirement**
   - For non-`FileChannel` channels, bounded entry streams synchronize on shared channel before seeking+reading (`BoundedSeekableByteChannelInputStream`), so concurrent reads serialize on that lock.
6. **Optional split-archive support path**
   - If remote split archives are needed, composition can follow `MultiReadOnlySeekableByteChannel` + `ZipSplitReadOnlySeekableByteChannel` behavior, including split signature expectations.
7. **Not provided by this module**
   - No HTTP transport, no Range-request implementation, no retry/backoff policy, and no remote auth/session integration were found in ZIP/utils code paths searched under `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip` and `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/utils`.

## Error/unsupported/encryption behavior

1. **Archive shape errors during open/parse**
   - `ZipException("Archive is not a ZIP archive")` when EOCD signature cannot be found.
   - `ArchiveException("Central directory is empty, can't expand corrupt archive.")` when signatures mismatch and file starts with LFH.
   - Multiple explicit corruption guards in `ZipFile`: negative sizes/offsets, LFH after central directory, data overlap with central directory, invalid split relationships.
2. **Entry-name limit enforcement**
   - Entry name length is checked with `ArchiveUtils.checkEntryNameLength(...)` against builder `maxEntryNameLength`; violations throw `ArchiveException`.
3. **Unsupported/encrypted entry handling**
   - `canReadEntryData(entry)` gates unsupported features via `ZipUtil.canHandleEntryData`.
   - `getInputStream(entry)` calls `ZipUtil.checkRequestedFeatures` and throws `UnsupportedZipFeatureException` for encryption or unsupported methods.
   - `EncryptedArchiveTest` verifies encrypted entries return `canReadEntryData=false` and throw with `Feature.ENCRYPTION`.
4. **Unsupported method set in `ZipFile` extraction switch**
   - Methods like AES-encrypted, LZMA, PPMD, JPEG, WAVPACK, tokenization, and unknown methods lead to `UnsupportedZipFeatureException`.
5. **Offset resolution failures**
   - `getRawInputStream` returns `null` when data offset remains unknown or entry type mismatch.
   - EOF/truncation while reading metadata surfaces as `EOFException`/`IOException` from parse and stream calls.

## Performance/retrieval-order considerations

1. **EOCD scan cost profile**
   - `tryToLocateSignature` scans from `(size - MIN_EOCD_SIZE)` backwards to `(size - MAX_EOCD_SIZE)` one byte at a time and reads 4 bytes each probe; naive remote backends can amplify request count.
2. **Central-directory-first parse**
   - Open sequence parses all central-directory entries eagerly.
   - Default mode also resolves each local header eagerly; `ignoreLocalFileHeader=true` defers local-header work until offset is needed.
3. **Physical-order retrieval option**
   - `getEntriesInPhysicalOrder()` sorts by disk/start offset; using this order can reduce seek churn relative to arbitrary selection order.
4. **Concurrent read behavior differs by channel type**
   - File-backed path uses positioned `FileChannel.read(buf, pos)` for bounded streams.
   - Generic channel path serializes seek+read under synchronized block.
5. **Raw copy path avoids decompression/recompression**
   - `copyRawEntries` + `getRawInputStream` can preserve compressed payload and metadata for selected ZIP-to-ZIP workflows (validated by `ZipTest.testCopyRawEntriesFromFile`).

## Gaps/unknowns relative to spike specs

1. **Spike 3 transport evidence gap**
   - `ZipFile` has no built-in HTTP Range tracing or transport abstraction; this module alone cannot produce required HTTP trace artifacts from `spike-3-remote-zip.md`.
2. **Spike 3 selected-only remote fetch gap**
   - Selection and per-entry read primitives exist, but remote selective download behavior depends on external channel/transport implementation; not present here.
3. **Spike 3 identity payload format gap**
   - Module exposes entry metadata but does not define a stable serialized entry-ID schema for downstream queue payloads; this must be designed externally.
4. **Spike 4 end-to-end stage timeline gap**
   - ZIP parser provides per-entry data, but no orchestration layer for resolver->selection->download->recursive-unarchive stage tracing exists here.
5. **Encryption handling for spike expectations**
   - Read path is fail-fast for encrypted entries (no password/decryption path in `ZipFile`); encrypted fixture handling in spike runs must be treated as unsupported unless a separate decrypt-capable module is introduced.
6. **UNKNOWN (not found in inspected source/tests)**
   - No module-level implementation for remote retries, throttling, auth token refresh, or request cancellation.
   - No direct API to open entry by `(offset,disk)` without first mapping to in-memory `ZipArchiveEntry`.

## Reusable patterns for spike app (adopt/adapt/avoid)

1. **Adopt**
   - Channel-injection seam (`ZipFile.builder().setChannel(...)`) for remote ZIP parser reuse.
   - `ignoreLocalFileHeader=true` option for metadata-only passes, then lazy offset resolution only for selected entries.
   - `getEntriesInPhysicalOrder()` for deterministic, seek-aware fetch order.
   - `copyRawEntries(...)` for selected-entry ZIP reassembly when output should remain zipped.
   - `ArchiveEntry.resolveIn(...)` for safe extraction target resolution.
2. **Adapt**
   - Build a remote `SeekableByteChannel` with tail-read optimization/caching tuned for EOCD scan pattern.
   - Define external stable selection identity: include at least `name`, `diskNumberStart`, `localHeaderOffset`, `compressedSize`, `method`.
   - Add stage-level telemetry around channel reads (offset, length, timing) to satisfy spike evidence requirements.
3. **Avoid**
   - Name-only selection identity (`getEntry(name)`), because duplicates are legal and tested.
   - Assuming concurrent entry streams scale linearly on non-file channels; synchronized seek+read path is serialized.
   - Assuming encrypted entries are readable via `ZipFile`; they fail with `UnsupportedZipFeatureException`.

## Evidence checklist additions for spike runs

1. Record channel call trace for open phase:
   - first `size()` call, EOCD probe offsets, central-directory start offset, and whether ZIP32/ZIP64 path executed.
2. Record parse mode:
   - `ignoreLocalFileHeader` enabled/disabled, and count of local-header seeks performed.
3. Record entry identity capture:
   - for each selected entry: `name`, `diskNumberStart`, `localHeaderOffset`, `dataOffset` (if resolved), `compressedSize`, `size`, `method`, `nameSource`.
4. Record selection correctness with duplicates:
   - include at least one fixture with duplicate names and prove selected instance mapping by offset, not name only.
5. Record selected-only retrieval proof:
   - list selected entries, retrieved byte totals per entry, and explicit absence of unselected outputs.
6. Record unsupported/error taxonomy:
   - encryption failure, unsupported method failure, malformed archive failure, and truncated-read failure with stage labels.
7. Record order strategy effect:
   - compare retrieval logs for central-directory order vs physical-order selection to show seek/request pattern differences.
8. Record extraction safety behavior (Spike 4 pathing):
   - path normalization via `resolveIn(...)` and handling outcome for attempted traversal paths.

## Appendix: concise file/symbol index with absolute paths

1. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipFile.java`
   - `Builder`, `openZipChannel`, `tryToLocateSignature`, `populateFromCentralDirectory`, `readCentralDirectoryEntry`, `resolveLocalFileHeaderData`, `setDataOffset`, `getEntries*`, `getInputStream`, `getRawInputStream`, `copyRawEntries`.
2. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipArchiveEntry.java`
   - `getName`, `getRawName`, `getLocalHeaderOffset`, `getDataOffset`, `getDiskNumberStart`, `getNameSource`, `isStreamContiguous`, `equals/hashCode`.
3. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipUtil.java`
   - `canHandleEntryData`, `checkRequestedFeatures`, `supportsEncryptionOf`, `supportsMethodOf`, `setNameAndCommentFromExtraFields`.
4. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/UnsupportedZipFeatureException.java`
   - `Feature` (`ENCRYPTION`, `METHOD`, etc.), exception constructors/getters.
5. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/zip/ZipSplitReadOnlySeekableByteChannel.java`
   - `buildFromLastSplitSegment`, `forPaths`, `assertSplitSignature`.
6. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/utils/MultiReadOnlySeekableByteChannel.java`
   - `position(long)`, `position(channelNumber, relativeOffset)`, `read(ByteBuffer)`, `size()`.
7. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/utils/BoundedSeekableByteChannelInputStream.java`
   - synchronized seek+read implementation for bounded entry streams.
8. `/Users/nskaria/projects/romulus/references/commons-compress/src/main/java/org/apache/commons/compress/archivers/ArchiveEntry.java`
   - `resolveIn(Path)` zip-slip-safe resolution.
9. `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/ZipFileTest.java`
   - order, offsets, duplicate names, split extraction, preamble handling, concurrent reads.
10. `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/ZipFileIgnoringLocalFileHeaderTest.java`
    - behavior with `ignoreLocalFileHeader=true`.
11. `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/EncryptedArchiveTest.java`
    - encrypted entry unsupported behavior.
12. `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/zip/DataDescriptorTest.java`
    - `getRawInputStream` + raw entry copy behavior.
13. `/Users/nskaria/projects/romulus/references/commons-compress/src/test/java/org/apache/commons/compress/archivers/ZipTest.java`
    - `copyRawEntries`, split archive readback with `setMaxNumberOfDisks`, input-stream statistics parity.
