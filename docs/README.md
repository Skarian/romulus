# Docs Workspace

This folder holds the product, behavior, and architecture docs for the shipped app, plus historical migration and spike material.

## Current Runtime Docs

1. [`PRD.md`](PRD.md) - product goals and simplification intent.
2. [`BEHAVIOR.md`](BEHAVIOR.md) - behavior contract index.
3. [`ARCHITECTURE.md`](ARCHITECTURE.md) - runtime ownership and package boundaries.
4. [`schema.json`](schema.json) - source input contract.
5. [`behavior/README.md`](behavior/README.md) - detailed behavior specs.
6. [`architecture/app.md`](architecture/app.md) - composition root and startup ownership.
7. [`architecture/ui.md`](architecture/ui.md) - presentation ownership.
8. [`architecture/source.md`](architecture/source.md) - source ingestion, snapshots, and browse ownership.
9. [`architecture/downloads.md`](architecture/downloads.md) - queue, execution, and output ownership.
10. [`architecture/realdebrid.md`](architecture/realdebrid.md) - Real-Debrid boundary ownership.
11. [`architecture/remotezip.md`](architecture/remotezip.md) - remote ZIP boundary ownership.
12. [`architecture/diagnostics.md`](architecture/diagnostics.md) - diagnostics ownership.
13. [`architecture/diagram/diagrams/app-architecture.mmd`](architecture/diagram/diagrams/app-architecture.mmd) - canonical high-level runtime diagram.

## Historical Context

1. [`MIGRATION.md`](MIGRATION.md) - migration plan and cutover history.
2. [`architecture/SPIKE.md`](architecture/SPIKE.md) - spike gates used before the runtime was finalized.
3. [`spikes/specs/README.md`](spikes/specs/README.md) - executable spike specs.
4. [`spikes/apps/README.md`](spikes/apps/README.md) - spike harness docs.
5. [`spikes/research/README.md`](spikes/research/README.md) - research notes.
6. [`spikes/fixtures/README.md`](spikes/fixtures/README.md) - fixture inventory and generation workflow.
7. [`spikes/outputs/README.md`](spikes/outputs/README.md) - spike result artifacts.

## Structure

1. `behavior/` - behavior contracts.
2. `architecture/` - runtime ownership docs and the diagram viewer.
3. `spikes/` - historical spike specs, apps, fixtures, and outputs.

## Run the Viewer

```bash
cd docs
just diagram
```

Open the local Vite URL shown in terminal.

## Export Docs

```bash
cd docs
just export-docs
```

`just export-docs` writes a timestamped zip under `docs/exports/`.
When run interactively, it also asks whether to include the active ExecPlans and, if you answer `yes`, adds them under `plans/` in the export archive.

## Viewer Features

1. Top navigation links to each diagram.
2. Mermaid rendering with fullscreen, zoom in/out, and reset.
3. Mouse drag pan and wheel zoom.
4. Optional raw Mermaid source view.

## Files

1. `architecture/diagram/index.html` - viewer shell.
2. `architecture/diagram/src/main.js` - render logic and controls.
3. `architecture/diagram/src/diagrams.js` - diagram index.
4. `architecture/diagram/diagrams/app-architecture.mmd` - canonical architecture diagram.

## Checks

```bash
cd docs/architecture/diagram
npm run check
npm test
npm run build
```

1. `check`: offline Mermaid flowchart syntax lint.
2. `test`: diagram catalog and syntax tests.
3. `build`: viewer bundle validation.
