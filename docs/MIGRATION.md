# MIGRATION - Behavior-First Rip and Replace

## 1. Migration Goal

Rebuild toward the architecture in [`ARCHITECTURE.md`](ARCHITECTURE.md) while preserving behavior in [`BEHAVIOR.md`](BEHAVIOR.md).

## 2. Principles

1. Preserve user outcomes, not old internals.
2. Keep only one primary runtime path per responsibility.
3. Remove complexity unless it protects a required behavior.

## 3. Phases

### Phase 1 - Freeze Behavior Contracts

1. Finalize `behavior/*` contracts and `schema.json`.
2. Confirm diagram and docs are lock-step.
3. Confirm cross-cutting specs are complete:
   - `app-shell.md`,
   - `status-model.md`,
   - `naming.md`,
   - `persistence.md`.
4. Mark anything not in contracts as simplifiable.

### Phase 2 - Stand Up Simple Runtime Seams

1. Make explicit seams for App Flow, Source Pipeline, and Download Pipeline.
2. Keep Settings Store and Task Store as state authorities.
3. Route all external calls through adapters.

### Phase 3 - Collapse Legacy Complexity

1. Remove duplicate orchestration paths.
2. Remove policy from adapters and stores.
3. Keep only behavior-preserving branches.
4. Validate each behavior change against `BEHAVIOR.md` and `behavior/*`.

### Phase 4 - Verify Behavior Parity

1. Validate each behavior area from `behavior/*`.
2. Confirm source contract compatibility against `schema.json`.
3. Resolve mismatches with behavior-first decisions.

## 4. Exit Criteria

1. One canonical diagram explains the runtime.
2. Behavior contracts are complete and unambiguous.
3. Architecture is navigable by humans without legacy context.
4. No extra complexity remains without a behavior justification.
5. Cross-cutting behavior specs are linked and used by page-level specs.
