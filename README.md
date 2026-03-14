# Romulus Android

Standalone Android app for the Real-Debrid download manager.

## Requirements

- Android Studio (recommended for emulator/device workflows)
- JDK 17+ (JDK 21 also works)
- Android SDK with API 36 platform installed

## Quick Start

1. Build a debug APK:

```bash
./gradlew assembleDebug
```

2. Run automated verification:

```bash
./gradlew test lint
```

3. Install to a connected Android 13+ device or emulator:

```bash
./gradlew installDebug
```

`installDebug` fails with `No connected devices!` if no emulator/phone is attached.

## Runtime Flow

The app is setup-gated. Before Home/Downloads/Settings are shown, setup must complete:

1. Validate Real-Debrid API key.
2. Configure JSON source (public URL or local file picker).
3. Pick a download directory using Storage Access Framework (this completes setup immediately and shows `Setup Completed!`).

After setup, bottom navigation routes are:

- `Home`: validated source catalog with a `Source | Folder` table, URL-only refresh action, and direct navigation into per-entry file tables.
- `Downloads`: one reverse-chronological table where the file column takes remaining width, while progress and overflow actions stay in compact columns.
- `Settings`: grouped sections for credentials/source/directory/download behavior, obfuscated saved API key display, and manual refresh near source controls.

## Source JSON Contract

Each source entry can optionally define a torrent-internal `scope` object. `scope.path` says where file selection starts, and `scope.includeNestedFiles` controls whether deeper descendants are eligible.

- `version` stays `1`.
- `entries[i].scope` is optional.
- If `scope` is omitted, it defaults to `{ "path": "/", "includeNestedFiles": false }`.
- `scope.path` uses forward slashes and cannot contain `..`.
- `scope.path: "/"` means top-level files only unless `scope.includeNestedFiles` is `true`.
- Path matching is boundary-safe: `/Season 1/` does not match `/Season 10/`.

Example:

```json
{
  "version": 1,
  "entries": [
    {
      "displayName": "Show Pack",
      "subfolder": "shows/show-pack",
      "scope": {
        "path": "/Series/Season 01/",
        "includeNestedFiles": true
      },
      "torrents": [
        { "url": "magnet:?xt=urn:btih:...", "partName": "Part 1" }
      ],
      "ignore": { "glob": ["*.nfo"] }
    }
  ]
}
```

## Build and Verification

```bash
./gradlew clean
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

## Package Structure

- `app/`: Compose app shell, navigation, setup gating.
- `core/`: shared primitives and helpers (validation, filenames, clock).
- `data/`: settings storage, source ingestion, Real-Debrid client, queue persistence, runtime queue state.
- `domain/`: business models and transformations.
- `feature/`: setup, home, file selection, downloads, settings UI.
- `worker/`: queue orchestration, notifications, background execution.

## Operational Notes

- Queue states are persisted in Room and survive process restarts.
- Queue uses split state lanes: Room for durable lifecycle state and in-memory runtime store for high-frequency progress/control updates.
- Notification permission is requested at runtime on Android 13+.
- Queue progress notification shows percent, bytes downloaded, and file completion count (plus failed count when present).
- Completion notification posts when active count transitions to zero.
- Progress and completion notifications deep-link to `Downloads`.
- Local source snapshots are immutable once queued work is created.
- Home shows an invalid-entry summary when source validation skips malformed entries.
- File-resolution results are cached per snapshot/entry in-memory to avoid repeated resolve churn during configuration changes and route switches.
- File selection uses a compact top icon row (`warning` when present, `search`, `settings`, `download`) and shows resolver warnings in a popup dialog.
- Default max concurrency fallback is `25`.

## Dependency policy

The app intentionally keeps a small dependency surface:

- Compose + Navigation for a single-activity, setup-gated UI shell.
- WorkManager for durable background queue orchestration.
- Room for persisted queue/history state.
- DataStore for non-sensitive settings.
- Retrofit/OkHttp + Kotlin serialization for Real-Debrid HTTP integration.

## Known Limitations

- Physical-device validation for setup/download lifecycle is not runnable in CI.
- `connectedCheck`/instrumented UI tests require a connected emulator/device.
