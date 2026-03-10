package com.romulus.mobile.realdebrid.acquisition

import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.budget.RequestBudget

@Suppress("UnusedParameter", "UnusedPrivateProperty")
internal class ProviderSelectionService(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun start(locator: ProviderLocator): Result<ProviderResumeMarker> = budget.run {
        Result.failure(UnsupportedOperationException("ProviderSelectionService is not wired yet"))
    }

    suspend fun verify(marker: ProviderResumeMarker): Result<ProviderResumeMarker> = budget.run {
        Result.failure(UnsupportedOperationException("ProviderSelectionService is not wired yet"))
    }
}
