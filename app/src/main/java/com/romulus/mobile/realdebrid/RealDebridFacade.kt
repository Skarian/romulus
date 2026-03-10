package com.romulus.mobile.realdebrid

import com.romulus.mobile.realdebrid.auth.MaskedTokenState
import com.romulus.mobile.realdebrid.auth.TokenReadiness
import com.romulus.mobile.realdebrid.auth.TokenSaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("RedundantSuspendModifier", "UnusedParameter")
class RealDebridFacade {
    private val maskedToken = MutableStateFlow(MaskedTokenState(maskedValue = ""))
    private val tokenReadiness = MutableStateFlow(
        TokenReadiness(
            isUsable = false,
            brokenReason = "RealDebridFacade is not wired yet"
        )
    )

    fun observeMaskedToken(): StateFlow<MaskedTokenState> = maskedToken.asStateFlow()

    fun observeTokenReadiness(): StateFlow<TokenReadiness> = tokenReadiness.asStateFlow()

    suspend fun readTokenReadiness(): TokenReadiness = tokenReadiness.value

    suspend fun saveValidatedToken(candidate: String): TokenSaveResult =
        TokenSaveResult.Failed("RealDebridFacade is not wired yet")

    suspend fun enumerateProviderFiles(
        request: ProviderInventoryRequest
    ): Result<ProviderInventory> =
        Result.failure(UnsupportedOperationException("RealDebridFacade is not wired yet"))

    suspend fun startAcquisition(locator: ProviderLocator): Result<AcquisitionStatus> =
        Result.failure(UnsupportedOperationException("RealDebridFacade is not wired yet"))

    suspend fun resumeAcquisition(marker: ProviderResumeMarker): Result<AcquisitionStatus> =
        Result.failure(UnsupportedOperationException("RealDebridFacade is not wired yet"))

    suspend fun resolveReadyLink(link: ProviderReadyLink): Result<ResolvedDownloadUnit> =
        Result.failure(UnsupportedOperationException("RealDebridFacade is not wired yet"))

    suspend fun resolveExactZip(request: ExactZipRequest): Result<ArchiveContainerLocator> =
        Result.failure(UnsupportedOperationException("RealDebridFacade is not wired yet"))
}
