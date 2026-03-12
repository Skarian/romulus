package com.romulus.mobile.realdebrid.budget

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

internal class RequestBudget(
    private val clock: Clock,
    private val sleeper: suspend (Duration) -> Unit = { duration ->
        delay(duration.toMillis())
    }
) {
    private val gate = Mutex()
    private var retryAfter: Instant? = null

    suspend fun <T> run(block: suspend () -> T): T = gate.withLock {
        delayIfRequired()
        try {
            block()
        } catch (error: HttpException) {
            retryAfterFrom(error)?.let(::recordRetryAfter)
            throw error
        }
    }

    fun recordRetryAfter(until: Instant) {
        val currentRetryAfter = retryAfter
        if (currentRetryAfter == null || until.isAfter(currentRetryAfter)) {
            retryAfter = until
        }
    }

    private suspend fun delayIfRequired() {
        val blockedUntil = retryAfter ?: return
        val now = clock.instant()
        if (!now.isBefore(blockedUntil)) {
            return
        }
        sleeper(Duration.between(now, blockedUntil))
        retryAfter = null
    }

    private fun retryAfterFrom(error: HttpException): Instant? {
        if (error.code() != TOO_MANY_REQUESTS_CODE) {
            return null
        }
        val headerValue = error.response()?.headers()?.get(RETRY_AFTER_HEADER)
        val retryAfterSeconds = headerValue?.toLongOrNull()
        return retryAfterSeconds?.let { seconds ->
            clock.instant().plusSeconds(seconds)
        }
    }

    private companion object {
        const val TOO_MANY_REQUESTS_CODE = 429
        const val RETRY_AFTER_HEADER = "Retry-After"
    }
}
