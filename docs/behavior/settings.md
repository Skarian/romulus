# Settings Behavior

## Trigger

1. User opens Settings.
2. User edits credentials, source, storage, or concurrency.
3. User manages diagnostics controls.

## Expected Result

1. With no active downloads, all settings are editable.
2. With active downloads and all saved required settings usable, configuration fields are locked.
3. With active downloads and a broken saved setting, only the affected field remains editable:
   - API key when the saved key no longer authenticates,
   - source when the saved source file is no longer readable,
   - download directory when the saved directory is no longer writable.
4. While active downloads continue, all unaffected settings remain locked.
5. Successful settings saves show a toast confirmation for:
   - API key save,
   - source save,
   - directory save,
   - concurrency save.
6. Concurrency must be between `1` and `5`.
7. Source mode supports URL and file.
8. Settings does not expose manual source refresh.
9. `Active downloads` means active states from [`status-model.md`](status-model.md).
10. When Home is in the invalid-settings error state, Settings is the place where the user fixes the broken saved setting; shell visibility behavior follows [`app-shell.md`](app-shell.md).
11. Settings includes `Diagnostics` controls:
   - `Enable diagnostics` toggle,
   - `Clear diagnostics` action,
   - `Export diagnostics` action.
12. `Enable diagnostics` takes effect immediately and persists as a settings value; persistence rules follow [`persistence.md`](persistence.md).
13. `Clear diagnostics` requires confirmation before changes are applied.
14. Diagnostics actions remain available while downloads are active.
15. `Clear diagnostics` and `Export diagnostics` show toast feedback on success or failure.
16. Diagnostics data, retention, and export semantics follow [`diagnostics.md`](diagnostics.md).
17. Saving an API key performs live Real-Debrid validation.
18. API key save succeeds only when the new key validates successfully and replaces the prior saved key.
19. If API key validation fails, the previously saved valid key remains unchanged.
20. Cancelling source reselection or download-directory reselection leaves the previously saved value unchanged.
21. When diagnostics is enabled, Settings events are captured:
   - settings edit and save attempts,
   - settings save outcomes,
   - diagnostics control actions and outcomes.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Invalid edits show clear corrective feedback.
2. Settings screen remains usable for fixing broken saved settings without forcing setup reset.
3. Failed diagnostics actions keep settings usable and do not partially report success.
