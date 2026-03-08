# Archive Selection Behavior

## Trigger

1. User opens Home Files page for a source entry.
2. Entry `path` is a normalized exact file path to a `.zip` file inside that entry's torrent file set; path normalization and validity rules follow [`source.md`](source.md).

## Expected Result

1. Files page enters archive-selection mode for that entry.
2. Archive-selection activation uses source path-normalization and path-validity rules from [`source.md`](source.md).
3. App resolves a Real-Debrid downloadable link for the configured `.zip` file.
4. App remotely enumerates files inside the `.zip` and renders those internal files as selectable rows.
5. Outer `.zip` container is not shown as a selectable download row in this mode.
6. Ignore glob rules still apply case-insensitively to internal-file basenames before rows are shown.
7. Search, multi-select, select all or none, and queue confirmation behavior remain the same as [`files.md`](files.md).
8. Queue payload captures selected internal-file identity plus naming and archive intents.
9. Rename behavior for selected internal files follows [`naming.md`](naming.md).
10. Unarchive and recursive unarchive behavior for selected internal files follows [`downloads.md`](downloads.md).
11. When diagnostics is enabled, archive-selection events are captured:
   - configured archive link resolution start and outcome,
   - internal-file enumeration start and outcome,
   - `Retry` action.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. If configured `.zip` path is not found, Files shows a resolver failure state with `Retry`.
2. If `.zip` link resolution fails, Files shows a resolver failure state with `Retry`.
3. If internal-file enumeration fails, Files shows a resolver failure state with `Retry`.
4. Archive-selection failures do not silently fall back to full torrent-file enumeration.
