# `realdebrid/` Package Architecture

## Purpose

`realdebrid/` owns encrypted API-token access and all Real-Debrid protocol behavior.
It exposes app-shaped models for token readiness, provider inventory, acquisition status, and resolved download units.

## Responsibilities

- Store, read, and mask the encrypted Real-Debrid token.
- Validate candidate tokens before saving them.
- Expose token-readiness state to startup and Settings.
- Budget requests so app-side concurrency does not oversubscribe the provider.
- Enumerate provider inventory for cached standard browse fills and exact `.zip` lookup.
- Select the correct provider file for standard queue work or exact-container lookup.
- Start and resume provider acquisition polling until links are ready.
- Resolve provider-ready links into final download units for the app.

## Boundaries

- `realdebrid/` does not own queue rows, retry policy, or queue state.
- `realdebrid/` does not own source snapshots or browse filtering.
- `realdebrid/` does not own output naming or local file I/O.
- `realdebrid/` does not own remote ZIP enumeration.
- `realdebrid/` reports token readiness; it does not decide UI lock policy on its own.

## Public Seams

- Masked token and token-readiness observation.
- Validated token save.
- Standard-file acquisition start or resume.
- Exact-container lookup for archive selection.
- Ready-link resolution into download units.

## Durable Invariants

1. Token validation is separate from token persistence.
2. Exact-zip lookup reuses the same inventory and acquisition model as other provider work.
3. Acquisition polling is resumable without resetting the owning row's `Preparing` deadline.
4. Raw provider DTOs stay inside this package.

## Key Flows

### Token Save

1. Validate the candidate token against the provider.
2. Save it only if validation succeeds.
3. Publish masked-token and readiness updates.

### Inventory and Exact ZIP Lookup

1. Enumerate provider inventory from source-owned torrent references.
2. Return browse-fill data for standard mode or an exact-container locator for archive-selection mode.

### Acquisition and Link Resolution

1. Start or resume provider acquisition for one selection request.
2. Surface waiting updates while acquisition is still preparing.
3. When provider links are ready, resolve them into final download units.
