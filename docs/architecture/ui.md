# `ui/` Package Architecture

## Purpose

`ui/` owns presentation: screens, view models, screen-local state, and translation from user intent into owner-facing commands.
It renders app-owned shell state but does not own durable product state.

## Responsibilities

- Render Setup, Home, Files, Downloads, and Settings.
- Hold page-local search, selection, dialog, toast, and list-position state.
- Translate user edits and taps into typed commands for `app/`, `source/`, `downloads/`, `realdebrid/`, and `diagnostics/`.
- Observe owner-backed state and map it into presentation models.
- Preserve the runtime visual language through shared theme primitives under `ui/theme`.

## Boundaries

- `ui/` does not create or mutate durable queue rows directly.
- `ui/` does not own source snapshots, token storage, download settings storage, or diagnostics artifacts.
- `ui/` does not run, pause, resume, or recover background work on its own.
- `ui/` does not parse Android intents or capture URI grants directly.

## Stable Surfaces

- `ui/shell` hosts the persistent shell and route-to-screen rendering.
- `ui/setup` owns first-run edit buffers and submission feedback.
- `ui/home` owns search and refresh presentation.
- `ui/files` owns browse presentation, selection, and page-local rename or unarchive preferences.
- `ui/downloads` owns queue projection display, row details, and clear-history confirmation.
- `ui/settings` owns editable drafts, field lock presentation, and diagnostics controls.

## Durable Invariants

1. Screen-local state survives configuration change through view models and saved state where appropriate.
2. `ui/` consumes owner facades and typed models rather than raw stores or transport DTOs.
3. Queue activity and settings lock state are always read from owner-backed state, not inferred from local UI memory.
4. Shell routing remains app-owned even though `ui/` renders the shell.

## Key Flows

### Setup

1. Collect API key, source, and output directory inputs.
2. Ask `app/` to persist grants when the user picks local documents or directories.
3. Submit validated commands through owner facades.

### Browse and Queue

1. Home opens a source entry in Files.
2. Files loads owner-provided browse results, then applies search and selection locally.
3. Queueing returns the user to Downloads while keeping queue ownership in `downloads/`.

### Downloads and Settings

1. Downloads renders queue projection and available row actions from `downloads/`.
2. Settings renders editable or locked fields based on owner readiness and active-download state.
3. Diagnostics controls in Settings call `diagnostics/` directly through its facade.
