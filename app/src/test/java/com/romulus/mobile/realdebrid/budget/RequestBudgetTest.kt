package com.romulus.mobile.realdebrid.budget

import com.romulus.mobile.realdebrid.MutableClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestBudgetTest {
    @Test
    fun requestBudgetSerializesRequests() = runTest {
        val budget = RequestBudget(clock = MutableClock())
        val order = mutableListOf<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            budget.run {
                order += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first-end"
            }
        }
        firstEntered.await()
        val second = async {
            budget.run {
                order += "second"
            }
        }

        assertEquals(listOf("first-start"), order)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second"), order)
    }

    @Test
    fun retryAfterDelayRunsBeforeRequest() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-10T00:00:00Z"))
        var waitedFor: Duration? = null
        val budget = RequestBudget(clock = clock) { duration ->
            waitedFor = duration
            clock.advanceBy(duration)
        }

        budget.recordRetryAfter(clock.instant().plusSeconds(5))
        var executedAt: Instant? = null
        budget.run {
            executedAt = clock.instant()
        }

        assertEquals(Duration.ofSeconds(5), waitedFor)
        assertEquals(Instant.parse("2026-03-10T00:00:05Z"), executedAt)
    }
}
