@file:Suppress("ClassSignature")

package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ProviderSelectionRequest
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.realdebrid.ResolvedDownloadUnit
import com.romulus.mobile.realdebrid.auth.TokenReadiness
import kotlinx.coroutines.flow.StateFlow

internal interface ProviderRuntimeGateway {
    fun observeTokenReadiness(): StateFlow<TokenReadiness>

    suspend fun readTokenReadiness(): TokenReadiness

    suspend fun startAcquisition(request: ProviderSelectionRequest): Result<AcquisitionStatus>

    suspend fun resumeAcquisition(marker: ProviderResumeMarker): Result<AcquisitionStatus>

    suspend fun resolveReadyLink(link: ProviderReadyLink): Result<ResolvedDownloadUnit>
}

internal class RealDebridProviderRuntimeGateway(private val facade: RealDebridFacade) :
    ProviderRuntimeGateway {
    override fun observeTokenReadiness(): StateFlow<TokenReadiness> = facade.observeTokenReadiness()

    override suspend fun readTokenReadiness(): TokenReadiness = facade.readTokenReadiness()

    override suspend fun startAcquisition(
        request: ProviderSelectionRequest
    ): Result<AcquisitionStatus> = facade.startAcquisition(request)

    override suspend fun resumeAcquisition(
        marker: ProviderResumeMarker
    ): Result<AcquisitionStatus> = facade.resumeAcquisition(marker)

    override suspend fun resolveReadyLink(link: ProviderReadyLink): Result<ResolvedDownloadUnit> =
        facade.resolveReadyLink(link)
}
