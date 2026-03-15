package com.romulus.mobile.realdebrid.links

import com.romulus.mobile.realdebrid.ProviderReadyLink
import com.romulus.mobile.realdebrid.RealDebridApi
import com.romulus.mobile.realdebrid.ResolvedDownloadUnit
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.captureResult

internal class UnrestrictedLinkResolver(
    private val budget: RequestBudget,
    private val api: RealDebridApi
) {
    suspend fun resolve(link: ProviderReadyLink): Result<ResolvedDownloadUnit> = captureResult {
        val unrestricted = budget.run { api.unrestrictLink(link.restrictedUrl) }
        ResolvedDownloadUnit(
            downloadUrl = unrestricted.downloadUrl,
            originalName = unrestricted.filename
                ?: link.restrictedUrl.substringAfterLast('/').ifBlank { "download" },
            sizeBytes = unrestricted.fileSize
        )
    }
}
