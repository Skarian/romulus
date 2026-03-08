# Spike Outputs

This folder is the post-run handoff from spike execution to architecture buildout.

## Rule of Use

1. Output docs are created only after a spike has been run.
2. Do not use this folder for pre-run planning or draft runbooks.
3. Pre-run proof contracts belong in `spikes/specs/`.
4. Pre-run harness and build guidance belongs in `spikes/apps/`.

## Required Output Files

1. `spike-1-resolution.md`
2. `spike-2-unarchive.md`
3. `spike-3-remote-zip.md`
4. `spike-4-integration.md`

## Required Sections Per Output

1. Run context:
   - fixture identifiers,
   - runtime environment,
   - references used.
2. Evidence summary:
   - observed flow or capability,
   - observed constraints,
   - reproducibility notes.
3. Decision proposal:
   - recommended keep or replace dependencies,
   - recommended architecture constraints.
4. Risk register:
   - blocking risks,
   - non-blocking risks,
   - open unknowns marked `UNCONFIRMED`.
5. Architecture implications:
   - specific `ARCHITECTURE.md` sections to update,
   - required design constraints confirmed by the spike,
   - candidate follow-on architecture docs to create.

## Rule

1. Architecture buildout decisions must cite spike output evidence.
