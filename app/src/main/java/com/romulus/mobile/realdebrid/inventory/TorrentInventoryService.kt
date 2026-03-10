package com.romulus.mobile.realdebrid.inventory

import com.romulus.mobile.realdebrid.ProviderInventory
import com.romulus.mobile.realdebrid.ProviderInventoryRequest
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.budget.RequestBudget

@Suppress(
    "ArgumentListWrapping",
    "ChainMethodContinuation",
    "FunctionExpressionBody",
    "MaximumLineLength",
    "UnusedParameter",
    "UnusedPrivateProperty"
)
internal class TorrentInventoryService(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun enumerate(request: ProviderInventoryRequest): Result<ProviderInventory> {
        val failure = UnsupportedOperationException("TorrentInventoryService is not wired yet")
        return budget.run {
            Result.failure(failure)
        }
    }
}
