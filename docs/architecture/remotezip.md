# `remotezip/` Package Architecture

## Purpose

`remotezip/` owns exact-container remote ZIP behavior. It probes remote range support, enumerates ZIP metadata without downloading the whole container, assigns duplicate-safe archive-entry identity, and copies one selected entry into a caller-provided sink.

This package stays more concrete because its identity and transport rules are contract-bearing:
- it never falls back to whole-container download,
- archive-entry identity must survive duplicate filenames,
- selected-entry copy must rematch by stable identity before bytes move,
- selected-entry copy must be able to resume from a caller-provided byte offset without whole-container fallback.

## Owned Responsibilities

- Probe remote ZIP endpoints for valid range support.
- Validate that range responses are structurally usable.
- Enumerate ZIP metadata remotely from the exact container URL.
- Assign duplicate-safe `ArchiveEntryIdentity`.
- Copy one selected archive entry into a caller-provided output sink.

## Explicit Non-Responsibilities

- `remotezip/` does not own source snapshots or browse filtering.
- `remotezip/` does not own queue state or retry policy.
- `remotezip/` does not own final output naming or extraction.
- `remotezip/` does not own Real-Debrid link resolution.

## Authorities and Owned Records

- `RemoteZipProbe`
  - Range-support facts for one archive URL.
- `ArchiveEntryIdentity`
  - Stable identity for one ZIP entry, safe against duplicate names.
- `ArchiveEntryDescriptor`
  - Public enumeration record returned to `source/`.
- `EnumeratedRemoteZip`
  - Container metadata plus visible entries.

## Dependencies

- Inbound dependencies:
  - `source/` for archive-selection browse.
  - `downloads/attempts` for selected-entry copy.
- Outbound dependencies:
  - HTTP range-read boundary,
  - `diagnostics/` for probe, enumerate, and copy events.
- What may cross the root-package boundary:
  - `ArchiveEntryIdentity`,
  - `ArchiveEntryDescriptor`,
  - `EnumerateRemoteZipRequest`,
  - `CopySelectedEntryRequest`.
- What may not cross the root-package boundary:
  - raw HTTP client types,
  - queue or output mutations.

## Public Entry Points

- `RemoteZipFacade.enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip>`
- `RemoteZipFacade.copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit>`

## Internal Structure

### `remotezip/probe`

- Purpose: prove that a remote URL is usable for ZIP metadata reads.
- What it owns:
  - HEAD or initial range checks,
  - `Accept-Ranges` and `Content-Range` validation,
  - content-length sanity checks.
- What it must not own:
  - ZIP parsing,
  - queue logic.
- Sibling interaction:
  - enumeration and copy both depend on probe output.
- What may cross this seam:
  - `RemoteZipProbe`.
- What may not cross this seam:
  - queue or output state.

### `remotezip/enumerate`

- Purpose: enumerate the ZIP central directory remotely.
- What it owns:
  - metadata-first ZIP parsing,
  - duplicate-safe identity assignment,
  - entry rematch rules.
- What it must not own:
  - final output policy,
  - source ignore filtering.
- Sibling interaction:
  - browse consumes descriptors,
  - copy reuses the same identity rules.
- What may cross this seam:
  - `ArchiveEntryDescriptor`,
  - `ArchiveEntryIdentity`.
- What may not cross this seam:
  - full archive bytes.

### `remotezip/copy`

- Purpose: copy one selected archive entry only.
- What it owns:
  - identity rematch,
  - ranged reads for selected entry data,
  - resume-safe ranged reads from a saved byte offset,
  - progress callbacks during copy.
- What it must not own:
  - archive enumeration fallback,
  - output finalization.
- Sibling interaction:
  - depends on probe and enumerate seams.
- What may cross this seam:
  - `CopySelectedEntryRequest`.
- What may not cross this seam:
  - queue-state mutation.

## Internal Files

### `RemoteZipFacade.kt`
- Internal area: `remotezip/root`
- Purpose: expose the public API for enumeration and selected-entry copy.
- Responsibility: delegate to probe, enumeration, and copy services.
- Depends on: `RangeProbeService`, `RemoteZipEnumerator`, `SelectedEntryCopier`
- Must not depend on: queue stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
class RemoteZipFacade(
    private val enumerator: RemoteZipEnumerator,
    private val copier: SelectedEntryCopier
) {
    suspend fun enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip>
    suspend fun copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit>
}
```

### `HttpRangeReader.kt`
- Internal area: `remotezip/probe`
- Purpose: isolate HTTP range reads.
- Responsibility: provide HEAD and byte-range reads without leaking HTTP-client details.
- Depends on: HTTP client boundary
- Must not depend on: ZIP parsing or queue state
- Visibility: `internal`
- Key types/functions:

```kotlin
data class RangeReadResult(
    val statusCode: Int,
    val contentRange: String?,
    val body: ByteArray,
    val contentLength: Long?
)

interface HttpRangeReader {
    suspend fun head(url: String): Result<RangeReadResult>
    suspend fun readRange(url: String, startInclusive: Long, endInclusive: Long): Result<RangeReadResult>
}
```

### `RangeProbeService.kt`
- Internal area: `remotezip/probe`
- Purpose: validate that a remote URL is safe to treat as a ranged ZIP container.
- Responsibility: reject invalid range support early and explicitly.
- Depends on: `HttpRangeReader`
- Must not depend on: ZIP parsing or queue state
- Visibility: `internal`
- Key types/functions:

```kotlin
data class RemoteZipProbe(
    val archiveUrl: String,
    val contentLength: Long
)

class RangeProbeService(
    private val rangeReader: HttpRangeReader
) {
    suspend fun probe(url: String): Result<RemoteZipProbe>
}
```

### `ArchiveEntryModels.kt`
- Internal area: `remotezip/shared`
- Purpose: define the public archive-entry models.
- Responsibility: make duplicate-safe entry identity explicit and reusable by browse and copy.
- Depends on: Kotlin stdlib only
- Must not depend on: queue stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
data class ArchiveEntryIdentity(
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long,
    val normalizedPath: String
)

data class ArchiveEntryDescriptor(
    val identity: ArchiveEntryIdentity,
    val entryPath: String,
    val sizeBytes: Long
)

data class EnumerateRemoteZipRequest(
    val archiveUrl: String
)

data class EnumeratedRemoteZip(
    val entries: List<ArchiveEntryDescriptor>
)

data class CopySelectedEntryRequest(
    val archiveUrl: String,
    val identity: ArchiveEntryIdentity,
    val destination: Path,
    val resumeByteOffset: Long,
    val onProgress: suspend (downloadedBytes: Long) -> Unit
)
```

### `RemoteZipEnumerator.kt`
- Internal area: `remotezip/enumerate`
- Purpose: enumerate ZIP entries remotely from metadata only.
- Responsibility: parse the central directory, assign stable identities, and return entries without downloading the whole container.
- Depends on: `RangeProbeService`, `RemoteZipSeekableChannel`
- Must not depend on: output services or queue state
- Visibility: `internal`
- Key types/functions:

```kotlin
class RemoteZipEnumerator(
    private val probeService: RangeProbeService,
    private val seekableChannelFactory: (RemoteZipProbe) -> RemoteZipSeekableChannel
) {
    suspend fun enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip>
}
```

### `RemoteZipSeekableChannel.kt`
- Internal area: `remotezip/enumerate`
- Purpose: expose a ranged remote ZIP as a seekable byte source for metadata parsing.
- Responsibility: satisfy ZIP parser reads through lazy range requests only.
- Depends on: `HttpRangeReader`
- Must not depend on: queue or output state
- Visibility: `internal`
- Key types/functions:

```kotlin
class RemoteZipSeekableChannel(
    private val probe: RemoteZipProbe,
    private val rangeReader: HttpRangeReader
) : SeekableByteChannel {
    override fun read(dst: ByteBuffer): Int
    override fun position(): Long
    override fun position(newPosition: Long): SeekableByteChannel
    override fun size(): Long
    override fun isOpen(): Boolean
    override fun close()
}
```

### `SelectedEntryCopier.kt`
- Internal area: `remotezip/copy`
- Purpose: copy only one selected archive entry by stable identity.
- Responsibility: rematch the requested identity against the remote ZIP metadata before copying, then stream only the remaining selected entry bytes starting at the caller-provided resume offset.
- Depends on: `RangeProbeService`, `RemoteZipEnumerator`, `HttpRangeReader`
- Must not depend on: final output policy or queue state
- Visibility: `internal`
- Key types/functions:

```kotlin
class SelectedEntryCopier(
    private val probeService: RangeProbeService,
    private val enumerator: RemoteZipEnumerator,
    private val rangeReader: HttpRangeReader
) {
    suspend fun copy(request: CopySelectedEntryRequest): Result<Unit> {
        // Contract:
        // - probe the archive URL
        // - enumerate metadata
        // - rematch by ArchiveEntryIdentity, not by filename
        // - if resumeByteOffset > 0, append only the remaining bytes after that offset
        // - fail explicitly if the entry no longer matches the saved identity or is shorter than the saved offset
        // - never fall back to downloading the whole container
    }
}
```

## Key Flows

1. Archive-selection browse:
   - `source/` resolves the exact outer `.zip` URL through `realdebrid/`.
   - `RemoteZipEnumerator` probes range support.
   - It parses ZIP metadata remotely and returns `ArchiveEntryDescriptor` values.
   - `source/` applies ignore rules before showing rows.

2. Selected-entry copy:
   - `downloads/attempts` passes `ArchiveEntryIdentity` captured at enqueue time.
   - `SelectedEntryCopier` reprobes and re-enumerates the archive.
   - It rematches the selected entry by stable identity.
   - It resumes from the saved offset when a transfer checkpoint already exists.
   - It streams only that entry to the reserved artifact path.

## Failure and Recovery Rules

- Invalid range support is a hard failure for archive-selection mode.
- Enumeration failure does not fall back to standard browse.
- Copy failure does not fall back to full-container download.
- Duplicate filenames are safe because `ArchiveEntryIdentity` is not filename-only.
- Resume copy must fail explicitly if the selected entry no longer matches the saved identity or saved byte offset.

## Testing Plan

### `RangeProbeServiceTest.kt`

- Scope: unit
- Covers:
  - valid range-support probe
  - missing or invalid `Content-Range` rejection
  - content-length mismatch rejection
- Fixtures:
  - fake `HttpRangeReader`

### `RemoteZipEnumeratorTest.kt`

- Scope: unit
- Covers:
  - metadata-first enumeration
  - duplicate filename identity safety
  - alphabetical descriptor ordering delegated to caller, not silently applied here
  - enumeration failure on invalid ZIP metadata
- Fixtures:
  - fake `HttpRangeReader`
  - ZIP fixtures served from byte arrays

### `SelectedEntryCopierTest.kt`

- Scope: unit
- Covers:
  - identity rematch before copy
  - selected-entry-only copy
  - resume continues from the saved byte offset
  - failure when the requested identity no longer matches
  - progress callback invocation
- Fixtures:
  - fake `HttpRangeReader`
  - ZIP fixtures
  - temp filesystem

### `RemoteZipIntegrationTest.kt`

- Scope: integration
- Covers:
  - enumerate remote ZIP over HTTP range support
  - copy one selected entry without downloading unselected entries
  - resume selected-entry copy after interruption without whole-container fallback
  - invalid-range recovery surface
- Fixtures:
  - local HTTP test server
  - ZIP fixtures with duplicate filenames
  - temp filesystem

## Open Questions or Deferred Decisions

None currently.
