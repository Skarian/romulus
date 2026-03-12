package com.romulus.mobile.downloads.config

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSettingsServiceTest {
    @Test
    fun updatePersistsValidatedSettingsAndReadiness() = runTest {
        val store = FakeDownloadSettingsStore()
        val outputAccess = FakeOutputDirectoryAccess(usableUris = setOf("content://downloads/tree"))
        val service = DownloadSettingsService.create(
            store = store,
            outputAccess = outputAccess
        )

        val result = service.update(
            DownloadSettingsDraft(
                outputDirectoryUri = "content://downloads/tree",
                maxConcurrency = 3
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(
            DownloadSettingsState(
                outputDirectoryUri = "content://downloads/tree",
                maxConcurrency = 3
            ),
            service.readState()
        )
        assertTrue(service.readReadiness().isUsable)
        assertEquals(
            DownloadSettingsState(
                outputDirectoryUri = "content://downloads/tree",
                maxConcurrency = 3
            ),
            store.persistedState
        )
    }

    @Test
    fun unusableDirectoryIsRejected() = runTest {
        val service = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore(),
            outputAccess = FakeOutputDirectoryAccess(usableUris = emptySet())
        )

        val result = service.update(
            DownloadSettingsDraft(
                outputDirectoryUri = "content://broken/tree",
                maxConcurrency = 2
            )
        )

        assertTrue(result.isFailure)
        assertFalse(service.readReadiness().isUsable)
        assertEquals(
            "Download directory is no longer accessible",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun revalidateReadinessPublishesBrokenDirectoryAfterRuntimeChange() = runTest {
        val outputAccess = FakeOutputDirectoryAccess(
            usableUris = setOf("content://downloads/tree")
        )
        val service = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore().apply {
                persistedState = DownloadSettingsState(
                    outputDirectoryUri = "content://downloads/tree",
                    maxConcurrency = 2
                )
            },
            outputAccess = outputAccess
        )

        outputAccess.updateUsableUris(emptySet())
        val readiness = service.revalidateReadiness()

        assertFalse(readiness.isUsable)
        assertEquals(
            "Download directory is no longer accessible",
            service.observeReadiness().value.brokenReason
        )
    }

    @Test
    fun maxConcurrencyAboveLimitIsRejected() = runTest {
        val service = DownloadSettingsService.create(
            store = FakeDownloadSettingsStore(),
            outputAccess = FakeOutputDirectoryAccess(
                usableUris = setOf("content://downloads/tree")
            )
        )

        val result = service.update(
            DownloadSettingsDraft(
                outputDirectoryUri = "content://downloads/tree",
                maxConcurrency = DownloadLimits.MAX_CONCURRENCY + 1
            )
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Max concurrency must be between ${DownloadLimits.MIN_CONCURRENCY} and ${DownloadLimits.MAX_CONCURRENCY}",
            result.exceptionOrNull()?.message
        )
    }
}

private class FakeDownloadSettingsStore : DownloadSettingsStore {
    var persistedState: DownloadSettingsState? = null

    override suspend fun read(): DownloadSettingsState? = persistedState

    override suspend fun write(state: DownloadSettingsState): Result<Unit> {
        persistedState = state
        return Result.success(Unit)
    }
}

private class FakeOutputDirectoryAccess(
    private var usableUris: Set<String>
) : OutputDirectoryAccess {
    override suspend fun check(outputDirectoryUri: String?): DownloadSettingsReadiness =
        if (outputDirectoryUri != null && outputDirectoryUri in usableUris) {
            DownloadSettingsReadiness(
                isUsable = true,
                brokenReason = null
            )
        } else {
            DownloadSettingsReadiness(
                isUsable = false,
                brokenReason = "Download directory is no longer accessible"
            )
        }

    fun updateUsableUris(next: Set<String>) {
        usableUris = next
    }
}
