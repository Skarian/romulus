# Spike 5 `tryAll` API Harness

## 1. Purpose

1. This folder contains a disposable Kotlin or JVM CLI that validates the Real-Debrid whole-torrent API flow required by `tryAll`.
2. The harness proves the live `addMagnet -> selectFiles("all") -> status polling` behavior for one known cached magnet after clearing prior matching torrents from the account.
3. It is not production app code and it is intentionally isolated from the Android build.

## 2. Project Layout

1. `build.gradle.kts` and `settings.gradle.kts` define a nested standalone Gradle application build.
2. `src/main/kotlin/com/romulus/spikes/spike5/` contains the CLI entrypoint, env loading, Real-Debrid client, runner, and artifact writer.
3. `src/test/kotlin/com/romulus/spikes/spike5/` contains offline tests for env parsing and info-hash extraction.

## 3. Implemented Behavior

1. Loads `docs/spikes/apps/spike-5-try-all-api/.env.local` by default and also accepts one optional positional env-file override.
2. Requires `RD_API_TOKEN`, `SPIKE5_MAGNET`, and `SPIKE5_SELECTED_FILE_PATH`.
3. Deletes all existing Real-Debrid torrents whose `hash` matches the configured magnet before the probe starts.
4. Adds the magnet, selects all provider files using `files=all`, and polls torrent info until cached success, terminal failure, or timeout.
5. After cached success, takes the whole-torrent link, calls `unrestrict/folder`, and attempts a single-file download proof from the returned child links.
6. Writes timestamped artifacts under `docs/spikes/fixtures/generated/spike-5/run-artifacts/`.

## 4. Local Commands

1. Verified local build and test command from the repo root:

       ./gradlew -p docs/spikes/apps/spike-5-try-all-api --no-daemon test installDist

2. Live probe run from the repo root:

       ./gradlew -p docs/spikes/apps/spike-5-try-all-api --no-daemon run

3. Live probe run with an explicit env-file override:

       ./gradlew -p docs/spikes/apps/spike-5-try-all-api --no-daemon run --args='/absolute/path/to/.env.local'

## 5. Shared Workspace Boundary

1. The shared operator surface is `cd docs/spikes && just spike-5-build` and `cd docs/spikes && just spike-5`.
2. The local env file for this spike lives in `apps/spike-5-try-all-api/.env.local` and is ignored by git.
3. Generated artifacts belong under `docs/spikes/fixtures/generated/` and stay out of git.

## 6. Artifacts

1. Each invocation creates a new timestamped directory under `docs/spikes/fixtures/generated/spike-5/run-artifacts/` and updates `latest-run.txt`.
2. Each run writes `sanitized-inputs.md`, `trace.jsonl`, `matching-account-torrents.json`, `deleted-torrents.json`, `available-hosts.json`, `add-magnet.json`, `initial-info.json`, `post-select-info.json`, `folder-links.json` when available, `selected-file-probe.json`, `status-timeline.json`, `final-info.json`, `summary.json`, and `summary.md`.
