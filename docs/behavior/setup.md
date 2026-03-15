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
12. Setup content stays clear of the Android status bar and uses the same safe top spacing as the rest of the app shell.
13. Setup visually separates:
   - the title and description,
   - the API key section,
   - the source section,
   - the download-directory section,
   - the final completion action.
14. Setup includes a visible helper action that opens `https://real-debrid.com/apitoken` for retrieving the Real-Debrid API token.
15. `Complete setup` is visually distinct from the other action buttons, centered, and disabled until API key, source, and download directory have all been provided locally.
## Failure Behavior

1. Invalid API key, source, or download directory shows clear corrective feedback.
2. Failed API key validation does not save the attempted key.
3. Invalid or missing download directory blocks completion and shows clear corrective feedback.
