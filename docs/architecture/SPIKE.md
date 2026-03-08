# SPIKE - Pre-Architecture Validation

## 1. Purpose

1. Validate high-risk capability areas before detailed architecture buildout.
2. Produce evidence-first decisions from reference modules and spike runs.
3. Convert spike outcomes into architecture inputs, not implementation code.

## 2. Scope and Gating Rule

1. Four spikes were required:
   - Spike 1: Resolver, enumeration, and provider-acquisition flow.
   - Spike 2: Unarchive runtime behavior.
   - Spike 3: Remote zip enumeration and selective internal download behavior.
   - Spike 4: End-to-end integration flow.
2. Detailed architecture expansion work under `docs/architecture` was gated until all four spikes had:
   - run evidence,
   - written outputs in `spikes/outputs`,
   - accepted go or no-go outcomes.
3. That gate is now satisfied; the spike outputs are the reviewed inputs for the next architecture phase.
4. User-provided magnet fixtures are canonical for Spike 1 and Spike 4.

## 3. Spike Specs

1. [`spikes/specs/spike-1-resolution-enumeration.md`](../spikes/specs/spike-1-resolution-enumeration.md)
2. [`spikes/specs/spike-2-unarchive-runtime.md`](../spikes/specs/spike-2-unarchive-runtime.md)
3. [`spikes/specs/spike-3-remote-zip.md`](../spikes/specs/spike-3-remote-zip.md)
4. [`spikes/specs/spike-4-integration.md`](../spikes/specs/spike-4-integration.md)

## 4. Spike Harness Docs

1. Harness and build guidance lives in [`spikes/apps/README.md`](../spikes/apps/README.md).
2. Each spike has one harness doc under `spikes/apps/`.
3. Those docs define harness workspace shape and operator surface; some spikes now also have implemented harness code under those folders.

## 5. Required Outputs

1. Output contracts live in [`spikes/outputs/README.md`](../spikes/outputs/README.md).
2. Each spike must produce one output doc under `spikes/outputs/` after the spike run completes.
3. Pre-run proof contracts belong in `spikes/specs/`; harness/build guidance belongs in `spikes/apps/`; output docs are post-run artifacts only.
4. Each output doc must include:
   - evidence summary,
   - constraints discovered,
   - dependency keep or replace recommendation,
   - architecture implications and required document updates.
5. Output docs are inputs to the next architecture buildout phase.

## 6. Reference Modules

1. Research notes live in [`spikes/research/README.md`](../spikes/research/README.md).
2. Spike 1 references:
   - [`spikes/research/mediafusion.md`](../spikes/research/mediafusion.md)
   - [`spikes/research/unchained-android.md`](../spikes/research/unchained-android.md)
   - [`spikes/research/rdt-client.md`](../spikes/research/rdt-client.md)
3. Spike 2 references:
   - [`spikes/research/7-zip-jbinding-4android.md`](../spikes/research/7-zip-jbinding-4android.md)
4. Spike 3 references:
   - [`spikes/research/cloudzip.md`](../spikes/research/cloudzip.md)
   - [`spikes/research/commons-compress.md`](../spikes/research/commons-compress.md)
5. Spike 4 references:
   - Spike 1, Spike 2, and Spike 3 output docs.
   - `docs/behavior/*` as contract targets.

## 7. Fixtures

1. Fixture inventory and generation workflow live in [`spikes/fixtures/README.md`](../spikes/fixtures/README.md).
2. User-provided Real-Debrid inputs for Spike 1 and Spike 4 belong in local ignored file `spikes/.env.local`, starting from [`spikes/.env.example`](../spikes/.env.example).
3. Generate the local fixture workspace with:

```bash
cd docs/spikes
just fixtures
```

4. Generated fixture material belongs under `spikes/fixtures/generated/` and stays out of git.

## 8. Execution Shape

1. Use one disposable spike workspace with isolated flows for each spike.
2. Spike 1 should be executed as a desktop CLI, not an Android harness.
3. Spike 2 should use a minimal Android harness with `7-Zip-JBinding-4Android`.
4. Spike 3 should use a desktop JVM or Kotlin harness with a tiny local HTTP server that serves test zips.
5. Spike 4 should use a focused Android app that composes accepted Spike 1-3 outcomes.
6. Prefer PC execution for spike runs that are primarily API orchestration and trace capture.
7. Keep spike code and fixtures separate from migration implementation.
8. Move only evidence-backed decisions into architecture docs.
