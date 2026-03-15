package com.romulus.mobile.realdebrid.auth

data class MaskedTokenState(val maskedValue: String)

data class TokenReadiness(val isUsable: Boolean, val brokenReason: String?)

sealed interface TokenSaveResult {
    data object Saved : TokenSaveResult

    data class Rejected(val message: String) : TokenSaveResult

    data class Failed(val message: String) : TokenSaveResult
}
