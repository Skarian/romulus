@file:Suppress("ChainMethodContinuation", "ImportOrdering", "InjectDispatcher")

package com.romulus.mobile.downloads.work

import com.romulus.mobile.downloads.attempts.ProviderRuntimeGateway
import com.romulus.mobile.downloads.config.DownloadLimits
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.queue.DownloadLedgerStore
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class WorkScheduler(
    private val settingsService: DownloadSettingsService,
    private val ledgerStore: DownloadLedgerStore,
    private val providerGateway: ProviderRuntimeGateway,
    private val workerLauncher: WorkerLauncher,
    private val clock: Clock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authTransitionLock = Any()
    private var pendingAuthRecoveryDispatch = false

    @Volatile
    private var lastObservedAuthBlocked = !providerGateway.observeTokenReadiness().value.isUsable
    private val gate = providerGateway.observeTokenReadiness()
        .map { readiness ->
            QueueWorkGateState(
                authBlocked = !readiness.isUsable,
                reason = readiness.brokenReason
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = QueueWorkGateState(
                authBlocked = !providerGateway.observeTokenReadiness().value.isUsable,
                reason = providerGateway.observeTokenReadiness().value.brokenReason
            )
        )

    init {
        scope.launch {
            providerGateway.observeTokenReadiness().collect { readiness ->
                flushAuthRecoveryIfNeeded(
                    isBlocked = !readiness.isUsable,
                    launchWorker = true
                )
            }
        }
    }

    fun observeGate(): StateFlow<QueueWorkGateState> = gate

    suspend fun synchronizeAuthRecovery(): Result<Unit> {
        val readiness = providerGateway.readTokenReadiness()
        return flushAuthRecoveryIfNeeded(
            isBlocked = !readiness.isUsable,
            launchWorker = false
        )
    }

    suspend fun readGate(): QueueWorkGateState {
        val readiness = providerGateway.readTokenReadiness()
        return QueueWorkGateState(
            authBlocked = !readiness.isUsable,
            reason = readiness.brokenReason
        )
    }

    fun readClaimLimit(): Int = settingsService.readState().maxConcurrency.coerceIn(
        DownloadLimits.MIN_CONCURRENCY,
        DownloadLimits.MAX_CONCURRENCY
    )

    @Suppress("FunctionExpressionBody")
    suspend fun requestWake(reason: WorkWakeReason): Result<Unit> {
        return recordDispatch(reason).fold(
            onSuccess = { workerLauncher.launchNow() },
            onFailure = { throwable -> Result.failure(throwable) }
        )
    }

    @Suppress("ReturnCount")
    suspend fun scheduleNextDeferredWake(): Result<Unit> {
        if (ledgerStore.hasPendingDispatch()) {
            return workerLauncher.launchNow()
        }
        val gateState = readGate()
        val nextRetryAt = if (gateState.authBlocked) {
            null
        } else {
            ledgerStore.readNextRetryAt()
        }
        val nextLeaseExpiryAt = ledgerStore.readNextLeaseExpiryAt(
            actionRecoveryOnly = gateState.authBlocked
        )
        val nextWakeAt = listOfNotNull(nextRetryAt, nextLeaseExpiryAt).minOrNull()
            ?: return Result.success(Unit)
        return if (nextWakeAt.isAfter(clock.instant())) {
            workerLauncher.launchAt(nextWakeAt)
        } else {
            workerLauncher.launchNow()
        }
    }

    private suspend fun recordDispatch(reason: WorkWakeReason): Result<Unit> = when (reason) {
        WorkWakeReason.ENQUEUE,
        WorkWakeReason.RESUME,
        WorkWakeReason.MANUAL_RETRY,
        WorkWakeReason.RESTART -> Result.success(Unit)

        WorkWakeReason.RETRY_AT_REACHED,
        WorkWakeReason.APP_LAUNCH_RECOVERY,
        WorkWakeReason.AUTH_RECOVERED -> ledgerStore.requestDispatch().fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { throwable -> Result.failure(throwable) }
        )
    }

    private suspend fun flushAuthRecoveryIfNeeded(
        isBlocked: Boolean,
        launchWorker: Boolean
    ): Result<Unit> {
        val shouldDispatch = synchronized(authTransitionLock) {
            if (lastObservedAuthBlocked && !isBlocked) {
                pendingAuthRecoveryDispatch = true
            }
            lastObservedAuthBlocked = isBlocked
            pendingAuthRecoveryDispatch
        }
        if (!shouldDispatch) {
            return Result.success(Unit)
        }
        return recordDispatch(WorkWakeReason.AUTH_RECOVERED).fold(
            onSuccess = {
                synchronized(authTransitionLock) {
                    pendingAuthRecoveryDispatch = false
                }
                if (launchWorker) {
                    workerLauncher.launchNow()
                } else {
                    Result.success(Unit)
                }
            },
            onFailure = { throwable -> Result.failure(throwable) }
        )
    }
}
