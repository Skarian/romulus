# `diagnostics/` Package Architecture

## Purpose

`diagnostics/` owns diagnostics enablement, append-only event capture, retained internal artifacts, export bundles, and clear actions.
It is a sink and reporting surface only; product decisions must not depend on diagnostics content.

## Responsibilities

- Persist and expose the diagnostics-enabled setting.
- Accept typed diagnostics events from all supported domains.
- Append diagnostics events and failure records while diagnostics is enabled.
- Maintain `manifest.json`, `timeline.jsonl`, `failures.jsonl`, and `summary.json`.
- Rotate retained artifacts oldest-first once storage exceeds the internal limit.
- Export timestamped diagnostics bundles to a user-selected destination.
- Clear retained internal diagnostics artifacts.

## Boundaries

- `diagnostics/` does not own queue state, source state, or non-diagnostics settings.
- `diagnostics/` does not drive retries, routing, or UI lock decisions.
- `diagnostics/` does not own notification delivery.
- `diagnostics/` must not persist plaintext credentials or raw sensitive tokens.

## Public Seams

- Diagnostics settings observation and enablement updates.
- Best-effort event emission.
- Export and clear actions.

## Durable Invariants

1. Enablement changes take effect immediately and persist.
2. Event capture is append-only while diagnostics is enabled.
3. Internal retention stays bounded to the configured size cap.
4. Export writes one timestamped bundle to a user-chosen location.
5. Clear and export must never partially report success.
6. Diagnostics failure must not block product behavior.

## Key Flows

### Event Capture

1. Product packages emit best-effort diagnostics events.
2. Events are redacted and appended only when diagnostics is enabled.
3. Summary and failure artifacts stay aligned with retained events.

### Export and Clear

1. Settings asks `diagnostics/` to export or clear.
2. Export writes a timestamped zip bundle to the chosen destination.
3. Clear affects only retained internal artifacts; previously exported bundles remain user-owned.
