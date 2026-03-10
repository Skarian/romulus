package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.downloads.output.FinalOutputRecord
import com.romulus.mobile.downloads.queue.ControlSignal
import com.romulus.mobile.downloads.queue.FailureReason
import com.romulus.mobile.downloads.queue.TransferCheckpoint

interface ControlHandle {
    suspend fun awaitSignal(): ControlSignal

    fun current(): ControlSignal
}

sealed interface AttemptOutcome {
    data class Completed(val outputs: List<FinalOutputRecord>) : AttemptOutcome

    data class Paused(val checkpoint: TransferCheckpoint) : AttemptOutcome

    data class Cancelled(val checkpoint: TransferCheckpoint?) : AttemptOutcome

    data class Failed(val reason: FailureReason) : AttemptOutcome
}
