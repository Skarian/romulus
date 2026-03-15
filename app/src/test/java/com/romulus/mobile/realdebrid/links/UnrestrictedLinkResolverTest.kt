package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.FakeRealDebridApi
import com.romulus.mobile.realdebrid.MutableClock
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.UnrestrictedLinkDto
import com.romulus.mobile.realdebrid.budget.RequestBudget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnrestrictedLinkResolverTest {
    @Test
    fun resolvesProviderReadyLinkToDownloadUnit() = runTest {
        val api = FakeRealDebridApi().apply {
            respondUnrestrict(
                "https://restricted.example/file-a",
                Result.success(
                    UnrestrictedLinkDto(
                        filename = "file-a.mkv",
                        downloadUrl = "https://download.example/file-a",
                        fileSize = 123L
                    )
                )
            )
        }
        val resolver = UnrestrictedLinkResolver(
            budget = RequestBudget(MutableClock()),
            api = api
        )

        val result = resolver.resolve(
            ProviderReadyLink(
                restrictedUrl = "https://restricted.example/file-a"
            )
        ).getOrThrow()

        assertEquals("https://download.example/file-a", result.downloadUrl)
        assertEquals("file-a.mkv", result.originalName)
        assertEquals(123L, result.sizeBytes)
    }

    @Test
    fun propagatesUnrestrictFailure() = runTest {
        val api = FakeRealDebridApi().apply {
            respondUnrestrict(
                "https://restricted.example/file-a",
                Result.failure(IllegalStateException("unrestrict failed"))
            )
        }
        val resolver = UnrestrictedLinkResolver(
            budget = RequestBudget(MutableClock()),
            api = api
        )

        val result = resolver.resolve(
            ProviderReadyLink(
                restrictedUrl = "https://restricted.example/file-a"
            )
        )

        assertTrue(result.isFailure)
        assertEquals("unrestrict failed", result.exceptionOrNull()?.message)
    }
}
