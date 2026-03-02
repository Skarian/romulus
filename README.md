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
3. Pick a download directory using Storage Access Framework.

After setup, bottom navigation routes are:

- `Home`: validated source catalog, cold-launch URL refresh, and manual refresh.
- `Downloads`: queue states, controls, details, and clear terminal history.
- `Settings`: credential/source/directory edits, concurrency, manual refresh, and Ketch spike entrypoint.

## Build and Verification

```bash
./gradlew clean
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

## Package Structure

- `app/`: Compose app shell, navigation, setup gating.
- `core/`: shared primitives and helpers (validation, filenames, error mapping, clock).
- `data/`: settings storage, source ingestion, Real-Debrid client, queue persistence.
- `domain/`: business models and transformations.
- `feature/`: setup, home, file selection, downloads, settings UI.
- `worker/`: queue orchestration, notifications, background execution.

## Operational Notes

- Queue states are persisted in Room and survive process restarts.
- Notification permission is requested at runtime on Android 13+.
- Queue progress notification uses `x/x completed` and appends failed count when needed.
- Completion notification posts when active count transitions to zero.
- Progress and completion notifications deep-link to `Downloads`.
- Local source snapshots are immutable once queued work is created.
- Home shows an invalid-entry summary when source validation skips malformed entries.

## Dependency policy

The app intentionally keeps a small dependency surface:

- Compose + Navigation for a single-activity, setup-gated UI shell.
- WorkManager for durable background queue orchestration.
- Room for persisted queue/history state.
- DataStore for non-sensitive settings.
- Retrofit/OkHttp + Kotlin serialization for Real-Debrid HTTP integration.
- Ketch for transfer-engine controls (pause/resume/cancel/retry/restart).

## Known Limitations

- Physical-device validation for setup/download lifecycle is not runnable in CI.
- `connectedCheck`/instrumented UI tests require a connected emulator/device.
