# Archive Selection Behavior

## Trigger

1. User opens Home Files page for a source entry.
2. Entry `scope.path` is a normalized exact file path to a `.zip` file inside that entry's torrent file set; scope normalization and validity rules follow [`source.md`](source.md).

## Expected Result

1. Files page enters archive-selection mode for that entry.
2. Archive-selection activation uses source scope normalization and path-validity rules from [`source.md`](source.md).
3. On the first open for a snapshot entry, app resolves the exact outer `.zip` file in Real-Debrid and starts provider preparation for that one container.
4. While the outer `.zip` is still preparing in Real-Debrid, Files shows a centered preparing state instead of failing immediately.
5. The preparing state uses plain-language copy, progress when available, and does not flash a generic loading message between polls.
6. Archive-preparation state is stored locally per snapshot entry, so revisiting the same source resumes the same Real-Debrid torrent instead of starting over.
7. Archive preparation may wait up to 24 hours before timing out.
8. Once the outer `.zip` is ready, app resolves an unrestricted container link, remotely enumerates files inside the `.zip`, and renders those internal files as selectable rows.
9. Outer `.zip` container is not shown as a selectable download row in this mode.
10. Ignore glob rules still apply case-insensitively to internal-file basenames before rows are shown.
11. Search, multi-select, select all or none, and queue confirmation behavior remain the same as [`files.md`](files.md).
12. Queue payload captures selected internal-file identity plus naming and archive intents.
13. Rename behavior for selected internal files follows [`naming.md`](naming.md).
14. Unarchive and recursive unarchive behavior for selected internal files follows [`downloads.md`](downloads.md).
15. When diagnostics is enabled, archive-selection events are captured:
   - configured archive link resolution start and outcome,
   - internal-file enumeration start and outcome,
   - `Retry` action.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. If configured `.zip` path is not found, Files shows a resolver failure state with `Retry`.
2. If provider preparation is still in progress, Files stays in archive-preparing state and does not create a duplicate Real-Debrid torrent for that same snapshot entry.
3. If provider preparation exceeds 24 hours, Files shows a resolver failure state with `Retry`.
4. If `.zip` link resolution fails after provider preparation completed, Files shows a resolver failure state with `Retry`.
5. If internal-file enumeration fails, Files shows a resolver failure state with `Retry`.
6. Archive-selection failures do not silently fall back to full torrent-file enumeration.
