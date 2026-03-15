# Spike Harness Docs

This folder holds the harness docs for each spike.

## Purpose

1. Keep spike execution specs focused on what each spike must prove.
2. Keep harness and build guidance in one predictable place.
3. Describe the harness-local operator surface and workspace shape for each spike.

## Harness Docs

1. [`spike-1-cli/README.md`](spike-1-cli/README.md)
2. [`spike-2-android/README.md`](spike-2-android/README.md)
3. [`spike-3-remote-zip/README.md`](spike-3-remote-zip/README.md)
4. [`spike-4-android/README.md`](spike-4-android/README.md)
5. [`spike-5-try-all-api/README.md`](spike-5-try-all-api/README.md)

## Ownership Split

1. `specs/` owns goals, run matrix, evidence, and pass or fail gates.
2. `apps/` owns intended harness location, build shape, operator surface, and artifact paths.
3. `outputs/` owns the post-run evidence handoff only.
4. Shared workspace files under `docs/spikes/` such as `justfile`, fixture generation, and common ignores are cross-spike infrastructure and should be coordinated centrally rather than owned by one spike plan.

## Status Rule

1. These docs define the intended harness shape for each spike and, once implemented, the actual harness surface that should stay aligned with the code.
2. Spike 1, Spike 2, Spike 3, and Spike 4 now have harness implementations under their folders.
3. Spike 5 is an additional post-architecture-validation harness for `tryAll` Real-Debrid cache characterization.
4. Shared repo-level recipes in `docs/spikes/justfile` are coordinator-owned cross-spike infrastructure and should stay aligned with the implemented harness surfaces.
5. Keep each harness implementation and its matching doc aligned as the accepted record of the completed spike chapter.
