# App Shell Behavior

## Trigger

1. App launches.
2. User navigates between primary tabs.
3. App receives a route intent from notification tap.
4. Device configuration changes (including orientation) while app is open.

## Expected Result

1. Before setup is complete, only setup flow is shown; setup gating rules follow [`setup.md`](setup.md).
2. After setup, primary navigation is always available with:
   - Home,
   - Downloads,
   - Settings.
3. User can move between tabs without re-running setup.
4. The `Home` tab preserves its current route context:
   - if user leaves Home while viewing the source list, returning to `Home` shows the source list,
   - if user leaves Home while viewing a Files page, returning to `Home` shows that same Files page.
5. Device configuration changes keep the current tab and route context.
6. In compact-height layouts such as phone landscape rotation, shell pages keep top and bottom content spacing tight enough to preserve usable scroll space without crowding system UI or the bottom navigation bar.
7. Device configuration changes by themselves do not trigger source refresh, file re-resolution, or queue mutation; page-level behavior is defined in [`home.md`](home.md), [`files.md`](files.md), and [`downloads.md`](downloads.md).
8. Starting downloads from Home Files navigates to Downloads; start behavior follows [`files.md`](files.md).
9. Notification deep-link opens Downloads; notification behavior follows [`notifications.md`](notifications.md).
10. App requests Android notification permission once after setup completes.
11. If a previously saved API key, source file, or download directory becomes unusable after setup, app keeps navigation visible and routes Home to the invalid-settings error state defined in [`home.md`](home.md) while the user fixes the broken setting in [`settings.md`](settings.md).
12. When diagnostics is enabled, app-shell events are captured for support:
   - app launch,
   - primary-tab navigation,
   - notification deep-link routing,
   - invalid-settings error-state visibility.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Broken saved settings never force user back into first-run setup.
2. Unknown startup route falls back to default app navigation safely.
