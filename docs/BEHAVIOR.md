# BEHAVIOR

This document indexes the user-visible behavior contract for the shipped app.
It stays implementation-agnostic and should be updated whenever runtime behavior changes materially.

## Contract Rules

1. Behavior docs define user-visible outcomes, not internal code shape.
2. Preserve behavior parity, not system parity.
3. Internal timings, caching, storage, and algorithms may change unless they affect a user-visible outcome.
4. Source JSON shape lives in [`schema.json`](schema.json); runtime handling of valid or invalid input lives in [`behavior/source.md`](behavior/source.md).
5. If implementation changes user-visible behavior, update these docs in the same work.

## Behavior Spec Set

1. [`behavior/setup.md`](behavior/setup.md)
2. [`behavior/source.md`](behavior/source.md)
3. [`behavior/home.md`](behavior/home.md)
4. [`behavior/files.md`](behavior/files.md)
5. [`behavior/archive-selection.md`](behavior/archive-selection.md)
6. [`behavior/downloads.md`](behavior/downloads.md)
7. [`behavior/settings.md`](behavior/settings.md)
8. [`behavior/notifications.md`](behavior/notifications.md)
9. [`behavior/security.md`](behavior/security.md)
10. [`behavior/diagnostics.md`](behavior/diagnostics.md)
11. [`behavior/app-shell.md`](behavior/app-shell.md)
12. [`behavior/status-model.md`](behavior/status-model.md)
13. [`behavior/naming.md`](behavior/naming.md)
14. [`behavior/persistence.md`](behavior/persistence.md)

## Stable Boundaries

1. Setup gating remains mandatory.
2. Queue state remains durable across process death.
3. Downloads retain recoverability actions for visible tasks; user-confirmed history clear may hide terminal tasks from the default list.
4. Credential handling remains protected.
5. Diagnostics capture, notification copy, naming, and persistence semantics are defined by their dedicated behavior docs.

## Historical Context

Migration planning and cutover history now live in [`MIGRATION.md`](MIGRATION.md), archived ExecPlans, and git history.
This file and the specs under [`behavior/`](behavior/README.md) describe current behavior.
