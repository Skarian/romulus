package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.ResolvedDownloadUnit
import com.romulus.mobile.realdebrid.budget.RequestBudget

@Suppress("UnusedParameter", "UnusedPrivateProperty")
internal class UnrestrictedLinkResolver(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun resolve(link: ProviderReadyLink): Result<ResolvedDownloadUnit> = budget.run {
        Result.failure(UnsupportedOperationException("UnrestrictedLinkResolver is not wired yet"))
    }
}
