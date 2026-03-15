package com.romulus.mobile.realdebrid.auth

import com.romulus.mobile.realdebrid.FakeAuthClient
import com.romulus.mobile.realdebrid.InvalidTokenException
import com.romulus.mobile.realdebrid.MutableClock
import com.romulus.mobile.realdebrid.RecordingCredentialVault
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenServiceTest {
    @Test
    fun candidateTokenIsValidatedBeforeSave() = runTest {
        val authClient = FakeAuthClient()
        val vault = RecordingCredentialVault()
        val service = TokenService(
            credentialVault = vault,
            authClient = authClient,
            clock = MutableClock()
        )

        val result = service.saveValidatedToken("token-1234")

        assertEquals(TokenSaveResult.Saved, result)
        assertEquals(listOf("token-1234"), authClient.validatedCandidates)
        assertEquals("••••••••1234", service.observeMaskedToken().value.maskedValue)
        assertTrue(service.observeReadiness().value.isUsable)
    }

    @Test
    fun invalidCandidateLeavesPriorTokenUnchanged() = runTest {
        val authClient = FakeAuthClient { Result.failure(InvalidTokenException()) }
        val service = TokenService(
            credentialVault = RecordingCredentialVault(storedToken = "good-token-1234"),
            authClient = authClient,
            clock = MutableClock()
        )

        val result = service.saveValidatedToken("bad-token")

        assertEquals(TokenSaveResult.Rejected("Invalid API key"), result)
        assertEquals("••••••••1234", service.observeMaskedToken().value.maskedValue)
        assertTrue(service.observeReadiness().value.isUsable)
    }

    @Test
    fun maskedTokenHydratesFromStoredToken() = runTest {
        val service = TokenService(
            credentialVault = RecordingCredentialVault(storedToken = "persisted-9876"),
            authClient = FakeAuthClient(),
            clock = MutableClock()
        )

        assertEquals("••••••••9876", service.observeMaskedToken().value.maskedValue)
        assertTrue(service.observeReadiness().value.isUsable)
    }

    @Test
    fun tokenReadinessReportsBrokenAuthCleanly() = runTest {
        val service = TokenService(
            credentialVault = RecordingCredentialVault(storedToken = "persisted-9876"),
            authClient = FakeAuthClient(),
            clock = MutableClock()
        )

        service.reportAuthFailure()

        assertFalse(service.observeReadiness().value.isUsable)
        assertEquals(
            "Saved API key no longer authenticates",
            service.observeReadiness().value.brokenReason
        )
        assertEquals("••••••••9876", service.observeMaskedToken().value.maskedValue)
    }

    @Test
    fun networkValidationFailureSurfacesAsFailedSave() = runTest {
        val service = TokenService(
            credentialVault = RecordingCredentialVault(),
            authClient = FakeAuthClient { Result.failure(IOException("network down")) },
            clock = MutableClock()
        )

        val result = service.saveValidatedToken("token-1234")

        assertEquals(
            TokenSaveResult.Failed("Network error while validating API key"),
            result
        )
        assertNull(service.observeMaskedToken().value.maskedValue.ifBlank { null })
        assertFalse(service.observeReadiness().value.isUsable)
    }
}
