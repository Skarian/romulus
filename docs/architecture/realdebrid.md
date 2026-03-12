# `realdebrid/` Package Architecture

## Purpose

`realdebrid/` owns all Real-Debrid-specific protocol behavior and encrypted API-token access. It exposes Romulus-shaped models to the rest of the app for token readiness, provider inventory, provider acquisition polling, unrestricted-link resolution, and exact `.zip` container lookup.

This package stays moderately concrete because spike evidence locked the execution stages:
- token validation is separate from token persistence,
- inventory enumeration is separate from selection,
- acquisition polling is a first-class public seam for fresh start and resume,
- unrestricted-link resolution is a later step after acquisition reports links ready,
- exact `.zip` lookup reuses the same provider inventory and acquisition stages instead of inventing a side path.

## Owned Responsibilities

- Store and read the encrypted Real-Debrid token.
- Validate candidate tokens before saving them.
- Expose masked-token and token-readiness state hydrated from storage.
- Apply request budgeting so app-side concurrency does not oversubscribe the provider.
- Enumerate provider inventory for one or more source torrents.
- Select the correct provider file for a queue row or exact `.zip` path.
- Expose provider acquisition as a first-class start-or-resume seam that surfaces waiting updates until links are ready.
- Resolve ready links into app-shaped download units.

## Explicit Non-Responsibilities

- `realdebrid/` does not own queue rows, retry policy, or queue state.
- `realdebrid/` does not own source snapshots or browse filtering.
- `realdebrid/` does not own output naming or local file I/O.
- `realdebrid/` does not own remote ZIP enumeration.
- `realdebrid/` does not decide UI field locking; it reports token readiness only.

## Authorities and Owned Records

- `StoredTokenRecord`
  - Encrypted token owned only by `CredentialVault`.
- `TokenReadiness`
  - Live readiness of the saved token.
- `ProviderSourceRef`
  - Source-owned torrent reference reshaped for Real-Debrid calls.
- `ProviderInventory`
  - Aggregated provider file list for one or more source torrents.
- `ProviderLocator`
  - Stable queue-owned selection contract needed to re-resolve the same selected provider file later without path-only fallback.
- `ProviderResumeMarker`
  - Opaque marker needed to resume provider acquisition polling without resetting the `Preparing` deadline.
- `ResolvedDownloadUnit`
  - Final app-shaped downloadable link plus metadata.
- `ProviderReadyLink`
  - Provider-side ready link that still requires unrestricted-link resolution.
- `ArchiveContainerLocator`
  - Exact outer `.zip` container link and locator data for archive-selection mode.

## Dependencies

- Inbound dependencies:
  - `app/` and `ui/settings` for token save and token state.
  - `source/` for provider inventory and exact `.zip` lookup.
  - `downloads/` for standard-file resolution and provider acquisition.
- Outbound dependencies:
  - encrypted credential storage boundary,
  - Real-Debrid HTTP client boundary,
  - `diagnostics/` for auth and provider events.
- What may cross the root-package boundary:
  - masked token state,
  - token readiness,
  - provider inventory,
  - provider locators,
  - acquisition markers,
  - resolved download units.
- What may not cross the root-package boundary:
  - raw Real-Debrid response models,
  - raw HTTP clients,
  - queue rows.

## Public Entry Points

- `RealDebridFacade.observeMaskedToken(): StateFlow<MaskedTokenState>`
- `RealDebridFacade.observeTokenReadiness(): StateFlow<TokenReadiness>`
- `RealDebridFacade.readTokenReadiness(): TokenReadiness`
- `RealDebridFacade.saveValidatedToken(candidate: String): TokenSaveResult`
- `RealDebridFacade.enumerateProviderFiles(request: ProviderInventoryRequest): Result<ProviderInventory>`
- `RealDebridFacade.startAcquisition(locator: ProviderLocator): Result<AcquisitionStatus>`
- `RealDebridFacade.resumeAcquisition(marker: ProviderResumeMarker): Result<AcquisitionStatus>`
- `RealDebridFacade.resolveReadyLink(link: ProviderReadyLink): Result<ResolvedDownloadUnit>`
- `RealDebridFacade.resolveExactZip(request: ExactZipRequest): Result<ArchiveContainerLocator>`

## Internal Structure

### `realdebrid/auth`

- Purpose: own token persistence, masking, and readiness.
- What it owns:
  - encrypted token storage,
  - candidate-token validation,
  - masked-token projection,
  - token-readiness projection.
- What it must not own:
  - queue rows,
  - provider inventory,
  - download execution.
- Sibling interaction:
  - feeds token state to inventory, acquisition, and link services.
- What may cross this seam:
  - `MaskedTokenState`,
  - `TokenReadiness`,
  - `TokenSaveResult`.
- What may not cross this seam:
  - raw encrypted bytes.

### `realdebrid/budget`

- Purpose: protect the provider boundary from app-side request bursts.
- What it owns:
  - concurrency limits,
  - retry-after delays,
  - backoff state derived from provider responses.
- What it must not own:
  - queue retry scheduling,
  - token storage.
- Sibling interaction:
  - used by inventory, acquisition, and link services.
- What may cross this seam:
  - request permits,
  - retry-after windows.
- What may not cross this seam:
  - queue semantics.

### `realdebrid/inventory`

- Purpose: turn one or more source torrents into a Romulus-shaped provider inventory.
- What it owns:
  - host selection,
  - torrent registration,
  - provider file enumeration,
  - locator construction.
- What it must not own:
  - queue state,
  - browse filtering,
  - output policy.
- Sibling interaction:
  - used by `source/` and `downloads/`.
- What may cross this seam:
  - `ProviderInventory`,
  - `ProviderLocator`.
- What may not cross this seam:
  - raw API payloads.

### `realdebrid/acquisition`

- Purpose: own provider selection and acquisition polling.
- What it owns:
  - provider file selection,
  - acquisition start,
  - acquisition resume,
  - polling status,
  - 24-hour-safe resume markers.
- What it must not own:
  - queue timeout policy,
  - output writes.
- Sibling interaction:
  - inventory provides locators,
  - links resolve final unrestricted URLs after links become ready.
- What may cross this seam:
  - `ProviderResumeMarker`,
  - `AcquisitionStatus`.
- What may not cross this seam:
  - queue state or timer ownership.

### `realdebrid/links`

- Purpose: resolve provider-ready links for standard downloads and exact `.zip` container access after acquisition succeeds.
- What it owns:
  - unrestricted-link resolution,
  - exact `.zip` locator production.
- What it must not own:
  - archive enumeration,
  - queue state,
  - source filtering.
- Sibling interaction:
  - uses inventory and acquisition services.
- What may cross this seam:
  - `ResolvedDownloadUnit`,
  - `ArchiveContainerLocator`.
- What may not cross this seam:
  - raw HTTP responses.

## Internal Files

### `RealDebridFacade.kt`
- Internal area: `realdebrid/root`
- Purpose: expose the only public API for the rest of the app.
- Responsibility: delegate to auth, inventory, acquisition, and link services.
- Depends on: package-internal services only
- Must not depend on: queue stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
class RealDebridFacade(
    private val tokenService: TokenService,
    private val inventoryService: TorrentInventoryService,
    private val acquisitionPoller: ProviderAcquisitionPoller,
    private val linkResolver: UnrestrictedLinkResolver,
    private val exactZipResolver: ExactZipResolver
) {
    fun observeMaskedToken(): StateFlow<MaskedTokenState>
    fun observeTokenReadiness(): StateFlow<TokenReadiness>
    suspend fun readTokenReadiness(): TokenReadiness
    suspend fun saveValidatedToken(candidate: String): TokenSaveResult
    suspend fun enumerateProviderFiles(request: ProviderInventoryRequest): Result<ProviderInventory>
    suspend fun startAcquisition(locator: ProviderLocator): Result<AcquisitionStatus>
    suspend fun resumeAcquisition(marker: ProviderResumeMarker): Result<AcquisitionStatus>
    suspend fun resolveReadyLink(link: ProviderReadyLink): Result<ResolvedDownloadUnit>
    suspend fun resolveExactZip(request: ExactZipRequest): Result<ArchiveContainerLocator>
}
```

### `CredentialVault.kt`
- Internal area: `realdebrid/auth`
- Purpose: isolate encrypted token storage.
- Responsibility: persist and read only the Real-Debrid token.
- Depends on: encrypted storage boundary
- Must not depend on: provider HTTP client
- Visibility: `public`
- Key types/functions:

```kotlin
data class StoredTokenRecord(
    val encryptedValue: ByteArray,
    val savedAt: Instant
)

interface CredentialVault {
    suspend fun readToken(): StoredTokenRecord?
    suspend fun writeToken(token: String, savedAt: Instant): Result<Unit>
    suspend fun clearToken(): Result<Unit>
}
```

### `TokenService.kt`
- Internal area: `realdebrid/auth`
- Purpose: validate candidate tokens before saving and expose live token state.
- Responsibility: ensure masked-token and readiness flows hydrate from persisted storage on startup.
- Depends on: `CredentialVault`, auth HTTP client
- Must not depend on: queue stores or source models
- Visibility: `internal`
- Key types/functions:

```kotlin
data class MaskedTokenState(
    val maskedValue: String
)

data class TokenReadiness(
    val isUsable: Boolean,
    val brokenReason: String?
)

sealed interface TokenSaveResult {
    data object Saved : TokenSaveResult
    data class Rejected(val message: String) : TokenSaveResult
    data class Failed(val message: String) : TokenSaveResult
}

class TokenService(
    private val credentialVault: CredentialVault,
    private val authClient: RealDebridAuthClient,
    private val clock: Clock
) {
    fun observeMaskedToken(): StateFlow<MaskedTokenState>
    fun observeReadiness(): StateFlow<TokenReadiness>
    suspend fun readReadiness(): TokenReadiness

    suspend fun saveValidatedToken(candidate: String): TokenSaveResult {
        val validation = authClient.validateToken(candidate)
        if (validation.isFailure) return TokenSaveResult.Rejected("API key validation failed")

        return credentialVault.writeToken(candidate, clock.instant())
            .fold(
                onSuccess = { TokenSaveResult.Saved },
                onFailure = { TokenSaveResult.Failed(it.message ?: "Token save failed") }
            )
    }
}
```

### `RequestBudget.kt`
- Internal area: `realdebrid/budget`
- Purpose: serialize or delay provider requests when required.
- Responsibility: expose a simple permit boundary to higher-level services.
- Depends on: Kotlin coroutines, clock
- Must not depend on: queue semantics
- Visibility: `internal`
- Key types/functions:

```kotlin
class RequestBudget(
    private val clock: Clock
) {
    suspend fun <T> run(block: suspend () -> T): T
    fun recordRetryAfter(until: Instant)
}
```

### `ProviderModels.kt`
- Internal area: `realdebrid/shared`
- Purpose: define Romulus-shaped Real-Debrid models shared across the app.
- Responsibility: keep API payloads hidden behind stable internal app models.
- Depends on: Kotlin stdlib only
- Must not depend on: queue stores or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
data class ProviderSourceRef(
    val magnetUri: String,
    val partLabel: String?
)

data class ProviderInventoryRequest(
    val sources: List<ProviderSourceRef>
)

data class ProviderFileRecord(
    val providerFileId: String,
    val originalName: String,
    val path: String,
    val sizeBytes: Long?,
    val partLabel: String?,
    val locator: ProviderLocator
)

data class ProviderLocator(
    val sourceMagnetUri: String,
    val torrentId: String,
    val providerFileIds: List<String>,
    val selectedProviderFileId: String,
    val path: String,
    val partLabel: String?
)

data class ProviderInventory(
    val files: List<ProviderFileRecord>
)

data class ProviderResumeMarker(
    val torrentId: String,
    val sourceMagnetUri: String,
    val selectedProviderFileIds: List<String>
)

data class ProviderReadyLink(
    val providerFileId: String,
    val restrictedUrl: String
)

sealed interface AcquisitionStatus {
    data class Waiting(
        val statusLabel: String,
        val progressPercent: Double?,
        val resumeMarker: ProviderResumeMarker
    ) : AcquisitionStatus
    data class LinksReady(
        val resumeMarker: ProviderResumeMarker,
        val readyLinks: List<ProviderReadyLink>
    ) : AcquisitionStatus
}

data class ResolvedDownloadUnit(
    val downloadUrl: String,
    val originalName: String,
    val sizeBytes: Long?
)

data class ExactZipRequest(
    val sources: List<ProviderSourceRef>,
    val exactPath: String
)

data class ArchiveContainerLocator(
    val archiveUrl: String,
    val originalName: String,
    val providerLocator: ProviderLocator
)
```

### `TorrentInventoryService.kt`
- Internal area: `realdebrid/inventory`
- Purpose: enumerate provider files for one or more source torrents.
- Responsibility: follow the host-selection and torrent-registration flow proven by the spike.
- Inventory-built `ProviderLocator.providerFileIds` and `selectedProviderFileId` are queue-owned opaque selection ids derived deterministically from the provider file list, not raw Real-Debrid file ids. Fresh acquisition must re-derive those opaque ids from the new torrent before selecting provider-side file ids.
- Depends on: HTTP client boundary, `RequestBudget`
- Must not depend on: queue stores
- Visibility: `internal`
- Key types/functions:

```kotlin
class TorrentInventoryService(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun enumerate(request: ProviderInventoryRequest): Result<ProviderInventory>
}
```

### `ProviderSelectionService.kt`
- Internal area: `realdebrid/acquisition`
- Purpose: select the correct provider file set for one queue row.
- Responsibility: start or verify provider-side selection for the exact locator passed in from source or queue state.
- Selection must fail explicitly when the queue-owned locator cannot be re-derived from the fresh torrent; it must not fall back to matching by path alone.
- Depends on: `RequestBudget`, `RealDebridApi`
- Must not depend on: queue stores
- Visibility: `internal`
- Key types/functions:

```kotlin
class ProviderSelectionService(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun start(locator: ProviderLocator): Result<ProviderResumeMarker>
    suspend fun verify(marker: ProviderResumeMarker): Result<ProviderResumeMarker>
}
```

### `ProviderAcquisitionPoller.kt`
- Internal area: `realdebrid/acquisition`
- Purpose: own provider-side acquisition start, resume, and polling.
- Responsibility: emit waiting updates with resume markers until provider links are ready for later unrestricted-link resolution.
- Depends on: `ProviderSelectionService`, `RequestBudget`, `RealDebridApi`
- Must not depend on: queue timeout ownership
- Visibility: `internal`
- Key types/functions:

```kotlin
class ProviderAcquisitionPoller(
    private val selectionService: ProviderSelectionService,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun start(locator: ProviderLocator): Result<AcquisitionStatus>
    suspend fun resume(marker: ProviderResumeMarker): Result<AcquisitionStatus>
}
```

### `UnrestrictedLinkResolver.kt`
- Internal area: `realdebrid/links`
- Purpose: convert one provider-ready link into a final unrestricted download unit.
- Responsibility: keep unrestricted-link resolution as a later step after acquisition, not a hidden side effect of polling.
- Depends on: `RequestBudget`, `RealDebridApi`
- Must not depend on: queue stores or output services
- Visibility: `internal`
- Key types/functions:

```kotlin
class UnrestrictedLinkResolver(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun resolve(link: ProviderReadyLink): Result<ResolvedDownloadUnit>
}
```

### `ExactZipResolver.kt`
- Internal area: `realdebrid/links`
- Purpose: resolve the exact provider file that backs archive-selection mode.
- Responsibility: reuse inventory and acquisition stages, then return an outer `.zip` locator without enumerating the archive itself.
- Depends on: `TorrentInventoryService`, `ProviderAcquisitionPoller`, `RequestBudget`, `RealDebridApi`
- Must not depend on: `remotezip/`
- Visibility: `internal`
- Key types/functions:

```kotlin
class ExactZipResolver(
    private val inventoryService: TorrentInventoryService,
    private val acquisitionPoller: ProviderAcquisitionPoller,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun resolve(request: ExactZipRequest): Result<ArchiveContainerLocator>
}
```

## Key Flows

1. Token save:
   - `TokenService.saveValidatedToken(candidate)` validates the candidate token before saving it.
   - Only a successfully validated candidate replaces the prior saved token.
   - Masked-token and token-readiness flows hydrate from persisted storage on startup.

2. Provider inventory:
   - `TorrentInventoryService` iterates `ProviderSourceRef` values.
   - For each source it performs host selection, torrent registration, and file enumeration.
   - It aggregates files into one `ProviderInventory` without leaking raw API models.

3. Standard-file resolution:
   - `downloads/` passes a `ProviderLocator` captured at enqueue time.
   - `ProviderSelectionService` verifies or starts selection for that locator.
   - `ProviderAcquisitionPoller` is called through `startAcquisition(...)` or `resumeAcquisition(...)` and emits waiting updates until provider links are ready.
   - `downloads/attempts` persists the waiting status, progress, and resume marker while it owns `Preparing`.
   - Once links are ready, `downloads/attempts` selects the matching `ProviderReadyLink` and calls `resolveReadyLink(...)`.

4. Exact `.zip` resolution:
   - `source/` requests `ExactZipRequest`.
   - `ExactZipResolver` enumerates provider files, finds the exact matching `.zip`, runs acquisition if needed, and returns `ArchiveContainerLocator`.
   - `remotezip/` later enumerates or copies the archive; `realdebrid/` stops at providing the container URL and locator.

## Failure and Recovery Rules

- Invalid token candidates do not replace the previously saved valid token.
- Missing or broken saved token is exposed through `TokenReadiness`; `realdebrid/` does not route the UI itself.
- `TokenReadiness` is the auth-readiness input consumed by `downloads/work` to stop or resume new provider-dependent queue claims; `realdebrid/` reports readiness but does not own queue gating policy.
- Provider-acquisition resume markers are opaque to other packages.
- `realdebrid/` does not own the 24-hour `Preparing` timeout; it exposes waiting updates, ready-link results, and resume markers while `downloads/` owns the timeout window.
- Raw API model changes must be absorbed inside this package and must not leak across the boundary.

## Testing Plan

### `TokenServiceTest.kt`

- Scope: unit
- Covers:
  - candidate token validated before save
  - invalid candidate leaves prior token unchanged
  - masked-token state hydrates from stored token
  - token-readiness reports broken auth cleanly
- Fixtures:
  - fake `CredentialVault`
  - fake auth client
  - test clock

### `RequestBudgetTest.kt`

- Scope: unit
- Covers:
  - request serialization
  - retry-after delay handling
- Fixtures:
  - test clock

### `TorrentInventoryServiceTest.kt`

- Scope: unit
- Covers:
  - multi-source inventory aggregation
  - provider locator construction
  - host-selection flow
  - part-label preservation
- Fixtures:
  - fake `RealDebridApi`
  - fake `RequestBudget`

### `ProviderSelectionServiceTest.kt`

- Scope: unit
- Covers:
  - selection start
  - selection verify
  - selected provider-file ids remain stable
- Fixtures:
  - fake `RealDebridApi`
  - fake `RequestBudget`

### `ProviderAcquisitionPollerTest.kt`

- Scope: unit
- Covers:
  - waiting updates emit progress and resume markers
  - links-ready result returns provider-ready links without silently unrestricting them
  - resume continues from saved marker
- Fixtures:
  - fake `ProviderSelectionService`
  - fake `RealDebridApi`
  - fake `RequestBudget`

### `UnrestrictedLinkResolverTest.kt`

- Scope: unit
- Covers:
  - provider-ready link resolves to one download unit
  - failure propagation from unrestricted-link resolution
- Fixtures:
  - fake `RealDebridApi`
  - fake `RequestBudget`

### `ExactZipResolverTest.kt`

- Scope: unit
- Covers:
  - exact `.zip` match selection
  - acquisition for archive container
  - archive-container locator construction
  - exact-path-not-found failure
- Fixtures:
  - fake `TorrentInventoryService`
  - fake `ProviderAcquisitionPoller`
  - fake `RealDebridApi`

## Open Questions or Deferred Decisions

None currently.
