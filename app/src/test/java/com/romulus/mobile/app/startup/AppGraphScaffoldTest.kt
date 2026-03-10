package com.romulus.mobile.app.startup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGraphScaffoldTest {
    @Test
    fun unwiredSetupStateStoreReadFailsExplicitly() = runTest {
        val failure = runCatching { UnwiredSetupStateStore().read() }.exceptionOrNull()

        assertTrue(failure is UnsupportedOperationException)
        assertEquals("SetupStateStore is not wired yet", failure?.message)
    }

    @Test
    fun unwiredUriGrantRestoreFailsExplicitly() = runTest {
        val failure = runCatching {
            FailFastUriGrantRegistry("com.romulus.mobile.test").restorePersistedGrants()
        }.exceptionOrNull()

        assertTrue(failure is UnsupportedOperationException)
        assertEquals(
            "UriGrantRegistry is not wired yet for com.romulus.mobile.test",
            failure?.message
        )
    }
}
