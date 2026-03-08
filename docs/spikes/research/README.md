# Spike Research Modules

This folder captures evidence-level research notes from reference modules used to prepare spike execution.

## Module Index

1. [`mediafusion.md`](/Users/nskaria/projects/romulus/docs/spikes/research/mediafusion.md) - Real-Debrid lifecycle and file-selection patterns.
2. [`unchained-android.md`](/Users/nskaria/projects/romulus/docs/spikes/research/unchained-android.md) - Android Real-Debrid endpoint usage and selection flow.
3. [`rdt-client.md`](/Users/nskaria/projects/romulus/docs/spikes/research/rdt-client.md) - provider orchestration, retry/rate-limit handling, and unpack flow.
4. [`7-zip-jbinding-4android.md`](/Users/nskaria/projects/romulus/docs/spikes/research/7-zip-jbinding-4android.md) - multi-format archive extraction on Android.
5. [`zip4j.md`](/Users/nskaria/projects/romulus/docs/spikes/research/zip4j.md) - zip-focused extraction and metadata APIs.
6. [`commons-compress.md`](/Users/nskaria/projects/romulus/docs/spikes/research/commons-compress.md) - `ZipFile` + `SeekableByteChannel` patterns for random-access zip reads.
7. [`cloudzip.md`](/Users/nskaria/projects/romulus/docs/spikes/research/cloudzip.md) - remote zip central-directory and HTTP range strategy.

## Notes

1. These are research references, not implementation templates.
2. Use spike specs in [`../specs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/README.md) to convert research into runnable tests.
3. Record outcomes in [`../outputs/README.md`](/Users/nskaria/projects/romulus/docs/spikes/outputs/README.md) before architecture buildout.
4. Keep behavior contracts in `/docs/behavior` as source of truth.
5. If a spike changes expected behavior, update `/docs/behavior/*` first, then architecture docs.
