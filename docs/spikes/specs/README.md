# Spike Specs

This folder defines execution specs for each required spike.

## Specs

1. [`spike-1-resolution-enumeration.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-1-resolution-enumeration.md)
2. [`spike-2-unarchive-runtime.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-2-unarchive-runtime.md)
3. [`spike-3-remote-zip.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-3-remote-zip.md)
4. [`spike-4-integration.md`](/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md)

## Rules

1. Specs are the pre-run source of truth for each spike.
2. Specs are execution and evidence focused.
3. Specs define what each spike must prove, not how the harness is internally built or invoked.
4. Harness location, command surface, and artifact paths live in [`../apps/README.md`](/Users/nskaria/projects/romulus/docs/spikes/apps/README.md).
5. Final product-policy decisions are made after spike outputs are reviewed.
6. Each completed spike must produce one post-run output file in `../outputs/`.
7. Fixture inventory and local fixture generation live in [`../fixtures/README.md`](/Users/nskaria/projects/romulus/docs/spikes/fixtures/README.md).
