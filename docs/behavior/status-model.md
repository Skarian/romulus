# Status Model Behavior

## Trigger

1. Queue task state changes.
2. Downloads page renders rows and actions.
3. Notification summaries are generated.

## Expected Result

1. Canonical task states are:
   - `Queued`,
   - `Resolving`,
   - `Preparing`,
   - `Running`,
   - `Paused`,
   - `Retry Scheduled`,
   - `Completed`,
   - `Failed`,
   - `Cancelled`.
2. Active states are:
   - `Queued`,
   - `Resolving`,
   - `Preparing`,
   - `Running`,
   - `Paused`,
   - `Retry Scheduled`.
3. Terminal states are:
   - `Completed`,
   - `Failed`,
   - `Cancelled`.
4. A task must always display the state that matches the work actually happening at that moment; transition order may vary by cache state, provider readiness, retry, pause or resume, restart, recovery, and download mode.
5. State meanings are:
   - `Queued`: waiting for queue execution to begin,
   - `Resolving`: app-side link or setup work is in progress,
   - `Preparing`: provider-side acquisition is in progress and no local download bytes are moving yet,
   - `Running`: bytes are actively transferring to the device,
   - `Paused`: local transfer is intentionally stopped and no bytes are moving,
   - `Retry Scheduled`: waiting for the next automatic retry window.
6. User-facing status labels are:
   - `Queued` for `Queued`,
   - `Resolving` for `Resolving`,
   - `Preparing` for `Preparing`,
   - `Downloading` for `Running`,
   - `Paused` for `Paused`,
   - `Retry scheduled` for `Retry Scheduled`,
   - `Done` for completed work,
   - `Failed` for failed work,
   - `Cancelled` for cancelled work.
7. `Failed` and `Cancelled` remain distinct statuses and are never merged into one label.
8. When a retry attempt is actively running, the task re-enters the canonical work state that matches the actual work in progress (`Resolving`, `Preparing`, `Running`, or `Paused`); retry attempt count and history are metadata, not a separate user-facing state.
9. Action availability by detailed state is defined by [`downloads.md`](downloads.md).
10. Run counters use these status buckets and total semantics.
11. Notification presentation may use byte-first copy while still reflecting these underlying queue outcomes, as defined in [`notifications.md`](notifications.md).
12. When diagnostics is enabled, queue state diagnostics events must use canonical state names defined in this contract; diagnostics rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Unknown state values must not be mapped to `Failed` or `Cancelled`.
2. State mismatch must not hide available recovery actions or misrepresent whether bytes are actually moving.
