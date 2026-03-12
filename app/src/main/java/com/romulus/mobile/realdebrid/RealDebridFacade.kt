package com.romulus.mobile.realdebrid

import android.app.Application
import com.romulus.mobile.realdebrid.acquisition.ProviderAcquisitionPoller
import com.romulus.mobile.realdebrid.acquisition.ProviderSelectionService
import com.romulus.mobile.realdebrid.auth.AndroidKeystoreTokenCipher
import com.romulus.mobile.realdebrid.auth.InMemoryCredentialVault
import com.romulus.mobile.realdebrid.auth.MaskedTokenState
import com.romulus.mobile.realdebrid.auth.SharedPreferencesCredentialVault
import com.romulus.mobile.realdebrid.auth.TokenReadiness
import com.romulus.mobile.realdebrid.auth.TokenSaveResult
import com.romulus.mobile.realdebrid.auth.TokenService
import com.romulus.mobile.realdebrid.budget.RequestBudget
import com.romulus.mobile.realdebrid.inventory.TorrentInventoryService
import com.romulus.mobile.realdebrid.links.ExactZipResolver
import com.romulus.mobile.realdebrid.links.UnrestrictedLinkResolver
import java.time.Clock
import kotlinx.coroutines.flow.StateFlow

class RealDebridFacade internal constructor(
    private val tokenService: TokenService,
    private val inventoryService: TorrentInventoryService,
    private val acquisitionPoller: ProviderAcquisitionPoller,
    private val linkResolver: UnrestrictedLinkResolver,
    private val exactZipResolver: ExactZipResolver
) {
    private constructor(components: RealDebridFacadeComponents) : this(
        tokenService = components.tokenService,
        inventoryService = components.inventoryService,
        acquisitionPoller = components.acquisitionPoller,
        linkResolver = components.linkResolver,
        exactZipResolver = components.exactZipResolver
    )

    constructor() : this(scaffoldComponents())

    fun observeMaskedToken(): StateFlow<MaskedTokenState> = tokenService.observeMaskedToken()

    fun observeTokenReadiness(): StateFlow<TokenReadiness> = tokenService.observeReadiness()

    @Suppress("RedundantSuspendModifier")
    suspend fun readTokenReadiness(): TokenReadiness = tokenService.readReadiness()

    suspend fun saveValidatedToken(candidate: String): TokenSaveResult =
        tokenService.saveValidatedToken(candidate)

    suspend fun enumerateProviderFiles(
        request: ProviderInventoryRequest
    ): Result<ProviderInventory> = requireUsableToken {
        inventoryService.enumerate(request)
    }

    suspend fun startAcquisition(locator: ProviderLocator): Result<AcquisitionStatus> =
        requireUsableToken {
            acquisitionPoller.start(locator)
        }

    suspend fun resumeAcquisition(marker: ProviderResumeMarker): Result<AcquisitionStatus> =
        requireUsableToken {
            acquisitionPoller.resume(marker)
        }

    suspend fun resolveReadyLink(link: ProviderReadyLink): Result<ResolvedDownloadUnit> =
        requireUsableToken {
            linkResolver.resolve(link)
        }

    suspend fun resolveExactZip(request: ExactZipRequest): Result<ArchiveContainerLocator> =
        requireUsableToken {
            exactZipResolver.resolve(request)
        }

    private suspend fun <T> requireUsableToken(block: suspend () -> Result<T>): Result<T> {
        val readiness = tokenService.readReadiness()
        return if (readiness.isUsable) {
            block()
        } else {
            Result.failure(
                AuthRequiredException(readiness.brokenReason ?: "Auth required")
            )
        }
    }

    companion object {
        private fun scaffoldComponents(): RealDebridFacadeComponents {
            val clock = Clock.systemUTC()
            val api = UnwiredRealDebridApi()
            val budget = RequestBudget(clock)
            val tokenService = TokenService(
                credentialVault = InMemoryCredentialVault(),
                authClient = ScaffoldAuthClient(),
                clock = clock
            )
            val inventoryService = TorrentInventoryService(
                budget = budget,
                api = api
            )
            val selectionService = ProviderSelectionService(
                budget = budget,
                api = api
            )
            val acquisitionPoller = ProviderAcquisitionPoller(
                selectionService = selectionService,
                budget = budget,
                api = api
            )
            return RealDebridFacadeComponents(
                tokenService = tokenService,
                inventoryService = inventoryService,
                acquisitionPoller = acquisitionPoller,
                linkResolver = UnrestrictedLinkResolver(
                    budget = budget,
                    api = api
                ),
                exactZipResolver = ExactZipResolver(
                    inventoryService = inventoryService,
                    acquisitionPoller = acquisitionPoller,
                    budget = budget,
                    api = api
                )
            )
        }

        fun create(application: Application): RealDebridFacade {
            val clock = Clock.systemUTC()
            val tokenService = TokenService(
                credentialVault = SharedPreferencesCredentialVault(
                    context = application,
                    tokenCipher = AndroidKeystoreTokenCipher()
                ),
                authClient = RealDebridHttpFactory.createAuthClient(),
                clock = clock
            )
            val api = RealDebridHttpFactory.createApi(tokenService)
            val budget = RequestBudget(clock)
            val inventoryService = TorrentInventoryService(
                budget = budget,
                api = api
            )
            val selectionService = ProviderSelectionService(
                budget = budget,
                api = api
            )
            val acquisitionPoller = ProviderAcquisitionPoller(
                selectionService = selectionService,
                budget = budget,
                api = api
            )
            return RealDebridFacade(
                tokenService = tokenService,
                inventoryService = inventoryService,
                acquisitionPoller = acquisitionPoller,
                linkResolver = UnrestrictedLinkResolver(
                    budget = budget,
                    api = api
                ),
                exactZipResolver = ExactZipResolver(
                    inventoryService = inventoryService,
                    acquisitionPoller = acquisitionPoller,
                    budget = budget,
                    api = api
                )
            )
        }
    }
}

private data class RealDebridFacadeComponents(
    val tokenService: TokenService,
    val inventoryService: TorrentInventoryService,
    val acquisitionPoller: ProviderAcquisitionPoller,
    val linkResolver: UnrestrictedLinkResolver,
    val exactZipResolver: ExactZipResolver
)

internal class UnwiredRealDebridApi : RealDebridApi {
    override suspend fun getAvailableHosts(): List<AvailableHostDto> =
        throw UnsupportedOperationException("RealDebridFacade is not wired yet")

    override suspend fun addMagnet(magnet: String, host: String): AddedMagnetDto =
        throw UnsupportedOperationException("RealDebridFacade is not wired yet")

    override suspend fun selectFiles(torrentId: String, fileIdsCsv: String): Unit =
        throw UnsupportedOperationException("RealDebridFacade is not wired yet")

    override suspend fun getTorrentInfo(torrentId: String): TorrentInfoDto =
        throw UnsupportedOperationException("RealDebridFacade is not wired yet")

    override suspend fun unrestrictLink(link: String): UnrestrictedLinkDto =
        throw UnsupportedOperationException("RealDebridFacade is not wired yet")
}

internal class ScaffoldAuthClient : RealDebridAuthClient {
    override suspend fun validateToken(candidate: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("RealDebridFacade is not wired yet"))
}
