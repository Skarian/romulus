@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.work

import java.time.Instant

internal enum class WorkWakeReason {
    ENQUEUE,
    RESUME,
    MANUAL_RETRY,
    RESTART,
    RETRY_AT_REACHED,
    APP_LAUNCH_RECOVERY,
    AUTH_RECOVERED
}

internal data class QueueWorkGateState(
    val authBlocked: Boolean,
    val reason: String?
)

internal interface WorkerLauncher {
    suspend fun launchNow(): Result<Unit>

    suspend fun launchAt(instant: Instant): Result<Unit>
}
