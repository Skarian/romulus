# Source Behavior

## Trigger

1. User saves source during Setup:
   - URL source, or
   - local JSON file source.
2. User updates source in Settings:
   - save source URL, or
   - pick local source file.
3. User requests manual refresh from Home source page (URL mode only).
4. App cold-launches after setup with URL source configured.

## Expected Result

1. App has exactly one active source configuration at a time (URL or file).
2. URL source must start with `http://` or `https://`.
3. Source input is validated against `version: 1` contract ([`schema.json`](../schema.json) + runtime validation).
4. Source scope and ignore rules are validated as part of source acceptance:
   - `scope` omitted defaults to shallow root scope (`{ "path": "/", "includeNestedFiles": false }`),
   - `scope.path` is required when `scope` exists,
   - non-root directory `scope.path` values must end in `/`,
   - `scope.path` may represent directory scope or an exact `.zip` file path,
   - exact `.zip` `scope.path` enables archive-selection mode on Files page, as defined in [`archive-selection.md`](archive-selection.md),
   - exact non-`.zip` file paths reject source,
   - invalid `scope.path` (blank, contains `..` or backslashes, or uses a non-root directory path without a trailing `/`) rejects source,
   - `scope.includeNestedFiles` is optional and defaults to `false`,
   - exact `.zip` `scope.path` with `scope.includeNestedFiles: true` rejects source,
   - ignore glob matching is case-insensitive against file basename,
   - invalid ignore rules reject source.
5. `unarchive` is optional and must be an object when present:
   - if omitted, the entry exposes no unarchive controls on Files,
   - if present, the entry exposes unarchive controls on Files and the main `Unarchive` toggle defaults on.
6. When `unarchive` is present:
   - `layout` is required,
   - `layout.mode` must be `flat` or `dedicatedFolder`,
   - `recursive` is optional and defaults to `false`,
   - `layout.rename` is allowed only when `layout.mode` is `dedicatedFolder`,
   - invalid rename regex in either entry `rename` or `unarchive.layout.rename` rejects source.
7. Source validity is all-or-nothing:
   - fully valid source is accepted,
   - any invalid source is rejected.
8. Successful source load or refresh creates a new active snapshot and replaces the prior snapshot.
9. URL cold-launch refresh runs once per cold launch when URL source is configured.
10. Queued downloads bind to immutable snapshot identity captured at enqueue time.
11. Queued downloads continue to use the source-derived execution context captured at enqueue time for execution, retry, restart, and recovery even if the active source snapshot later changes.
12. Files-page inclusion behavior using `scope` and ignore rules follows [`files.md`](files.md).
13. Archive-selection activation and behavior for exact `.zip` `scope.path` values follows [`archive-selection.md`](archive-selection.md).
14. When diagnostics is enabled, source set, update, and refresh triggers and outcomes are captured:
   - accepted,
   - rejected,
   - fallback to prior snapshot,
   - error without usable snapshot.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. If URL-source refresh or cold-launch load fails and a prior snapshot exists, app keeps using the prior snapshot and Home shows a visible warning that the source list may be stale.
2. If source save or load fails during Setup or Settings, the current screen shows clear corrective feedback and does not commit the attempted source change.
3. If URL-source refresh or cold-launch load fails after setup without a usable snapshot, Home shows the source-load error state with a visible `Retry`; Home behavior follows [`home.md`](home.md).
4. If active snapshot content is missing or unreadable, app falls back to an empty source and surfaces a warning on Home; durability semantics follow [`persistence.md`](persistence.md).
5. Failed source update must not mutate an already valid active source.
6. If an exact `.zip` path is structurally valid but cannot be resolved at Files time, app surfaces Files-page resolver failure behavior defined in [`archive-selection.md`](archive-selection.md).
7. If the saved local source file is no longer readable, Home shows the invalid-settings error state instead of source content and directs the user to Settings.
