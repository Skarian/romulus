# Security Behavior

## Trigger

1. User saves API key.
2. App performs authenticated Real-Debrid operations.

## Expected Result

1. API key is encrypted at rest.
2. Credentials are user-provided only.
3. Sensitive values are not exposed in logs or diagnostics artifacts.
4. URI permissions follow least-privilege intent:
   - read for local source,
   - read or write for download directory.
5. Saved API key display uses masked format with a suffix derived from the last successfully validated saved key.
6. If saved access to the source file or download directory is lost, the app treats that saved value as unusable and requires the user to select it again in Settings.
7. The app never broadens file access automatically and never substitutes a different source file or directory.
8. Diagnostics captures and exports must redact credentials, auth tokens, and sensitive URL query values; diagnostics ownership follows [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Security or control failures do not expose plaintext credentials.
2. App remains usable through the Home invalid-settings error state and Settings update flow ([`home.md`](home.md) + [`settings.md`](settings.md)).
3. If diagnostics redaction fails for an event, app drops or sanitizes that event instead of persisting sensitive values.
