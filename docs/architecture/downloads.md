# `downloads/` Package Architecture

## Purpose

`downloads/` owns download settings, durable queue state, execution attempts, output reservation and finalization, and the background worker entrypoint. It is the only package allowed to create queue rows, mutate canonical queue state, reserve output destinations, gate forward progress when shared prerequisites fail, and mark work completed, failed, paused, cancelled, retried, or restarted.

This package stays intentionally concrete because it owns the most contract-bearing behavior in the app:
- queue rows are durable,
- queue state must match real work,
- interruption must not lose `Preparing` timing or transfer checkpoints,
- output naming and extraction order must be deterministic,
- clear history is visibility-only and transactional,
- rows are projected newest-first by `createdAt`, not by latest mutation time.

## Owned Responsibilities

- Persist and validate download settings such as output directory and concurrency.
- Create durable queue rows from `QueueTaskInput`.
- Own queue action handling for pause, resume, cancel, retry, restart, and clear history.
- Persist claims, checkpoints, retry schedules, preparing metadata, visibility metadata, and final-output records.
- Resolve, prepare, run, resume, and recover download attempts through `realdebrid/` or `remotezip/`.
- Reserve deterministic output identity before writes and finalize outputs in the correct order.
- Bind fresh writes to the current saved output directory at execution or retry time while keeping existing reservations resumable in place.
- Read runtime concurrency, own the queue-global auth gate, and wake background work when launch recovery, queue mutations, auth repair, or retry deadlines make work runnable.
- Expose worker entrypoints and queue-backed notification projection.

## Explicit Non-Responsibilities

- `downloads/` does not validate source JSON or assign snapshot identity.
- `downloads/` does not parse Real-Debrid HTTP payloads directly.
- `downloads/` does not enumerate remote ZIP contents; it only consumes selected-entry identity from `source/`.
- `downloads/` does not own diagnostics retention.
- `downloads/` does not capture URI grants; it consumes persisted output-directory descriptors from `app/`.

## Authorities and Owned Records

- `DownloadSettingsState`
  - Active output directory and max concurrency.
- `DownloadSettingsReadiness`
  - Whether the saved output directory is still usable.
- `QueueTask`
  - Durable row identity, enqueue-time execution contract, original file size when known, and output-subfolder context.
- `QueueRowRecord`
  - Durable row envelope with canonical state, visibility, durable `updatedAt`, durable attempt count, claim-lease metadata, persisted pending live action, queue-owned persisted reservation/output data, and retained cleanup scopes from earlier manual retries that `Restart` must still delete.
- `QueueTaskState`
  - Canonical durable state for one row; completed output records live on the row envelope, not inside the terminal state payload.
- `QueueActionRequest`
  - Internal persisted request for live pause or cancel coordination that recovery must honor if process death happens before a worker acknowledges it.
- `ClaimLease`
  - Durable lease metadata that makes stale claim reclamation explicit after process death.
- `PreparingMetadata`
  - `Preparing` start time, timeout deadline, last provider status, last provider progress, and resume marker.
- `TransferCheckpoint`
  - Latest local transfer progress and resume data.
- `OutputReservation`
  - Output-owned reservation shape, deterministic output identity, and the bound output root for one resumable write, persisted by `downloads/queue` as queue-owned row data.
- `FinalOutputRecord`
  - Durable record of what the row wrote successfully, including output-owned final-output identity.
- `QueueWorkGateState`
  - Runtime gate over new queue claims when auth is broken.
- `DownloadsProjection`
  - Owner-backed UI projection with newest-first visible rows, summary counters over visible rows only, explicit detail fields for updated time, original size, output subfolder, preparing metadata, and running or paused local transfer detail, plus canonical state and allowed actions for row menus.

## Dependencies

- Inbound dependencies:
  - `app/` for startup readiness.
  - `ui/files` for enqueue commands.
  - `ui/downloads` for queue projection and row actions.
  - `ui/settings` for download-settings edits.
- Outbound dependencies:
  - `realdebrid/` for standard file acquisition and later ready-link resolution.
  - `remotezip/` for archive-entry copy.
  - `diagnostics/` for queue and worker events.
- What may cross the root-package boundary:
  - `DownloadSettingsDraft`,
  - `DownloadSettingsState`,
  - `DownloadSettingsReadiness`,
  - `QueueTaskInput`,
  - `QueueActionCommand`,
  - `DownloadsProjection`,
  - `EnqueueResult`.
- What may not cross the root-package boundary:
  - ledger internals,
  - temp files,
  - worker control channels,
  - raw notification channel APIs.

## Public Entry Points

- `DownloadsFacade.observeSettings(): StateFlow<DownloadSettingsState>`
- `DownloadsFacade.observeSettingsReadiness(): StateFlow<DownloadSettingsReadiness>`
- `DownloadsFacade.readSettingsReadiness(): DownloadSettingsReadiness`
- `DownloadsFacade.updateSettings(draft: DownloadSettingsDraft): Result<Unit>`
- `DownloadsFacade.enqueue(inputs: List<QueueTaskInput>): EnqueueResult`
- `DownloadsFacade.observeDownloadsProjection(): StateFlow<DownloadsProjection>`
- `DownloadsFacade.observeActiveDownloadsFlag(): StateFlow<Boolean>`
- `DownloadsFacade.performAction(command: QueueActionCommand): Result<Unit>`
- `DownloadsFacade.clearHistory(includeFailed: Boolean): Result<Unit>`
- `DownloadsFacade.requestAppLaunchRecovery(): Result<Unit>`
- `DownloadWorkerEntryPoint.runOnce(): Unit`
- `DownloadWorkerEntryPoint.runUntilDrained(): Unit`

## Internal Structure

### `downloads/config`

- Purpose: own persisted download settings and their readiness.
- What it owns:
  - output-directory record,
  - concurrency record,
  - output-directory usability checks,
  - hydration of settings state from storage.
- Queue rows do not own or pin the saved output-directory URI; fresh writes read the current saved directory here when a new reservation is needed.
- What it must not own:
  - queue rows,
  - output reservation,
  - worker loops.
- Sibling interaction:
  - provides readiness to `downloads/queue`, `ui/settings`, and `app/`.
- What may cross this seam:
  - `DownloadSettingsState`,
  - `DownloadSettingsReadiness`,
  - `DownloadSettingsDraft`.
- What may not cross this seam:
  - queue or attempt state.

### `downloads/queue`

- Purpose: own the durable queue ledger and the canonical state machine.
- What it owns:
  - `taskId` assignment,
  - enqueue order,
  - state transitions,
  - attempt counts,
  - claims and claim leases,
  - retry schedule,
  - `Preparing` metadata,
  - transfer checkpoints,
  - persisted pending pause/cancel actions,
  - row visibility,
  - queue-owned persisted reservations and final-output records.
- What it must not own:
  - link resolution algorithms,
  - output writes,
  - notification channel APIs.
- Sibling interaction:
  - uses `downloads/attempts` only through attempt outcomes and progress updates,
  - uses `downloads/output` for restart cleanup and reservation state,
  - uses `downloads/work` for live control signaling.
- What may cross this seam:
  - `QueueTask`,
  - `QueueTaskState`,
  - `QueueClaim`,
  - `QueueActionCommand`,
  - `DownloadsProjection`.
- What may not cross this seam:
  - temp files,
  - provider clients,
  - extraction runtime handles.

### `downloads/attempts`

- Purpose: run one attempt for one claimed row.
- What it owns:
  - stage-by-stage attempt execution,
  - preparing-time provider polling,
  - local transfer loops,
  - pause or cancel observation during running work.
- What it must not own:
  - canonical state mutation,
  - retry policy,
  - final output semantics.
- Sibling interaction:
  - receives claims from `downloads/work`,
  - reports progress and outcomes back to `downloads/queue`,
  - writes only through `downloads/output`.
- What may cross this seam:
  - `AttemptOutcome`,
  - `PreparingMetadata`,
  - `TransferCheckpoint`.
- What may not cross this seam:
  - direct ledger mutation,
  - notification updates,
  - settings writes.

### `downloads/output`

- Purpose: own deterministic output identity, temp writes, finalization, extraction ordering, and restart cleanup.
- What it owns:
  - output reservation,
  - reservation and final-output identity shape,
  - bound output-root identity for one reservation,
  - direct-save final names,
  - extraction-root reservation,
  - extraction manifest reservation,
  - collision handling,
  - rename application,
  - archive cleanup,
  - restart cleanup preconditions.
- What it must not own:
  - queue state transitions,
  - retry policy,
  - provider acquisition.
- Sibling interaction:
  - receives queue-owned task rows plus queue-owned persisted reservation/output data as input,
  - returns reservation updates and final-output records for `downloads/queue` to persist.
- What may cross this seam:
  - `OutputReservation`,
  - `FinalOutputRecord`,
  - cleanup results.
- What may not cross this seam:
  - queue-state mutation,
  - worker claims.

### `downloads/work`

- Purpose: own background entrypoints, runtime claim limits, queue wake-up scheduling, and live attempt control.
- What it owns:
  - worker startup,
  - runtime max-concurrency reads,
  - queue-global auth gating for new provider-dependent work,
  - app-launch recovery wake,
  - claim-limit derivation,
  - queue wake-up requests on enqueue, resume, manual retry, restart, auth repair, app launch, and retry-at deadlines,
  - claim dispatch,
  - live pause or cancel signaling,
  - notification projection posting.
- What it must not own:
  - canonical queue state,
  - output policy,
  - source or provider rules.
- Sibling interaction:
  - claims work from `downloads/queue`,
  - delegates actual execution to `downloads/attempts`,
  - posts projection snapshots from `downloads/queue`.
- What may cross this seam:
  - `QueueClaim`,
  - live control signals,
  - notification snapshots.
- What may not cross this seam:
  - direct store mutation outside public queue APIs.

## Internal Files

### `DownloadsFacade.kt`
- Internal area: `downloads/root`
- Purpose: expose the only public API for `app/` and `ui/`.
- Responsibility: delegate to config, queue, and work seams without leaking stores.
- Depends on: `DownloadSettingsService`, `QueueService`, `QueueSummaryProjector`
- Must not depend on: UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
class DownloadsFacade(
    private val settingsService: DownloadSettingsService,
    private val queueService: QueueService,
    private val summaryProjector: QueueSummaryProjector,
    private val workScheduler: WorkScheduler
) {
    fun observeSettings(): StateFlow<DownloadSettingsState>
    fun observeSettingsReadiness(): StateFlow<DownloadSettingsReadiness>
    suspend fun readSettingsReadiness(): DownloadSettingsReadiness
    suspend fun updateSettings(draft: DownloadSettingsDraft): Result<Unit>
    suspend fun enqueue(inputs: List<QueueTaskInput>): EnqueueResult
    fun observeDownloadsProjection(): StateFlow<DownloadsProjection>
    fun observeActiveDownloadsFlag(): StateFlow<Boolean>
    suspend fun performAction(command: QueueActionCommand): Result<Unit>
    suspend fun clearHistory(includeFailed: Boolean): Result<Unit>
    suspend fun requestAppLaunchRecovery(): Result<Unit>
}
```

### `DownloadSettingsStore.kt`
- Internal area: `downloads/config`
- Purpose: own the persisted download-settings record in shared `ConfigStore`.
- Responsibility: hydrate settings state from storage and persist only download-scoped settings.
- Depends on: shared `ConfigStore`
- Must not depend on: queue services or output services
- Visibility: `public`
- Key types/functions:

```kotlin
data class DownloadSettingsDraft(
    val outputDirectoryUri: String,
    val maxConcurrency: Int
)

data class DownloadSettingsState(
    val outputDirectoryUri: String?,
    val maxConcurrency: Int
)

data class DownloadSettingsReadiness(
    val isUsable: Boolean,
    val brokenReason: String?
)

interface DownloadSettingsStore {
    suspend fun read(): DownloadSettingsState?
    suspend fun write(state: DownloadSettingsState): Result<Unit>
}
```

### `DownloadSettingsService.kt`
- Internal area: `downloads/config`
- Purpose: validate and expose download settings.
- Responsibility: keep `observeSettings()` and `observeSettingsReadiness()` hydrated from persisted storage.
- Depends on: `DownloadSettingsStore`, output-directory access boundary
- Must not depend on: queue services
- Visibility: `internal`
- Key types/functions:

```kotlin
class DownloadSettingsService(
    private val store: DownloadSettingsStore,
    private val outputAccess: OutputDirectoryAccess
) {
    fun observeState(): StateFlow<DownloadSettingsState>
    fun observeReadiness(): StateFlow<DownloadSettingsReadiness>
    suspend fun readState(): DownloadSettingsState
    suspend fun readReadiness(): DownloadSettingsReadiness
    suspend fun update(draft: DownloadSettingsDraft): Result<Unit>
}
```

### `QueueModels.kt`
- Internal area: `downloads/queue`
- Purpose: define the durable queue contract.
- Responsibility: keep enqueue-time identity, execution context, action requests, and state metadata explicit.
- Depends on: public models from `source/`, `realdebrid/`, and `remotezip/`
- Must not depend on: UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
@JvmInline
value class TaskId(val value: String)

data class SourceQueueMetadata(
    val entryDisplayName: String,
    val partLabel: String?,
    val providerFileId: String?
)

data class NamingIntent(
    val applyRename: Boolean,
    val renameRule: RenameRule?
)

data class StorageTargetContext(
    val subfolder: String
)

sealed interface QueueExecutionContext {
    data class StandardFile(
        val selectionIntent: TorrentFileSelectionIntent
    ) : QueueExecutionContext

    data class ArchiveEntry(
        val preparationKey: ArchivePreparationKey,
        val archiveEntryIdentity: ArchiveEntryIdentity
    ) : QueueExecutionContext
}

data class QueueTaskInput(
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId,
    val selectedItemId: SelectableItemId,
    val originalDisplayName: String,
    val originalSizeBytes: Long?,
    val sourceMetadata: SourceQueueMetadata,
    val namingIntent: NamingIntent,
    val unarchiveIntent: QueueUnarchiveIntent,
    val storageTarget: StorageTargetContext,
    val executionContext: QueueExecutionContext
)

data class QueueTask(
    val taskId: TaskId,
    val createdAt: Instant,
    val snapshotId: SnapshotId,
    val entryId: SourceEntryId,
    val selectedItemId: SelectableItemId,
    val originalDisplayName: String,
    val originalSizeBytes: Long?,
    val sourceMetadata: SourceQueueMetadata,
    val namingIntent: NamingIntent,
    val unarchiveIntent: QueueUnarchiveIntent,
    val storageTarget: StorageTargetContext,
    val executionContext: QueueExecutionContext
)

sealed interface QueueTaskState {
    data object Queued : QueueTaskState
    data class Resolving(val enteredAt: Instant) : QueueTaskState
    data class Preparing(val metadata: PreparingMetadata) : QueueTaskState
    data class Running(val checkpoint: TransferCheckpoint) : QueueTaskState
    data class Paused(val checkpoint: TransferCheckpoint) : QueueTaskState
    data class RetryScheduled(val retryAt: Instant, val attemptIndex: Int) : QueueTaskState
    data object Completed : QueueTaskState
    data class Failed(val reason: FailureReason) : QueueTaskState
    data class Cancelled(val reason: String?) : QueueTaskState
}

enum class QueueVisibility {
    VISIBLE,
    HIDDEN
}

enum class PendingQueueAction {
    PAUSE,
    RESUME,
    CANCEL
}

enum class ControlSignal {
    NONE,
    PAUSE,
    CANCEL
}

sealed interface QueueActionCommand {
    val taskId: TaskId

    data class Pause(override val taskId: TaskId) : QueueActionCommand
    data class Resume(override val taskId: TaskId) : QueueActionCommand
    data class Cancel(override val taskId: TaskId) : QueueActionCommand
    data class Retry(override val taskId: TaskId) : QueueActionCommand
    data class Restart(override val taskId: TaskId) : QueueActionCommand
}

data class QueueActionRequest(
    val taskId: TaskId,
    val action: PendingQueueAction,
    val requestedAt: Instant
)

data class ClaimLease(
    val claimedAt: Instant,
    val leaseExpiresAt: Instant
)

data class PreparingMetadata(
    val enteredAt: Instant,
    val timeoutAt: Instant,
    val lastProviderStatus: String?,
    val lastProviderProgress: Double?,
    val resumeMarker: ProviderResumeMarker?
)

data class TransferCheckpoint(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val lastPersistedAt: Instant,
    val tempFileToken: TempFileToken?,
    val resumeByteOffset: Long
)

sealed interface FailureReason {
    data class AuthRequired(val message: String) : FailureReason
    data class ProviderFailure(val stage: String, val message: String) : FailureReason
    data class OutputFailure(val message: String) : FailureReason
    data class DirectoryAccessFailure(val message: String) : FailureReason
}
```

### `DownloadLedgerStore.kt`
- Internal area: `downloads/queue`
- Purpose: own the durable queue ledger.
- Responsibility: persist rows, row timestamps, state, claims, action requests, visibility, checkpoints, reservations, and outputs transactionally where required.
- Depends on: database or file-backed ledger boundary
- Must not depend on: provider clients or UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
data class QueueClaim(
    val task: QueueTask,
    val state: QueueTaskState,
    val lease: ClaimLease,
    val attemptCount: Int,
    val pendingAction: QueueActionRequest?,
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>
)

data class QueueRowRecord(
    val task: QueueTask,
    val updatedAt: Instant,
    val state: QueueTaskState,
    val visibility: QueueVisibility,
    val attemptCount: Int,
    val lease: ClaimLease?,
    val pendingAction: QueueActionRequest?,
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>
)

interface DownloadLedgerStore {
    suspend fun insertTasks(tasks: List<QueueTask>): Result<Unit>
    suspend fun claimRunnableTasks(now: Instant, limit: Int): List<QueueClaim>
    suspend fun releaseClaim(taskId: TaskId): Result<Unit>
    suspend fun resumePausedTask(taskId: TaskId): Result<Unit>
    suspend fun resetForManualRetry(taskId: TaskId): Result<Unit>
    suspend fun restartTask(taskId: TaskId): Result<Unit>
    suspend fun persistState(taskId: TaskId, state: QueueTaskState): Result<Unit>
    suspend fun persistActionRequest(taskId: TaskId, request: QueueActionRequest?): Result<Unit>
    suspend fun persistTransferCheckpoint(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit>
    suspend fun persistReservation(taskId: TaskId, reservation: OutputReservation): Result<Unit>
    suspend fun persistFinalOutputs(taskId: TaskId, outputs: List<FinalOutputRecord>): Result<Unit>
    suspend fun completeTask(taskId: TaskId, outputs: List<FinalOutputRecord>): Result<Unit>
    suspend fun readNextRetryAt(): Instant?
    suspend fun requestDispatch(): Result<Long>
    suspend fun acknowledgeDispatchStart(): Result<Long>
    suspend fun hasPendingDispatch(): Boolean
    suspend fun readRow(taskId: TaskId): QueueRowRecord?
    suspend fun readRows(includeHidden: Boolean = true): List<QueueRowRecord>
    fun observeRows(includeHidden: Boolean = true): Flow<List<QueueRowRecord>>
    suspend fun hideTerminalRowsAtomically(taskIds: List<TaskId>): Result<Unit>
}
```

### `QueueService.kt`
- Internal area: `downloads/queue`
- Purpose: own the canonical queue state machine.
- Responsibility: create rows, accept user actions, persist progress, settle attempt outcomes so durable stop requests win over retry scheduling, wake background work when the queue becomes runnable, and enforce visibility-only clear history.
- Depends on: `DownloadLedgerStore`, `ExecutionControlRegistry`, `OutputCleanupService`, `WorkScheduler`, `Clock`
- Must not depend on: provider clients or UI classes
- Visibility: `internal`
- Key types/functions:

```kotlin
sealed interface EnqueueResult {
    data class Enqueued(val taskIds: List<TaskId>) : EnqueueResult
    data class EnqueuedPendingDispatch(val taskIds: List<TaskId>, val message: String) : EnqueueResult
    data class Rejected(val message: String) : EnqueueResult
    data class Failed(val message: String) : EnqueueResult
}

class QueueService(
    private val ledgerStore: DownloadLedgerStore,
    private val executionControlRegistry: ExecutionControlRegistry,
    private val outputCleanupService: OutputCleanupService,
    private val workScheduler: WorkScheduler,
    private val clock: Clock
) {
    suspend fun enqueue(inputs: List<QueueTaskInput>): EnqueueResult
    suspend fun claimRunnableTasks(now: Instant, limit: Int): List<QueueClaim>
    suspend fun releaseClaim(taskId: TaskId): Result<Unit>
    suspend fun resumePausedTask(taskId: TaskId): Result<Unit>
    suspend fun resetForManualRetry(taskId: TaskId): Result<Unit>
    suspend fun restartTask(taskId: TaskId): Result<Unit>

    suspend fun performAction(command: QueueActionCommand): Result<Unit> {
        val row = ledgerStore.readRow(command.taskId) ?: return Result.failure(IllegalStateException("Unknown task"))

        return when (command) {
            is QueueActionCommand.Pause -> {
                when (row.state) {
                    is QueueTaskState.Running -> {
                        // Contract: only live Running work pauses through a durable pending action plus worker acknowledgement.
                        val persisted = ledgerStore.persistActionRequest(
                            command.taskId,
                            QueueActionRequest(command.taskId, PendingQueueAction.PAUSE, clock.instant())
                        )
                        if (persisted.isFailure) persisted else executionControlRegistry.signal(command.taskId, ControlSignal.PAUSE)
                    }
                    else -> Result.failure(IllegalStateException("Pause is valid only for Running rows"))
                }
            }
            is QueueActionCommand.Cancel -> when (row.state) {
                QueueTaskState.Queued,
                is QueueTaskState.Resolving,
                is QueueTaskState.RetryScheduled,
                is QueueTaskState.Paused -> {
                    // Contract: non-live states cancel immediately without waiting for a worker handshake.
                    ledgerStore.persistActionRequest(command.taskId, null)
                        .fold(
                            onSuccess = { ledgerStore.persistState(command.taskId, QueueTaskState.Cancelled(reason = null)) },
                            onFailure = { Result.failure(it) }
                        )
                }
                is QueueTaskState.Preparing,
                is QueueTaskState.Running -> {
                    val persisted = ledgerStore.persistActionRequest(
                        command.taskId,
                        QueueActionRequest(command.taskId, PendingQueueAction.CANCEL, clock.instant())
                    )
                    if (persisted.isFailure) persisted else executionControlRegistry.signal(command.taskId, ControlSignal.CANCEL)
                }
                is QueueTaskState.Completed,
                is QueueTaskState.Failed,
                is QueueTaskState.Cancelled -> Result.success(Unit)
            }
            is QueueActionCommand.Resume -> {
                val resumed = resumePausedTask(command.taskId)
                if (resumed.isSuccess) workScheduler.requestWake(WorkWakeReason.RESUME) else resumed
            }
            is QueueActionCommand.Retry -> {
                val reset = resetForManualRetry(command.taskId)
                if (reset.isSuccess) workScheduler.requestWake(WorkWakeReason.MANUAL_RETRY) else reset
            }
            is QueueActionCommand.Restart -> {
                val cleanup = outputCleanupService.cleanupForRestart(
                    OutputCleanupScope(
                        reservation = row.reservation,
                        finalOutputs = row.finalOutputs
                    )
                )
                if (cleanup.isFailure) cleanup else {
                    val restarted = restartTask(command.taskId)
                    if (restarted.isSuccess) workScheduler.requestWake(WorkWakeReason.RESTART) else restarted
                }
            }
        }
    }

    suspend fun recordPreparing(taskId: TaskId, metadata: PreparingMetadata): Result<Unit>
    suspend fun recordRunning(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit>
    suspend fun enterFinalization(
        taskId: TaskId,
        checkpoint: TransferCheckpoint,
        cursor: FinalizationCursor
    ): Result<Unit>
    suspend fun acknowledgePause(taskId: TaskId, checkpoint: TransferCheckpoint): Result<Unit>
    suspend fun acknowledgeCancel(taskId: TaskId, checkpoint: TransferCheckpoint?): Result<Unit>
    suspend fun settleAttemptOutcome(
        taskId: TaskId,
        outcome: AttemptOutcome,
        attemptIndex: Int
    ): Result<Unit>

    suspend fun clearHistory(includeFailed: Boolean): Result<Unit> {
        // Contract: this is visibility-only and all-or-nothing.
        // If the transaction fails, row visibility stays unchanged.
    }
}
```

### `QueueRecoveryPolicy.kt`
- Internal area: `downloads/queue`
- Purpose: decide how interrupted claims recover.
- Responsibility: preserve `Preparing` timing and resume checkpoints whenever recovery is safe.
- Depends on: queue-claimed row data, `OutputReservationService`
- Must not depend on: worker loops
- Visibility: `internal`
- Key types/functions:

```kotlin
sealed interface RecoveryDecision {
    data class HonorPendingPause(val checkpoint: TransferCheckpoint) : RecoveryDecision
    data class HonorPendingCancel(val checkpoint: TransferCheckpoint?) : RecoveryDecision
    data class ResumePreparing(val metadata: PreparingMetadata) : RecoveryDecision
data class ResumeRunning(
        val checkpoint: TransferCheckpoint,
        val reservation: OutputReservation
    ) : RecoveryDecision
    data class ResumeFinalization(
        val checkpoint: TransferCheckpoint,
        val reservation: OutputReservation,
        val cursor: FinalizationCursor
    ) : RecoveryDecision
    data class Requeue(val state: QueueTaskState) : RecoveryDecision
}

class QueueRecoveryPolicy(
    private val outputReservationService: OutputReservationService
) {
    suspend fun decide(claim: QueueClaim): RecoveryDecision
}
```

### `WorkScheduler.kt`
- Internal area: `downloads/work`
- Purpose: own runtime claim limits and worker wake-up scheduling.
- Responsibility: read max concurrency from persisted download settings, translate it into claim limits, own the queue-global auth gate, wake workers on queue mutations or app-launch recovery, and schedule the next deferred wake for retry deadlines or stale-claim recovery.
- Responsibility: read max concurrency from persisted download settings, translate it into claim limits, own the queue-global auth gate, treat runnable-work dispatch as durable queue-owned generation debt until a worker acknowledges it, wake workers on queue mutations or app-launch recovery, and schedule the next deferred wake for retry deadlines or stale-claim recovery.
- Depends on: `DownloadSettingsService`, `DownloadLedgerStore`, `RealDebridFacade`, worker-launch boundary, `Clock`
- Must not depend on: provider clients or UI classes
- Visibility: `internal`
- Key types/functions:

```kotlin
enum class WorkWakeReason {
    ENQUEUE,
    RESUME,
    MANUAL_RETRY,
    RESTART,
    RETRY_AT_REACHED,
    APP_LAUNCH_RECOVERY,
    AUTH_RECOVERED
}

data class QueueWorkGateState(
    val authBlocked: Boolean,
    val reason: String?
)

interface WorkerLauncher {
    suspend fun launchNow(): Result<Unit>
    suspend fun launchAt(instant: Instant): Result<Unit>
}

class WorkScheduler(
    private val settingsService: DownloadSettingsService,
    private val ledgerStore: DownloadLedgerStore,
    private val realDebridFacade: RealDebridFacade,
    private val workerLauncher: WorkerLauncher,
    private val clock: Clock
) {
    fun observeGate(): StateFlow<QueueWorkGateState>
    suspend fun readGate(): QueueWorkGateState
    suspend fun synchronizeAuthRecovery(): Result<Unit>
    suspend fun readClaimLimit(): Int
    suspend fun requestWake(reason: WorkWakeReason): Result<Unit>
    suspend fun scheduleNextRetryWake(): Result<Unit>
}
```

### `ExecutionControlRegistry.kt`
- Internal area: `downloads/work`
- Purpose: own live pause or cancel signals for claimed tasks.
- Responsibility: bridge queue action requests into running attempt loops without making the worker the source of truth.
- Depends on: Kotlin coroutines only
- Must not depend on: ledger stores
- Visibility: `internal`
- Key types/functions:

```kotlin
class ExecutionControlRegistry {
    fun register(taskId: TaskId): ControlHandle
    fun unregister(taskId: TaskId)
    fun signal(taskId: TaskId, signal: ControlSignal): Result<Unit>
}

interface ControlHandle {
    suspend fun awaitSignal(): ControlSignal
    fun current(): ControlSignal
}
```

### `StandardAttemptRunner.kt`
- Internal area: `downloads/attempts`
- Purpose: execute one standard-file queue row.
- Responsibility: start or resume provider acquisition through the public `realdebrid/` seam, persist `Preparing` metadata updates while `downloads/` owns the timeout window, resolve the ready link only after acquisition finishes, stream bytes, and stop promptly on pause or cancel signals.
- Transfer policy: unrestricted file transfers use a dedicated OkHttp client with a short connect timeout, a stall-tolerant read timeout, and no overall call timeout so multi-GB downloads are not killed merely for taking a long time end-to-end.
- Depends on: `RealDebridFacade`, `OutputReservationService`, `OutputFinalizer`, `QueueService`
- Must not depend on: ledger stores directly
- Visibility: `internal`
- Key types/functions:

```kotlin
sealed interface AttemptOutcome {
    data class Completed(
        val reservation: OutputReservation,
        val outputs: List<FinalOutputRecord>
    ) : AttemptOutcome
    data class Paused(val checkpoint: TransferCheckpoint) : AttemptOutcome
    data class Cancelled(val checkpoint: TransferCheckpoint?) : AttemptOutcome
    data class Failed(
        val reason: FailureReason,
        val retryable: Boolean = true
    ) : AttemptOutcome
}

class StandardAttemptRunner(
    private val realDebridFacade: RealDebridFacade,
    private val outputReservationService: OutputReservationService,
    private val outputFinalizer: OutputFinalizer,
    private val queueService: QueueService,
    private val clock: Clock
) {
    suspend fun run(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision,
        controlHandle: ControlHandle
    ): AttemptOutcome
}
```

### `ArchiveEntryAttemptRunner.kt`
- Internal area: `downloads/attempts`
- Purpose: execute one archive-selection queue row.
- Responsibility: resolve the shared outer-ZIP preparation record to a ready container URL, reopen the selected internal entry by stable identity, resume selected-entry copy from the saved byte offset when possible, and copy only that entry into the reserved artifact.
- Depends on: `RealDebridFacade`, `RemoteZipFacade`, `OutputReservationService`, `OutputFinalizer`, `QueueService`
- Must not depend on: ledger stores directly or full-download fallback logic
- Visibility: `internal`
- Key types/functions:

```kotlin
class ArchiveEntryAttemptRunner(
    private val realDebridFacade: RealDebridFacade,
    private val remoteZipFacade: RemoteZipFacade,
    private val outputReservationService: OutputReservationService,
    private val outputFinalizer: OutputFinalizer,
    private val queueService: QueueService,
    private val clock: Clock
) {
    suspend fun run(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision,
        controlHandle: ControlHandle
    ): AttemptOutcome
}
```

Archive-entry execution persists the queue-stored archive-preparation key as its durable outer-container contract. At run time it resolves that key through the shared source-owned archive-preparation service, reuses the saved Real-Debrid resume marker when possible, and then copies only the selected internal file through `remotezip/`.

### `OutputRootResolver.kt`
- Internal area: `downloads/output`
- Purpose: bind fresh write attempts to the current saved output directory.
- Responsibility: keep queue rows portable across directory repair while preserving an existing reservation's bound root for in-place resume.
- Depends on: `DownloadSettingsService`, `OutputDirectoryAccess`
- Must not depend on: queue-state mutation
- Visibility: `internal`
- Key types/functions:

```kotlin
data class OutputRootBinding(
    val outputDirectoryUri: String,
    val boundAt: Instant
)

class OutputRootResolver(
    private val settingsService: DownloadSettingsService,
    private val outputAccess: OutputDirectoryAccess,
    private val clock: Clock
) {
    suspend fun resolveFreshBinding(): Result<OutputRootBinding>
}
```

### `OutputReservationService.kt`
- Internal area: `downloads/output`
- Purpose: reserve deterministic output identity before writes begin.
- Responsibility: choose direct-save targets early by applying the queue row's `NamingIntent` before collision handling, reserve extraction roots early, preserve the source artifact extension on temp artifacts so local unarchive can reopen the completed file by type, and reserve extraction manifests before extraction writes final files.
- Depends on: `OutputFilesystem`, `OutputRootResolver`, `Clock`
- Must not depend on: queue-state mutation
- Visibility: `internal`
- Key types/functions:

```kotlin
@JvmInline
value class ReservationId(val value: String)

@JvmInline
value class FinalOutputId(val value: String)

@JvmInline
value class TempFileToken(val value: String)

data class ReservedDirectOutput(
    val finalOutputId: FinalOutputId,
    val finalPath: Path,
    val displayName: String
)

data class ReservedExtractionOutput(
    val finalOutputId: FinalOutputId,
    val archiveEntryPath: String,
    val finalPath: Path,
    val displayName: String
)

data class OutputReservation(
    val reservationId: ReservationId,
    val boundOutputDirectoryUri: String,
    val artifact: ReservedArtifact,
    val directOutput: ReservedDirectOutput?,
    val extractionPlan: List<ReservedExtractionOutput>
)

data class ReservedArtifact(
    val originalDisplayName: String,
    val tempArtifact: Path,
    val extractionRoot: Path,
    val handling: ReservedArtifactHandling
)

enum class ReservedArtifactHandling {
    DIRECT_SAVE,
    LOCAL_UNARCHIVE
}

sealed interface ArtifactRecoveryDisposition {
    data object CannotResume : ArtifactRecoveryDisposition
    data class ResumeTransfer(
        val reservation: OutputReservation,
        val safeResumeOffset: Long
    ) : ArtifactRecoveryDisposition
    data class ResumeFinalization(
        val reservation: OutputReservation,
        val cursor: FinalizationCursor
    ) : ArtifactRecoveryDisposition
}

class OutputReservationService(
    private val outputFilesystem: OutputFilesystem,
    private val outputRootResolver: OutputRootResolver,
    private val clock: Clock
) {
    suspend fun reserve(task: QueueTask): Result<OutputReservation>
    suspend fun expandExtractionPlan(
        task: QueueTask,
        reservation: OutputReservation,
        extractedEntries: List<ExtractionManifestEntry>
    ): Result<OutputReservation>
    suspend fun inspectRecoveryDisposition(
        reservation: OutputReservation?,
        checkpoint: TransferCheckpoint?,
        persistedCursor: FinalizationCursor?
    ): ArtifactRecoveryDisposition
}
```

- `reserve(task)` persists the selected artifact's handling mode up front, creates a direct-save reservation only when the row will actually direct-save, and applies rename before collision suffixing when reserving that final output identity.
- `expandExtractionPlan(...)` appends only newly discovered extracted-output reservations pass by pass, reuses existing `archiveEntryPath` reservations across retries or recovery, applies rename only while reserving final non-archive outputs, and never renames an archive filename before extraction or recursive handoff.
- `inspectRecoveryDisposition(...)` classifies local artifact state for queue recovery, including the proven handoff case where a complete reserved artifact can enter local finalization even if the initial cursor write did not land before interruption.

### `OutputFinalizer.kt`
- Internal area: `downloads/output`
- Purpose: convert a reserved artifact into final outputs.
- Responsibility: enforce direct-save versus unarchive ordering, use the names already reserved by `OutputReservationService`, apply rename only to final non-archive outputs, and delete consumed archives only after successful extraction.
- Finalization mode must come from the persisted reservation artifact handling, not from a temporary filename extension.
- Depends on: `OutputReservationService`, `ArchiveExtractionController`, `OutputFilesystem`
- Must not depend on: queue-state mutation
- Visibility: `internal`
- Key types/functions:

```kotlin
data class FinalOutputRecord(
    val finalOutputId: FinalOutputId,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long?
)

data class OutputFinalizationResult(
    val reservation: OutputReservation,
    val outputs: List<FinalOutputRecord>
)

class OutputFinalizer(
    private val reservationService: OutputReservationService,
    private val extractionController: ArchiveExtractionController,
    private val outputFilesystem: OutputFilesystem
) {
    fun createInitialCursor(reservation: OutputReservation, artifact: Path): FinalizationCursor
    suspend fun finalize(
        task: QueueTask,
        reservation: OutputReservation,
        artifact: Path
    ): Result<OutputFinalizationResult> {
        if (reservation.artifact.handling == ReservedArtifactHandling.DIRECT_SAVE) {
            // Contract: direct-save path is already reserved before transfer begins.
            val outputs = outputFilesystem.promoteDirectArtifact(
                artifact = artifact,
                reserved = reservation.directOutput!!
            ).getOrElse { return Result.failure(it) }
            return Result.success(OutputFinalizationResult(reservation = reservation, outputs = outputs))
        }

        // Contract:
        // - recursive extraction expands reservations pass by pass before each pass writes final files
        // - rename applies only to final non-archive outputs
        // - archive files are deleted only after their own extraction pass succeeds
        // - successful earlier extracted outputs remain if a later extraction step fails
        return extractPassByPass(task = task, initialReservation = reservation, artifact = artifact)
    }
}
```

### `ArchiveExtractionController.kt`
- Internal area: `downloads/output`
- Purpose: own bounded archive extraction passes.
- Responsibility: flatten directories, honor reserved output names, and perform optional recursive extraction in bounded additional passes.
- Depends on: archive runtime boundary, `OutputFilesystem`
- Must not depend on: queue-state mutation
- Visibility: `internal`
- Key types/functions:

```kotlin
data class ArchiveManifest(
    val finalEntryPaths: List<ExtractionManifestEntry>
)

class ArchiveExtractionController(
    private val archiveRuntime: ArchiveRuntime,
    private val outputFilesystem: OutputFilesystem
) {
    suspend fun inspectManifest(
        archiveFile: Path,
        recursive: Boolean,
        lineage: String
    ): ArchiveManifest
    suspend fun extract(
        archiveFile: Path,
        extractionRoot: Path,
        reservedOutputs: List<ReservedExtractionOutput>,
        recursive: Boolean,
        lineage: String
    ): Result<ArchivePassExecution>
}
```

### `OutputCleanupService.kt`
- Internal area: `downloads/output`
- Purpose: own restart cleanup.
- Responsibility: delete the queue-provided prior output set, reserved temp artifact, extraction root, and any reserved-but-not-finalized outputs for one row or reject restart if cleanup cannot complete safely.
- Cleanup must only delete outputs that the reservation actually owns; extract-only rows do not reserve or delete a direct-save destination.
- Depends on: `OutputFilesystem`
- Must not depend on: queue-state mutation
- Visibility: `internal`
- Key types/functions:

```kotlin
data class OutputCleanupScope(
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>
)

class OutputCleanupService(
    private val outputFilesystem: OutputFilesystem
) {
    suspend fun cleanupForRestart(scope: OutputCleanupScope): Result<Unit>
}
```

### `DownloadWorkerEntryPoint.kt`
- Internal area: `downloads/work`
- Purpose: own worker runtime entrypoints.
- Responsibility: ask `WorkScheduler` for the runtime claim limit and auth-gate state, keep up to that many active attempts running by refilling freed slots from the runnable queue, apply recovery decisions, register live control handles, dispatch the correct attempt runner, commit outcomes through `QueueService`, and schedule the next retry-at wake when work remains deferred.
- Depends on: `QueueService`, `QueueRecoveryPolicy`, `WorkScheduler`, `ExecutionControlRegistry`, attempt runners
- Must not depend on: UI classes
- Visibility: `public`
- Key types/functions:

```kotlin
class DownloadWorkerEntryPoint(
    private val queueService: QueueService,
    private val recoveryPolicy: QueueRecoveryPolicy,
    private val workScheduler: WorkScheduler,
    private val executionControlRegistry: ExecutionControlRegistry,
    private val standardAttemptRunner: StandardAttemptRunner,
    private val archiveEntryAttemptRunner: ArchiveEntryAttemptRunner
) {
    suspend fun runOnce()
    suspend fun runUntilDrained()
}
```

### `QueueSummaryProjector.kt`
- Internal area: `downloads/queue`
- Purpose: project ledger rows into the Downloads-page view model.
- Responsibility: sort visible rows newest-first by `createdAt`, keep summary counters over visible rows only, expose `activeDownloads`, project canonical state and allowed actions for row menus, and project the explicit details required by Downloads row details.
- Depends on: `DownloadLedgerStore`
- Must not depend on: UI classes or notification APIs
- Visibility: `internal`
- Key types/functions:

```kotlin
data class QueueSummary(
    val completed: Int,
    val total: Int,
    val failed: Int,
    val cancelled: Int
)

enum class QueuePresentationState {
    QUEUED,
    RESOLVING,
    PREPARING,
    RUNNING,
    PAUSED,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class QueueActionKind {
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
    RESTART
}

data class DownloadRowViewState(
    val taskId: TaskId,
    val originalDisplayName: String,
    val state: QueuePresentationState,
    val stateLabel: String,
    val allowedActions: Set<QueueActionKind>,
    val progressLabel: String?,
    val createdAt: Instant,
    val details: DownloadRowDetailsViewState
)

data class DownloadRowDetailsViewState(
    val sourceEntry: String,
    val outputSubfolder: String?,
    val outputSummary: String,
    val currentState: String,
    val updatedAt: Instant,
    val originalSizeBytes: Long?,
    val failureReason: String?,
    val partLabel: String?,
    val attemptCount: Int?,
    val preparing: PreparingRowDetailsViewState?,
    val transfer: TransferRowDetailsViewState?
)

data class PreparingRowDetailsViewState(
    val enteredAt: Instant,
    val timeoutAt: Instant,
    val lastProviderStatus: String?,
    val lastProviderProgress: Double?
)

data class TransferRowDetailsViewState(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val progressPercentLabel: String
)

data class DownloadsProjection(
    val summary: QueueSummary,
    val rows: List<DownloadRowViewState>,
    val activeDownloads: Boolean
)

class QueueSummaryProjector(
    private val ledgerStore: DownloadLedgerStore
) {
    fun observeProjection(): StateFlow<DownloadsProjection>
}
```

### `QueueNotificationPresenter.kt`
- Internal area: `downloads/work`
- Purpose: translate queue projection into active or completion notifications.
- Responsibility: follow the exact notification contract without taking over queue-state authority, using a posting boundary that ensures required Android notification channels exist before post or update.
- Depends on: `NotificationApi`
- Must not depend on: ledger mutation
- Visibility: `internal`
- Key types/functions:

```kotlin
class QueueNotificationPresenter(
    private val notificationApi: NotificationApi
) {
    fun present(projection: DownloadsProjection) {
        // Contract:
        // - while any row is Running, use byte-first progress text
        // - while active work exists but none is Running, use short status-only text
        // - when active work falls to zero, post a completion notification
        // - title is "Downloads complete" only if no failures and no cancellations
        // - otherwise title is "Downloads finished"
    }
}
```

- `NotificationApi` owns Android notification posting mechanics, including creating or ensuring required notification channels before any post or update.
- One acceptable runtime wiring is a projection observer started by `DownloadsFacade.create(...)` that forwards `QueueSummaryProjector.observeProjection()` snapshots into `QueueNotificationPresenter.present(...)`.

## Key Flows

1. Enqueue:
   - `ui/files` submits `QueueTaskInput`.
   - `QueueService.enqueue` assigns `taskId`, stores the row, stores initial `Queued` state, and appends it to FIFO processing order.
   - The row stores output subfolder and naming policy, but not a permanent copy of the saved output-directory URI.
   - `QueueService.enqueue` requests a worker wake through `WorkScheduler`.
   - Queue projection later renders rows newest-first by `createdAt`.

2. Standard attempt execution:
   - `DownloadWorkerEntryPoint` asks `WorkScheduler` for the current claim limit derived from persisted max concurrency and treats it as the maximum number of simultaneous active attempts.
   - `DownloadWorkerEntryPoint` checks the queue-global auth gate before starting new provider-dependent work.
   - `DownloadLedgerStore` may return stale interrupted claims whose leases expired; `QueueRecoveryPolicy` then decides whether they resume or safely requeue.
   - A claimed row enters `Resolving` while `downloads/attempts` rehydrates row context, refreshes the current download unit, and decides whether provider acquisition is needed.
   - `StandardAttemptRunner` starts or resumes provider acquisition through `realdebrid/` only after `Resolving` finishes.
   - While provider acquisition is still waiting, `QueueService.recordPreparing` persists `PreparingMetadata`.
   - When acquisition reports ready links, `StandardAttemptRunner` resolves only the matching ready link into a final download unit.
   - Before bytes move, `OutputReservationService.reserve` binds the write to the current saved output directory and reserves deterministic output identity unless a resumable reservation already exists.
   - For direct-save work, that reservation consumes `NamingIntent`, applies rename before collision suffixing, and fixes the final output name before transfer starts.
   - During transfer, checkpoints persist at least every 3 seconds and on pause, cancel, failure, or completion.
   - `OutputFinalizer` expands and persists the extraction reservation pass by pass before each extraction pass writes final files, then commits final outputs incrementally as files land.
   - `QueueService.complete` stores `Completed` plus the canonical `FinalOutputRecord` set on the row envelope.

3. Archive-selection attempt execution:
   - `ArchiveEntryAttemptRunner` enters `Resolving` while it resolves the shared outer-ZIP preparation record to a fresh unrestricted container URL.
   - It reopens the selected internal entry through `remotezip/` by stable `ArchiveEntryIdentity`.
   - It copies only the selected entry into the reserved artifact and resumes from the saved offset when a checkpoint exists.
   - `OutputFinalizer` either direct-saves or extracts the copied artifact.
   - The outer ZIP is never a queue row or a final output.

4. Pause and cancel:
   - `QueueService.performAction(Pause)` is valid only for `Running` rows and persists a `QueueActionRequest` before signaling the live control registry.
   - `QueueService.performAction(Cancel)` immediately transitions non-live states such as `Queued`, `Retry Scheduled`, or `Paused` to `Cancelled`.
   - For live `Preparing` or `Running` work, cancel persists a `QueueActionRequest` before signaling the live control registry.
   - If cancel arrives after pause was already requested but before the worker acknowledges stop, the durable cancel request wins and the row becomes `Cancelled` after the final checkpoint instead of getting stuck in `Paused`.
   - The attempt loop observes the signal, stops network or file movement, writes a final checkpoint, and only then reports `Paused` or `Cancelled` when it can do so directly.
   - If a live attempt exits through a generic failure after cancel was already persisted but before that direct acknowledgement path completes, `QueueService.settleAttemptOutcome(...)` still honors the durable cancel request and converts the row to `Cancelled` instead of scheduling retry.
   - Queue state therefore never claims that work stopped before bytes actually stopped.

5. Recovery after interruption:
   - `QueueRecoveryPolicy` reads the claimed row, checkpoints, persisted pending action, and persisted reservation state.
   - On app launch after setup, `app/` requests `WorkWakeReason.APP_LAUNCH_RECOVERY` so stale claims are reconsidered explicitly instead of waiting for an incidental later wake.
   - If a pending cancel was persisted before interruption, recovery honors it without forcing new live work.
   - If a pending pause was persisted before interruption, recovery honors it from the last durable checkpoint.
   - If the row was in `Resolving`, stale-claim recovery requeues it to a fresh claim cycle unless a pending cancel overrides that path.
   - If the row was in `Preparing`, it preserves the original `enteredAt` and `timeoutAt` and resumes provider polling from the saved resume marker.
   - If the row was in `Running` and the temp artifact plus reservation are still valid, it resumes from the saved transfer checkpoint.
   - When transfer finished and local promotion or extraction was about to start, `downloads/attempts` uses one queue transition to enter finalization with the completed transfer checkpoint plus the initial finalization cursor.
   - If interruption happens after the reserved artifact is complete but before that transition lands, `downloads/output` can still prove completion from the reserved artifact and resume local finalization instead of reopening the network stream from EOF.
   - If recovery cannot resume safely, the row requeues without deleting partial data unless the user explicitly restarts it.

6. Restart:
   - `QueueService.performAction(Restart)` first passes retained cleanup scopes from earlier manual retries plus the current queue-owned persisted reservation and final-output records into `OutputCleanupService.cleanupForRestart(...)`.
   - If cleanup fails, restart does not proceed.
   - If cleanup succeeds, the row resets to a fresh retry cycle and fresh `Preparing` window when needed again.

7. Runtime wake scheduling:
   - `WorkScheduler` reads `DownloadSettingsState.maxConcurrency` at runtime and converts it into the next worker claim limit.
   - `QueueService` advances queue-owned dispatch generation under the ledger lock on enqueue, resume, manual retry, and restart, then asks `WorkScheduler` for the best-effort worker nudge.
   - `DownloadWorkerEntryPoint` acknowledges the current dispatch generation when it actually begins draining; a requested wake is not considered satisfied merely because the launcher accepted a request.
   - While the worker stays alive, it refills freed attempt slots immediately from queued runnable work instead of waiting for the entire previously claimed batch to finish.
   - `app/` requests worker wake on completed-setup app launch through `WorkWakeReason.APP_LAUNCH_RECOVERY`.
   - `WorkScheduler` watches token readiness and requests `WorkWakeReason.AUTH_RECOVERED` automatically when a previously broken token becomes usable again, while the worker drain loop also reconciles auth recovery synchronously before exit so the durable dispatch generation cannot be missed at the empty-queue boundary.
   - `QueueService.scheduleRetry(...)` persists `retryAt` and asks `WorkScheduler` to schedule the earliest deferred wake.
   - If a best-effort immediate wake fails after durable queue intake, `EnqueueResult` reports partial success through `EnqueuedPendingDispatch(...)` rather than pretending the queue insert failed.
   - If app-launch recovery or a user action runs before a stale claim lease expires, `WorkScheduler` schedules a follow-up wake for the earliest relevant lease-expiry boundary instead of depending on a later incidental wake.
  - While auth is broken, `downloads/work` blocks new provider-dependent claims; already-running local byte transfers may finish, stale pause/cancel recovery still honors durable control requests, and no new forward progress starts until the gate reopens.
   - `DownloadWorkerEntryPoint` asks `WorkScheduler` to reschedule the next deferred wake when deferred work remains after a run.

8. Clear history:
   - `QueueService.clearHistory(includeFailed)` targets only terminal visible rows.
   - Matching rows are hidden transactionally in one ledger operation.
   - Hidden rows stop contributing to visible summary counters and notification counters.
   - If the transaction fails, all targeted rows remain visible.

## Failure and Recovery Rules

- Queue rows always reflect real work, not requested work.
- Attempt count is durable row metadata and survives process death, retry scheduling, and projection.
- Pending pause or cancel actions are durable and must be honored by recovery if a process dies before a live worker acknowledges them.
- Claim leases are durable enough to make stale-claim recovery explicit; active work renews its lease through durable live-state mutations, and recovery never depends on an implied in-memory worker list.
- `Resolving` covers claim-start rehydration, auth-gate check, fresh link or outer-container resolution, and deciding whether provider acquisition is required.
- `Preparing` timing and timeout deadline survive interruption and recovery.
- Auto-retry uses the fixed retry windows defined by behavior and does not create new rows.
- Manual `Retry` resets retry history for the same row, clears the active execution reservation, and retains any prior cleanup scope so `Restart` can still delete outputs already created by that row.
- Manual `Restart` requires cleanup of the prior output set first.
- Queue rows keep stable output subfolder and naming context, but they do not pin the saved output-directory URI forever; fresh execution or retry reads the current saved directory, while recovery with a valid reservation stays pinned to that reservation's bound root.
- `downloads/work` owns the queue-global auth gate. When token readiness is broken, new provider-dependent claims stop; when token readiness becomes usable again, queue wake resumes automatically.
- Output reservation must choose direct-save names before transfer begins, using `NamingIntent` with rename applied before collision suffixing.
- Output reservation must also persist whether the selected artifact is a direct-save file or a supported local-unarchive file before transfer begins.
- Output cleanup and resume inspection operate on queue-owned persisted reservation/output data passed into `downloads/output`; `downloads/output` does not reach into the queue ledger by `taskId`.
- Extraction reservations must be expanded before extraction writes final files, and rename applies there only to final non-archive outputs.
- Finalization writes that were already persisted as row-owned outputs must remain on disk if the final completed-state ledger commit fails; recovery then retries completion from durable local state instead of deleting promoted outputs and drifting the ledger.
- Archive-entry resume uses `remotezip/` selected-entry copy with a saved byte offset; archive selection is not carved out of resume-in-place.
- Hidden history is visibility-only and persists across app restarts.
- Notification failure must not break queue execution.

## Testing Plan

### `DownloadSettingsServiceTest.kt`

- Scope: unit
- Covers:
  - settings hydration from storage
  - directory-readiness success and failure
  - concurrency validation
- Fixtures:
  - fake `DownloadSettingsStore`
  - fake `OutputDirectoryAccess`

### `QueueServiceTest.kt`

- Scope: unit
- Covers:
  - enqueue creates durable rows
  - live pause and cancel persist pending action requests instead of fake final states
  - cancel on non-live states transitions immediately without worker handshake
  - restart passes full persisted reservation and final-output context into cleanup
  - resume, manual retry, and restart transitions
  - clear-history applies atomically or not at all
- Fixtures:
  - fake `DownloadLedgerStore`
  - fake `ExecutionControlRegistry`
  - fake `OutputCleanupService`
  - fake `WorkScheduler`
  - test clock

### `QueueRecoveryPolicyTest.kt`

- Scope: unit
- Covers:
  - pending cancel is honored after interruption
  - pending pause is honored after interruption
  - interrupted resolving requeues cleanly after stale-claim reclamation
  - interrupted preparing preserves timeout metadata
  - interrupted running resumes from checkpoint when reservation is intact
  - unsafe resume falls back to safe requeue
- Fixtures:
  - fake `DownloadLedgerStore`
  - fake `OutputReservationService`

### `WorkSchedulerTest.kt`

- Scope: unit
- Covers:
  - runtime claim limit reads persisted max concurrency
  - enqueue, resume, manual retry, restart, and app-launch recovery request immediate wake
  - auth gate blocks new provider-dependent launch while token readiness is broken
  - auth repair requests automatic wake
  - next retry-at wake uses the earliest persisted retry deadline
- Fixtures:
  - fake `DownloadSettingsService`
  - fake `DownloadLedgerStore`
  - fake `RealDebridFacade`
  - fake `WorkerLauncher`
  - test clock

### `StandardAttemptRunnerTest.kt`

- Scope: unit
- Covers:
  - acquisition start or resume drives preparing progression
  - ready-link resolution happens only after acquisition reports links ready
  - checkpoint persistence cadence
  - pause acknowledgement after final checkpoint
  - cancel acknowledgement after final checkpoint
- Fixtures:
  - fake `RealDebridFacade`
  - fake `OutputReservationService`
  - fake `OutputFinalizer`
  - fake `QueueService`
  - fake control handle
  - test clock

### `ArchiveEntryAttemptRunnerTest.kt`

- Scope: unit
- Covers:
  - outer ZIP refresh
  - selected-entry copy only
  - selected-entry copy resumes from the saved offset
  - archive-entry rematch by stable identity
  - failure propagation without full-download fallback
- Fixtures:
  - fake `RealDebridFacade`
  - fake `RemoteZipFacade`
  - fake `OutputReservationService`
  - fake `OutputFinalizer`
  - fake `QueueService`

### `OutputReservationServiceTest.kt`

- Scope: unit
- Covers:
  - direct-save final name reservation before transfer
  - fresh reservation binds to the current saved output directory
  - extraction-root reservation before extraction
  - deterministic extraction-plan expansion with collision suffixing
  - resume precondition inspection
- Fixtures:
  - temp filesystem
  - fake `OutputFilesystem`
  - test clock

### `OutputCleanupServiceTest.kt`

- Scope: unit
- Covers:
  - restart cleanup removes final outputs plus reserved temp and extraction artifacts
  - cleanup rejects restart when any reserved artifact cannot be removed safely
- Fixtures:
  - temp filesystem
  - fake `OutputFilesystem`

### `OutputFinalizerIntegrationTest.kt`

- Scope: integration
- Covers:
  - direct-save promotion
  - extraction-plan expansion persists the reserved output set before extraction writes
  - extraction ordering with rename applied only to final non-archive outputs
  - recursive unarchive bounded passes
  - consumed archive deletion only after successful extraction
- Fixtures:
  - temp filesystem
  - archive fixtures
  - fake `OutputFilesystem`

### `QueueSummaryProjectorTest.kt`

- Scope: unit
- Covers:
  - newest-first ordering uses `createdAt`
  - summary counters exclude hidden rows
  - active-download flag tracks active states only
  - canonical state and allowed actions project without UI-side reverse engineering
  - details projection includes updated timestamp, original file size, output subfolder, preparing metadata, running or paused transfer detail, and output summary
- Fixtures:
  - fake `DownloadLedgerStore`
  - queue row fixtures

### `QueueNotificationPresenterTest.kt`

- Scope: unit
- Covers:
  - byte-first running notification text
  - short status-only text when no row is Running
  - exact completion titles for success and mixed outcomes
  - notification clear when active work reaches zero
- Fixtures:
  - fake `NotificationApi`
  - projection fixtures

### `DownloadWorkerEntryPointIntegrationTest.kt`

- Scope: integration
- Covers:
  - claim, run, and commit flow
  - runtime claim limit follows persisted concurrency
  - app-launch recovery explicitly wakes stale claims
  - auth gate suppresses new provider-dependent claims until token repair
  - interruption and resume
  - pause and cancel coordination through live control registry
  - retry-at wake reschedules correctly after deferred work remains
  - process-death-safe recovery preserving preparing timeout
- Fixtures:
  - temp ledger store
  - temp filesystem
  - fake `RealDebridFacade`
  - fake `RemoteZipFacade`
  - test clock

## Open Questions or Deferred Decisions

None currently.
