# Downloads Page Behavior

## Trigger

1. User queues one or more files.
2. User opens Downloads page from the bottom navbar.
3. User is routed to Downloads after starting downloads from Home Files page.
4. Background worker processes queue tasks.
5. Device configuration changes (including orientation) while Downloads is visible.

## Expected Result

1. Queue state is durable across process death.
2. Downloads run in background.
3. A task-specific failure does not stop unrelated queue rows unless a shared prerequisite failure blocks forward progress globally.
4. Each attempt resolves a fresh downloadable link.
5. A task must always display the state that matches the work actually happening at that moment; transition order may vary by cache state, provider readiness, retry, pause or resume, restart, recovery, and download mode.
6. Downloads page matches current UX:
   - rows are ordered newest first,
   - each row shows original file name, size, progress, state tag, and action menu.
7. Configuration changes rebind and show existing queue state instead of creating new queue work.
8. Configuration changes do not restart in-flight work, duplicate rows, or reset progress.
9. When Downloads has no visible rows, the page shows `No active downloads`, both before any queue rows exist and after `Clear history` hides all visible terminal rows.
10. User-visible row status labels and their state-to-label mapping are defined in [`status-model.md`](status-model.md).
11. Downloads list rows show compact status and progress appropriate to the current state:
   - `Queued`, `Resolving`, and `Retry Scheduled` do not require a percent label,
   - `Preparing` shows Real-Debrid progress when available; otherwise it shows the `Preparing` state without fake byte progress,
   - `Running` shows local percent label (`--%` when total size is unknown) and local byte progress,
   - `Paused` preserves the last known local percent and byte counts and shows `Paused`.
12. Download details modal always shows:
   - source entry,
   - original file name,
   - output target summary,
   - created and updated timestamps,
   - file size,
   - output sub-folder,
   - current state,
   - failure reason,
   - part label,
   - attempt count when applicable.
13. When the row saves one file directly without unarchive, the output target summary is the single output name that will be written.
14. When unarchive intent is enabled for a supported archive, the output target summary describes the extracted output set:
   - extraction mode (`flattened extraction`),
   - extracted-file count when known,
   - representative final output names or a manifest preview when available.
15. When current state is `Preparing`, download details also show:
   - last Real-Debrid status,
   - Real-Debrid progress when available,
   - `Preparing` start time,
   - 24-hour timeout deadline.
16. When current state is `Running` or `Paused`, download details show local progress percent and bytes.
17. Downloads page queue summary counters use:
   - `completed/total`,
   - explicit `failed` count when non-zero,
   - explicit `cancelled` count when non-zero.
18. When `total` is zero, the page hides the summary subtitle instead of showing placeholder summary copy.
19. `total` means all currently visible files counted in the queue summary (completed + failed + cancelled + active); rows hidden by `Clear history` are excluded.
20. State ownership follows [`status-model.md`](status-model.md).
21. Persistence and recovery guarantees follow [`persistence.md`](persistence.md).
22. Notification copy and title behavior is defined in [`notifications.md`](notifications.md).
23. When provider-side acquisition is required, the task enters `Preparing` until links are ready, the user cancels, the provider returns a terminal failure, or the 24-hour cap is reached.
24. When queue intake came from archive-selection mode, each queue row represents one selected internal file; the outer `.zip` container is not a queue row.
25. When unarchive intent is enabled and the selected file is a supported archive (`.zip`, `.rar`, `.7z`):
   - archive contents are extracted,
   - internal archive directories are flattened,
   - extracted non-archive files are written directly into the entry `subfolder`,
   - no dedicated archive-named folder is created.
26. When recursive unarchive intent is enabled, supported archive outputs from a completed extraction pass are extracted again in bounded additional passes until no supported archive outputs remain.
27. After successful extraction, the archive file is deleted.
28. If unarchive intent is enabled but the selected file is not a supported archive, the file is saved normally without extraction.
29. Naming behavior for normal files and extracted files follows [`naming.md`](naming.md).
30. Archive-selection queue semantics are defined in [`archive-selection.md`](archive-selection.md).
31. When diagnostics is enabled, Downloads events are captured:
   - queue-task state transitions,
   - user actions (`Pause`, `Resume`, `Cancel`, `Retry`, `Restart`),
   - clear-history confirmation and outcome.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Queue Intake

1. Starting downloads appends selected files to the end of queue order, whether selected from standard Files mode or archive-selection mode.
2. Queue processing order is first-in-first-out, limited only by configured concurrency; configured concurrency is a live active-attempt cap, so freed slots refill immediately from the queued backlog while other attempts are still running.
3. Downloads page list order is newest-first for readability and does not change queue processing order.
4. Queueing the same source file again creates a new queue row.
5. `Retry` and `Restart` reuse the same queue row and do not create a new row.

## State and Actions (Menu)

| State | Required Actions |
| --- | --- |
| Queued | Cancel |
| Resolving | Cancel |
| Preparing | Cancel |
| Running | Pause, Cancel |
| Paused | Resume, Cancel |
| Retry Scheduled | Cancel |
| Failed | Retry, Restart |
| Cancelled | Restart |
| Completed | Restart |

17. When the user cancels a live `Preparing` or `Running` row, the row must finish as `Cancelled`; it must not fall through to `Retry Scheduled` or back into `Resolving` just because the in-flight attempt ended on a failure path after the durable cancel request was already recorded.

## History Cleanup

1. Downloads page includes a top-level `Clear history` action.
2. `Clear history` opens a confirmation dialog before applying changes.
3. By default, clear targets terminal rows in states:
   - `Completed`,
   - `Cancelled`.
4. Dialog includes optional `Include failed` toggle:
   - default is off,
   - when enabled, `Failed` rows are also included.
5. Confirmed clear hides matching rows from Downloads page.
6. Hidden rows remain in app data and are not deleted.
7. Successful clear shows a toast confirmation.
8. Clear history never targets active states.
9. Clear history remains available while active downloads run because it only applies to terminal rows.
10. Clear history changes list visibility only and does not delete queue rows, but hidden rows stop contributing to user-facing summary counters.
11. If user enables `Include failed` and confirms clear, failed rows are intentionally hidden from the default list and their `Retry` or `Restart` actions are no longer shown there.
12. Hiding failed rows is a deliberate user action, not automatic cleanup.
13. App provides no hidden-history view and no unhide action; cleared rows stay hidden across normal app restarts.
14. If visibility metadata is missing, fallback behavior follows [`persistence.md`](persistence.md).

## Recovery Semantics

1. Auto-retry allows up to 4 total attempts per task:
   - the initial attempt,
   - an immediate retry after the first failed attempt,
   - a retry after 15 seconds,
   - a retry after 60 seconds.
2. Manual `Retry` resets attempt count and starts a fresh retry cycle, including a fresh 24-hour `Preparing` window if provider-side acquisition is needed again.
3. `Restart` deletes the prior output set created by that row first, then resets progress or state and requeues from a fresh start, including a fresh 24-hour `Preparing` window if provider-side acquisition is needed again.
4. If restart cannot delete the prior output set, restart does not proceed.
5. Process interruption must not reset provider-preparation timing or local transfer progress. Recovered work resumes or safely requeues without deleting partial data, and any task that was already in `Preparing` keeps its original `Preparing` start time and 24-hour deadline.
6. Auth failures fan out clearly (`Auth required`) and stop forward progress until the user updates the API key in Settings.
7. Notifications for active or completed queue work are required; details are defined in [`notifications.md`](notifications.md).

## Failure Behavior

1. Failure reason is preserved and visible.
2. Prior-output cleanup, including partial files and extracted output sets, is handled through `Restart`.
3. If clear history fails, existing row visibility remains unchanged.
4. If the saved download directory becomes unavailable during download or output write, the affected queue row fails with a clear directory-access error and can be retried after the user selects a working directory in Settings.
   Retry after that repair uses the newly selected working directory rather than staying pinned to the previously broken saved directory.
5. If provider-side acquisition does not become link-ready within 24 hours from entering `Preparing`, the queue row fails with an explicit provider-preparation timeout reason and stops waiting.
