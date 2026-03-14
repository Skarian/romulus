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
6. Concurrency is adjusted with a discrete slider.
7. Concurrency must be between `1` and `50`.
8. Fresh setup defaults concurrency to `5`.
9. Concurrency means the maximum number of simultaneous active queue attempts; when one active attempt finishes, the next queued attempt may start immediately without waiting for the rest of the active set to finish.
10. Source mode supports URL and file.
11. Settings does not expose manual source refresh.
12. `Active downloads` means active states from [`status-model.md`](status-model.md).
13. When Home is in the invalid-settings error state, Settings is the place where the user fixes the broken saved setting; shell visibility behavior follows [`app-shell.md`](app-shell.md).
14. Settings includes `Diagnostics` controls:
   - `Enable diagnostics` toggle,
   - `Clear diagnostics` action,
   - `Export` action.
15. `Enable diagnostics` takes effect immediately and persists as a settings value; persistence rules follow [`persistence.md`](persistence.md).
16. `Clear diagnostics` requires confirmation before changes are applied.
17. Diagnostics actions remain available while downloads are active.
18. `Clear diagnostics` and `Export` show toast feedback on success or failure.
19. `Export` opens the Android system document picker so the user chooses where the diagnostics zip will be saved.
20. Diagnostics data, retention, and export semantics follow [`diagnostics.md`](diagnostics.md).
21. Saving an API key performs live Real-Debrid validation.
22. API key save succeeds only when the new key validates successfully and replaces the prior saved key.
23. If API key validation fails, the previously saved valid key remains unchanged.
24. Cancelling source reselection, download-directory reselection, or diagnostics export destination selection leaves the previously saved values and retained diagnostics artifacts unchanged.
25. When diagnostics is enabled, Settings events are captured:
   - settings edit and save attempts,
   - settings save outcomes,
   - diagnostics control actions and outcomes.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Invalid edits show clear corrective feedback.
2. Settings screen remains usable for fixing broken saved settings without forcing setup reset.
3. Failed diagnostics actions keep settings usable and do not partially report success.
