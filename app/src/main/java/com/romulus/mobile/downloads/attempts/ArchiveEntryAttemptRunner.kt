package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.downloads.queue.FailureReason
import com.romulus.mobile.downloads.queue.QueueClaim
import com.romulus.mobile.downloads.queue.RecoveryDecision

internal class ArchiveEntryAttemptRunner {
    @Suppress("UnusedParameter")
    fun run(
        claim: QueueClaim,
        recoveryDecision: RecoveryDecision,
        controlHandle: ControlHandle
    ): AttemptOutcome = AttemptOutcome.Failed(
        FailureReason.OutputFailure("Archive-entry downloads are not wired yet")
    )
}
