# Home Source Page Behavior

## Trigger

1. User opens the Home source page after setup.
2. User presses `Home` in the bottom navbar after setup.
3. Device configuration changes (including orientation) while Home is visible.

## Expected Result

1. If any previously saved required setting is unusable, Home does not show the normal source list, empty state, or refresh action.
2. In that case, Home shows only an error state that:
   - names the broken setting or settings,
   - states that normal source or download behavior cannot continue,
   - directs the user to the `Settings` tab.
3. If all previously saved required settings are usable but URL-source loading or refresh has no usable snapshot, Home shows only a source-load error state that:
   - names the current source-load failure,
   - provides a visible `Retry` action,
   - hides the normal source list and empty-state copy until a usable snapshot exists.
4. If all previously saved required settings are usable and a usable snapshot exists, Home shows source entries from the active snapshot in the same order as that snapshot's source JSON.
5. Row source names match JSON `displayName` values exactly.
6. Duplicate names are shown as-is with no automatic disambiguation.
7. Search is a dialog with:
   - a search box,
   - `Done` action,
   - `Clear` action.
8. Search is case-insensitive and filters by source name only.
9. Empty search query shows all rows.
10. URL mode shows refresh action; local-file mode does not.
11. Manual refresh result uses toast feedback:
   - success toast when refresh updates source snapshot,
   - failure toast when refresh fails.
   - refresh trigger and snapshot rules follow [`source.md`](source.md).
12. If the latest URL refresh or cold-launch load fails but a prior usable snapshot remains available, Home keeps showing that prior snapshot and also shows a persistent visible warning banner that the latest source update failed and the current source list may be stale.
13. Configuration changes by themselves do not trigger source set, source update, or source refresh; source triggers remain only those defined in [`source.md`](source.md).
14. If a usable snapshot has no entries, Home shows `No sources available` empty state.
15. Row click opens File Selection for that entry.
16. Each row clearly shows source name and folder context.
17. If the active source snapshot is missing or unreadable while all saved required settings remain usable, Home shows a persistent visible warning banner and remains usable with the `No sources available` empty state; persistence guarantees follow [`persistence.md`](persistence.md).
18. When diagnostics is enabled, Home events are captured:
   - page open,
   - search apply and clear,
   - refresh result outcome,
   - source-load error-state visibility,
   - invalid-settings error-state visibility,
   - stale-source warning banner visibility,
   - missing-snapshot warning banner visibility.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).
