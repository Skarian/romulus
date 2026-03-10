package com.romulus.mobile.realdebrid.acquisition

import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.budget.RequestBudget

@Suppress("UnusedParameter", "UnusedPrivateProperty")
internal class ProviderAcquisitionPoller(
    private val selectionService: ProviderSelectionService,
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun start(locator: ProviderLocator): Result<AcquisitionStatus> = budget.run {
        Result.failure(UnsupportedOperationException("ProviderAcquisitionPoller is not wired yet"))
    }

    suspend fun resume(marker: ProviderResumeMarker): Result<AcquisitionStatus> = budget.run {
        Result.failure(UnsupportedOperationException("ProviderAcquisitionPoller is not wired yet"))
    }
}
