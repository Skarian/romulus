package com.romulus.mobile.realdebrid.auth

import com.romulus.mobile.realdebrid.InvalidTokenException
import com.romulus.mobile.realdebrid.RealDebridAuthClient
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

internal class TokenService(
    private val credentialVault: CredentialVault,
    private val authClient: RealDebridAuthClient,
    private val clock: Clock
) {
    private var currentToken: String? = null
    private val maskedToken = MutableStateFlow(MaskedTokenState(maskedValue = ""))
    private val readiness = MutableStateFlow(
        TokenReadiness(
            isUsable = false,
            brokenReason = "API key is not configured"
        )
    )

    init {
        hydrateFromVault()
    }

    fun observeMaskedToken(): StateFlow<MaskedTokenState> = maskedToken.asStateFlow()

    fun observeReadiness(): StateFlow<TokenReadiness> = readiness.asStateFlow()

    fun readReadiness(): TokenReadiness = readiness.value

    suspend fun saveValidatedToken(candidate: String): TokenSaveResult {
        val trimmedCandidate = candidate.trim()
        val validationFailure = when {
            trimmedCandidate.isBlank() -> TokenSaveResult.Rejected("API key is required.")
            else -> {
                val validationResult = authClient.validateToken(trimmedCandidate)
                validationResult.exceptionOrNull()?.toTokenSaveFailure()
            }
        }
        if (validationFailure != null) {
            return validationFailure
        }

        return credentialVault.writeToken(trimmedCandidate, clock.instant()).fold(
            onSuccess = {
                applyTokenState(trimmedCandidate, brokenReason = null)
                TokenSaveResult.Saved
            },
            onFailure = { error ->
                TokenSaveResult.Failed(error.message ?: "API key could not be saved")
            }
        )
    }

    suspend fun readAuthHeader(): String? = currentToken?.let(::bearer) ?: restoreAuthHeader()

    fun reportAuthFailure() {
        if (currentToken == null) {
            readiness.value = TokenReadiness(
                isUsable = false,
                brokenReason = "API key is not configured"
            )
            return
        }
        readiness.value = TokenReadiness(
            isUsable = false,
            brokenReason = "Saved API key no longer authenticates"
        )
    }

    private fun hydrateFromVault() {
        runBlocking {
            credentialVault.readPlainToken().fold(
                onSuccess = { token ->
                    if (token == null) {
                        applyTokenState(token = null, brokenReason = "API key is not configured")
                    } else {
                        applyTokenState(token = token, brokenReason = null)
                    }
                },
                onFailure = {
                    applyTokenState(
                        token = null,
                        brokenReason = "Saved API key could not be decrypted"
                    )
                }
            )
        }
    }

    private fun applyTokenState(token: String?, brokenReason: String?) {
        currentToken = token
        maskedToken.value = MaskedTokenState(maskedValue = token?.toMaskedToken().orEmpty())
        readiness.value = TokenReadiness(
            isUsable = token != null && brokenReason == null,
            brokenReason = brokenReason
        )
    }

    private fun Throwable?.toTokenSaveFailure(): TokenSaveResult = when (this) {
        is InvalidTokenException -> TokenSaveResult.Rejected("Invalid API key")
        is IOException -> TokenSaveResult.Failed("Network error while validating API key")
        null -> TokenSaveResult.Failed("API validation failed")
        else -> TokenSaveResult.Failed(this.message ?: "API validation failed")
    }

    private suspend fun restoreAuthHeader(): String? {
        val restored = credentialVault.readPlainToken().getOrNull() ?: return null
        applyTokenState(restored, brokenReason = null)
        return bearer(restored)
    }

    private fun bearer(token: String): String = "Bearer $token"
}

internal fun String.toMaskedToken(): String {
    val suffix = takeLast(MASK_SUFFIX_LENGTH).filter { it.isLetterOrDigit() }
    return if (suffix.isBlank()) {
        "••••••••"
    } else {
        "••••••••$suffix"
    }
}

private const val MASK_SUFFIX_LENGTH = 4
