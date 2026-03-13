package com.romulus.mobile.source

import android.app.Application
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.browse.ArchiveBrowseBuilder
import com.romulus.mobile.source.browse.ArchiveContainerPreparationService
import com.romulus.mobile.source.browse.BrowseFailure
import com.romulus.mobile.source.browse.BrowseRequest
import com.romulus.mobile.source.browse.BrowseResult
import com.romulus.mobile.source.browse.BrowseService
import com.romulus.mobile.source.browse.CachedStandardBrowseInventoryService
import com.romulus.mobile.source.browse.FileStandardBrowseInventoryCacheStore
import com.romulus.mobile.source.browse.StandardBrowseBuilder
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.ingest.AndroidSourceLoader
import com.romulus.mobile.source.ingest.SharedPreferencesSourceConfigStore
import com.romulus.mobile.source.ingest.SourceAcceptanceService
import com.romulus.mobile.source.ingest.SourceDocumentParser
import com.romulus.mobile.source.ingest.SourceSchemaValidator
import com.romulus.mobile.source.ingest.SourceValidation
import com.romulus.mobile.source.snapshot.AcceptedSourceSummary
import com.romulus.mobile.source.snapshot.FileSnapshotStore
import com.romulus.mobile.source.snapshot.HomeSourceState
import com.romulus.mobile.source.snapshot.SharedPreferencesSourceActivationStore
import com.romulus.mobile.source.snapshot.SnapshotPublisher
import com.romulus.mobile.source.snapshot.SourceReadiness
import com.romulus.mobile.source.snapshot.SourceRefreshResult
import com.romulus.mobile.source.snapshot.SourceRefreshTrigger
import com.romulus.mobile.source.snapshot.SourceStateOwner
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class SourceFacade internal constructor(
    private val acceptanceService: SourceAcceptanceService?,
    private val stateOwner: SourceStateOwner,
    private val browseService: BrowseService?
) {
    constructor() : this(
        acceptanceService = null,
        stateOwner = ScaffoldSourceStateOwner(),
        browseService = null
    )

    suspend fun accept(command: AcceptSourceCommand): AcceptSourceResult =
        acceptanceService?.accept(command)
            ?: AcceptSourceResult.Failed("SourceFacade is not wired yet")

    suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult =
        stateOwner.refresh(trigger)

    fun observeHomeState(): StateFlow<HomeSourceState> = stateOwner.observeHomeState()

    fun observeReadiness(): StateFlow<SourceReadiness> = stateOwner.observeReadiness()

    @Suppress("RedundantSuspendModifier")
    suspend fun readStartupReadiness(): SourceReadiness = stateOwner.readStartupReadiness()

    suspend fun revalidateReadiness(): SourceReadiness = stateOwner.revalidateReadiness()

    fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?> =
        stateOwner.observeAcceptedSourceSummary()

    suspend fun browse(request: BrowseRequest): BrowseResult = browseService?.load(request)
        ?: BrowseResult.Failed(
            BrowseFailure.StandardResolver(
                "SourceFacade is not wired yet"
            )
        )

    companion object {
        @Suppress("LongMethod")
        internal fun create(
            application: Application,
            realDebridFacade: RealDebridFacade,
            archiveContainerPreparationService: ArchiveContainerPreparationService
        ): SourceFacade {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val clock = Clock.systemUTC()
            val sourceConfigStore = SharedPreferencesSourceConfigStore(
                context = application,
                json = json
            )
            val activationStore = SharedPreferencesSourceActivationStore(
                context = application,
                json = json
            )
            val snapshotStore = FileSnapshotStore(
                context = application,
                json = json
            )
            val parser = SourceDocumentParser(
                sourceLoader = AndroidSourceLoader(
                    context = application,
                    httpClient = OkHttpClient()
                ),
                json = json,
                schemaValidator = SourceSchemaValidator.create(application)
            )
            val validation = SourceValidation()
            val snapshotPublisher = SnapshotPublisher(
                snapshotStore = snapshotStore,
                sourceConfigStore = sourceConfigStore,
                activationStore = activationStore,
                parser = parser,
                validation = validation,
                clock = clock
            )
            val standardBrowseInventoryService = CachedStandardBrowseInventoryService(
                cacheStore = FileStandardBrowseInventoryCacheStore(
                    cacheDirectory = application.filesDir.resolve("source_browse_cache"),
                    json = json
                ),
                enumerateProviderFiles = realDebridFacade::enumerateProviderFiles
            )
            return SourceFacade(
                acceptanceService = SourceAcceptanceService(
                    parser = parser,
                    validation = validation,
                    sourceConfigStore = sourceConfigStore,
                    snapshotPublisher = snapshotPublisher,
                    clock = clock
                ),
                stateOwner = snapshotPublisher,
                browseService = BrowseService(
                    snapshotStore = snapshotStore,
                    standardBrowseBuilder = StandardBrowseBuilder(
                        enumerateTorrentMetadata = standardBrowseInventoryService::load
                    ),
                    archiveBrowseBuilder = ArchiveBrowseBuilder(
                        loadArchiveBrowse = archiveContainerPreparationService::loadForBrowse
                    )
                )
            )
        }
    }
}

private class ScaffoldSourceStateOwner : SourceStateOwner {
    private val acceptedSourceSummary = MutableStateFlow<AcceptedSourceSummary?>(null)
    private val homeState = MutableStateFlow<HomeSourceState>(
        HomeSourceState.SourceLoadError(
            message = "SourceFacade is not wired yet",
            refreshAvailable = false
        )
    )
    private val readiness = MutableStateFlow(
        SourceReadiness(
            acceptedMode = null,
            isUsable = false,
            hasUsableSnapshot = false,
            brokenReason = "SourceFacade is not wired yet"
        )
    )

    override suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult =
        SourceRefreshResult.FailedWithoutSnapshot("SourceFacade is not wired yet")

    override fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?> =
        acceptedSourceSummary

    override fun observeHomeState(): StateFlow<HomeSourceState> = homeState

    override fun observeReadiness(): StateFlow<SourceReadiness> = readiness

    override suspend fun readStartupReadiness(): SourceReadiness = readiness.value

    override suspend fun revalidateReadiness(): SourceReadiness = readiness.value
}
