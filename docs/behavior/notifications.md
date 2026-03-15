# Notifications Behavior

## Trigger

1. Active queue work changes from zero to non-zero.
2. While active work remains non-zero, queue state changes that would change visible notification text or presentation.
3. While active work remains non-zero, progress or summary counts change that would change visible notification text or counters.
4. Active queue work changes from non-zero to zero.

## Expected Result

1. Active work shows a persistent progress notification.
2. When any active task is in `Running`, progress text uses byte-first summary style:
   - when total bytes are known: `percent complete | downloaded/total | completed/total`,
   - when total bytes are unknown: `downloaded | completed/total`.
3. When no active task is in `Running`, notification text stays short and status-only, such as `Resolving downloads`, `Preparing downloads`, or `Retry scheduled`.
4. Notification text does not include provider status detail, provider percentages, or other long stage explanations; those details belong on the Downloads page.
5. Byte-first progress summary includes `failed` count when non-zero.
6. Byte-first progress summary may omit `cancelled` count.
7. Completion posts a final outcome notification.
8. Completion notification titles are:
   - `Downloads complete` when there are no failures and no cancellations,
   - `Downloads finished` when any failure or cancellation occurred.
9. Completion copy distinguishes full success from mixed outcomes.
10. Notification tap deep-links to Downloads.
11. Status buckets align with [`status-model.md`](status-model.md).
12. `completed/total` semantics align with [`downloads.md`](downloads.md).
13. Rows hidden by `Clear history` do not contribute to notification `completed/total`, `failed`, or `cancelled` counts.
14. While active work remains non-zero, notification updates occur whenever the visible notification text or counters would change, including non-Running short-status transitions such as `Resolving`, `Preparing`, or `Retry scheduled`.
15. When diagnostics is enabled, notification events are captured:
   - progress notification post and update,
   - progress notification clear,
   - completion notification post outcome.
   Diagnostics capture rules follow [`diagnostics.md`](diagnostics.md).

## Failure Behavior

1. Notification failure must not break queue execution.
2. Missing permission degrades gracefully without crashes.
