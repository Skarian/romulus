# Setup Behavior

## Trigger

1. First launch before first successful setup completion.

## Expected Result

1. Before first successful setup completion, app stays setup-gated until required configuration is valid and saved.
2. Setup is a single screen.
3. Required configuration includes:
   - RealDebrid API key,
   - source (URL or local file),
   - download directory.
4. API key must validate successfully with Real-Debrid before setup can complete.
5. Source must be validated and loaded successfully before setup can complete; source rules follow [`source.md`](source.md).
6. Source validation is all-or-nothing for setup completion:
   - fully valid source is accepted,
   - any invalid source is rejected.
7. Setup does not complete until the selected source is readable and the selected download directory is writable with currently valid access.
8. Cancelling source-file selection or download-directory selection leaves Setup open and keeps the current value unchanged.
9. Setup completion unlocks Home, Downloads, and Settings; shell behavior follows [`app-shell.md`](app-shell.md).
10. After setup has completed once, later breakage of a saved API key, source, or download directory does not reopen Setup; post-setup recovery follows [`app-shell.md`](app-shell.md), [`home.md`](home.md), and [`settings.md`](settings.md).
11. All three required inputs are persisted so the user does not re-enter setup on next launch when configuration remains valid; persistence rules follow [`persistence.md`](persistence.md).
## Failure Behavior

1. Invalid API key, source, or download directory shows clear corrective feedback.
2. Failed API key validation does not save the attempted key.
3. Invalid or missing download directory blocks completion and shows clear corrective feedback.
