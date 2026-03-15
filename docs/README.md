# Docs Workspace

This folder is independent from Android runtime code and is used to iterate on behavior and architecture contracts.

## Canonical Docs

1. [`PRD.md`](PRD.md) - product goals and simplification intent.
2. [`BEHAVIOR.md`](BEHAVIOR.md) - behavior contract index.
3. [`ARCHITECTURE.md`](ARCHITECTURE.md) - simplified runtime design.
4. [`MIGRATION.md`](MIGRATION.md) - behavior-first migration plan.
5. [`architecture/SPIKE.md`](architecture/SPIKE.md) - pre-architecture spike goals and gates.
6. [`spikes/specs/README.md`](spikes/specs/README.md) - executable spike specs for Spikes 1-4.
7. [`spikes/apps/README.md`](spikes/apps/README.md) - intended spike harness and operator-surface docs.
8. [`spikes/research/README.md`](spikes/research/README.md) - reference module research notes for spike preparation.
9. [`spikes/fixtures/README.md`](spikes/fixtures/README.md) - spike fixture inventory and local generation workflow.
10. [`spikes/outputs/README.md`](spikes/outputs/README.md) - required spike output docs that feed architecture buildout.
11. [`schema.json`](schema.json) - source input contract.
12. [`behavior/README.md`](behavior/README.md) - detailed behavior specs.

## Structure

1. `behavior/` - behavior contracts.
2. `architecture/SPIKE.md` - spike plan and pass/fail criteria.
3. `spikes/specs/` - spike execution specs.
4. `spikes/apps/` - intended spike harness and operator-surface docs.
5. `spikes/research/` - reference module research notes.
6. `spikes/fixtures/` - fixture inventory and generated local spike inputs.
7. `spikes/outputs/` - spike result artifacts for architecture buildout.
8. `architecture/diagram/` - Mermaid diagram viewer and diagram assets.

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
