# Home Files Page Behavior

## Trigger

1. User taps a source row on the Home source page after setup.

## Expected Result

1. On page open, app shows resolving or loading state until file resolution finishes.
2. Files page chooses one resolution mode from entry `path`:
   - standard mode for directory or root scope,
   - archive-selection mode for an exact `.zip` file path, as defined in [`archive-selection.md`](archive-selection.md).
3. In standard mode, file list resolves from Real-Debrid metadata across all torrents in the selected entry.
4. Android recreation caused by a configuration change does not trigger a new resolver run; resolver rerun happens only on page open for a source row or explicit user `Retry`.
5. Android recreation caused by a configuration change preserves:
   - the current source entry,
   - the current resolution mode,
   - the resolved file rows or current resolver failure state,
   - the search query and filtered view,
   - the selected rows,
   - the current `Apply rename`, `Unarchive`, and `Recursive unarchive` toggle state,
   - the current list position.
6. In standard mode, files are included only when:
   - file path is inside entry `path` scope,
   - file basename does not match ignore glob rules (case-insensitive).
7. Standard-mode filtering order is path scope first, then ignore rules; source-validation rules for these fields follow [`source.md`](source.md).
8. File rows are sorted alphabetically by original file name in both modes:
   - standard mode uses torrent file names,
   - archive-selection mode uses internal zip file names.
9. File rows display original file names.
10. Search is a dialog with:
   - a search box,
   - `Done` action,
   - `Clear` action.
11. Search is case-insensitive and matches original file name only.
12. Empty search query shows all files.
13. If resolution succeeds but zero rows are visible after path, ignore, archive-selection, and search filtering, Files shows `No files available` empty state.
14. Files page provides a `File preferences` dialog.
15. `Apply rename` appears inside `File preferences` only when `entries[i].rename` exists with valid `pattern` and `replacement` fields.
16. When shown, `Apply rename` initial toggle state is enabled.
17. User can toggle `Apply rename` for the current Files page before queueing; this does not rewrite source JSON.
18. `Unarchive` appears inside `File preferences` only when `entries[i].unarchive` key is present (even when its value is `false`).
19. When shown, `Unarchive` initial toggle state equals `entries[i].unarchive`.
20. User can toggle `Unarchive` for the current Files page before queueing; this does not rewrite source JSON.
21. `Recursive unarchive` appears inside `File preferences` only when `entries[i].recursiveUnarchive` key is present (even when its value is `false`).
22. When shown, `Recursive unarchive` is enabled only while `Unarchive` is enabled for the current Files page.
23. When `Unarchive` is enabled, `Recursive unarchive` initial toggle state equals `entries[i].recursiveUnarchive`.
24. When `Unarchive` is disabled, `Recursive unarchive` shows as off and non-interactive.
25. User can toggle `Recursive unarchive` for the current Files page before queueing only while `Unarchive` is enabled; this does not rewrite source JSON.
26. Queue payload normalizes `recursive-unarchive intent` to `false` whenever `unarchive intent` is `false`.
27. Multi-select is supported.
28. Select all and select none operate on visible rows only.
29. Download action is disabled until at least one file is selected.
30. File details dialog shows:
   - original file name,
   - file size,
   - source context (`part label` and `torrent file id` when available).
31. Queue payload preserves download intent fields:
   - snapshot identity,
   - entry identity,
   - file identity,
   - naming intent,
   - unarchive intent,
   - recursive-unarchive intent,
   - storage target context,
   - source-derived execution context needed for later execution, retry, restart, and recovery without rebinding to a newer active snapshot.
32. Starting downloads shows confirmation and routes user to Downloads.
33. Naming and rename semantics follow [`naming.md`](naming.md).
34. Post-download unarchive behavior and archive handling follow [`downloads.md`](downloads.md).
35. When diagnostics is enabled, Files events are captured:
   - file resolution start and outcome,
   - `Retry` action,
   - selection and select-all or select-none actions,
   - queue-start action with selected count and active preferences.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Resolver failures show warning details without crashing the page.
2. Failure state includes a visible `Retry` action to re-run file resolution.
