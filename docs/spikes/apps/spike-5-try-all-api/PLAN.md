# Spike 5 Harness Plan

## Purpose

1. Provide a narrow disposable harness for validating the live Real-Debrid API behavior behind the planned `tryAll` whole-torrent preflight.

## Deliverables

1. A standalone Kotlin or JVM CLI under this folder.
2. A local ignored env file containing `RD_API_TOKEN` and `SPIKE5_MAGNET`.
3. A generated artifact bundle under `docs/spikes/fixtures/generated/spike-5/run-artifacts/`.
4. A post-run output summary in `docs/spikes/outputs/spike-5-try-all-api.md`.

## Execution Rule

1. The harness must delete all matching-account torrents for the configured magnet before it adds the probe torrent.
2. The harness must use `selectFiles(id, "all")`.
3. The harness must persist observed provider statuses without persisting the token or unrestricted links.
