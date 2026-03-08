# PRD - Romulus MVP (Behavior-First)

## 1. Product Promise

Romulus is a simple Android app that lets a user:
1. connect Real-Debrid,
2. load a JSON source catalog,
3. pick files,
4. queue downloads,
5. track outcomes and recover from failures.

This PRD prioritizes behavior parity with simpler architecture and simpler implementation.

## 2. Core User Jobs

1. Complete setup once and recover quickly if settings become invalid.
2. Browse source entries and pick the right files fast.
3. Start downloads that keep working in the background.
4. Understand current status at a glance and take the next action.

## 3. Must-Keep User Outcomes

1. Setup gate before normal app use.
2. Post-setup navigation includes Home, Downloads, and Settings.
3. Source supports URL and local file modes with strict, predictable ingest behavior.
4. Setup and settings stay intentionally minimal, with guardrails during active download activity.
5. File selection supports search, multi-select, select all, and select none.
6. Queue behavior is predictable and user-controllable, with consistent recovery actions.
7. Downloads include history management so users can clear past results from the default list without deleting underlying app data.
8. Downloads persist across process death.
9. Notifications show active progress and final run outcome.
10. Browsing and tracking preserve recognizable file identity while still supporting output naming adjustments at save time.
11. Credentials are user-provided and protected at rest.
12. Source entries can optionally request archive extraction behavior, including recursive extraction when enabled, so compatible downloads are expanded for easier direct consumption.
13. Source entries can optionally use exact `.zip` path mode so users can browse zip internals remotely and select only the files they want before queueing.

## 4. Simplify-First Rules

1. Preserve outcomes, not implementation details.
2. Keep one primary runtime path for each responsibility.
3. Remove fallback branches unless they are needed for user-visible outcomes.
4. Prefer explicit, human-readable module names over technical layering jargon.
5. If a behavior is not in [`BEHAVIOR.md`](BEHAVIOR.md) or `behavior/*`, it is allowed to simplify.

## 5. Scope Boundary

In scope:
1. Setup, source ingest, file selection, queue execution, notifications, and settings governance.
2. Source input contract in [`schema.json`](schema.json).

Out of scope:
1. Multi-account support.
2. Cross-device sync.
3. Non-Android targets.
4. Plugin systems or feature-flag frameworks.

## 6. Success Criteria

1. Behavior contracts are clear enough to guide a rip-and-replace migration.
2. Architecture can be explained from one diagram and a small set of docs.
3. New engineers can find behavior intent without reading app internals first.
4. Migration decisions can be judged against user outcomes, not legacy implementation.

## 7. PRD vs Behavior Specs

1. This PRD defines product intent and high-level outcomes.
2. [`BEHAVIOR.md`](BEHAVIOR.md) and `behavior/*` define precise behavior contracts for migration parity.
3. Implementation-level behavior decisions should be taken from the behavior specs, not inferred from PRD wording.

## 8. Document Map

1. [`BEHAVIOR.md`](BEHAVIOR.md): behavior contract index.
2. [`ARCHITECTURE.md`](ARCHITECTURE.md): simplified runtime design.
3. [`MIGRATION.md`](MIGRATION.md): migration phases and exit gates.
4. [`behavior/README.md`](behavior/README.md): per-area and cross-cutting behavior specs.
5. [`behavior/app-shell.md`](behavior/app-shell.md): app-level navigation and gating contract.
6. [`behavior/status-model.md`](behavior/status-model.md): download status and action semantics.
7. [`behavior/naming.md`](behavior/naming.md): naming and rename behavior contract.
8. [`behavior/persistence.md`](behavior/persistence.md): persistence and recovery behavior contract.
9. [`behavior/archive-selection.md`](behavior/archive-selection.md): exact-path zip internal selection behavior contract.
