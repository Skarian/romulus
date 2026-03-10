package com.romulus.mobile.realdebrid.budget

import java.time.Clock
import java.time.Instant

internal class RequestBudget(private val clock: Clock) {
    private var retryAfter: Instant? = null

    suspend fun <T> run(block: suspend () -> T): T {
        val deferredUntil = retryAfter
        if (deferredUntil != null && clock.instant().isBefore(deferredUntil)) {
            throw UnsupportedOperationException("RequestBudget retry handling is not wired yet")
        }
        return block()
    }

    fun recordRetryAfter(until: Instant) {
        retryAfter = until
    }
}
