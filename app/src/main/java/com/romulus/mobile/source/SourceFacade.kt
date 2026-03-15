package com.romulus.mobile.source

import android.app.Application
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.source.browse.ArchiveBrowseBuilder
import com.romulus.mobile.source.browse.ArchiveContainerPreparationService
import com.romulus.mobile.source.browse.BrowseFailure
import com.romulus.mobile.source.browse.BrowseMode
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
    private val browseService: BrowseService?,
    private val diagnosticsFacade: DiagnosticsFacade?
) {
    constructor() : this(
        acceptanceService = null,
        stateOwner = ScaffoldSourceStateOwner(),
        browseService = null,
        diagnosticsFacade = null
    )

    suspend fun accept(command: AcceptSourceCommand): AcceptSourceResult {
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.SOURCE,
            event = "accept-source",
            outcome = "started",
            context = mapOf("mode" to command.mode.name)
        )
        val result = acceptanceService?.accept(command)
            ?: AcceptSourceResult.Failed("SourceFacade is not wired yet")
        when (result) {
            is AcceptSourceResult.Accepted -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.SOURCE,
                event = "accept-source",
                outcome = "succeeded",
                snapshotId = result.snapshotId.value,
                context = mapOf("mode" to command.mode.name)
            )

            is AcceptSourceResult.Rejected -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.SOURCE,
                event = "accept-source",
                outcome = "rejected",
                context = mapOf(
                    "mode" to command.mode.name,
                    "issueCount" to result.issues.size.toString()
                )
            )

            is AcceptSourceResult.Failed -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.SOURCE,
                event = "accept-source",
                outcome = "failed",
                context = mapOf(
                    "mode" to command.mode.name,
                    "message" to result.message
                )
            )
        }
        return result
    }

    suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult {
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.HOME,
            event = "refresh-source",
            outcome = "started",
            context = mapOf("trigger" to trigger.diagnosticName())
        )
        val result = stateOwner.refresh(trigger)
        when (result) {
            is SourceRefreshResult.Replaced -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.HOME,
                event = "refresh-source",
                outcome = "succeeded",
                snapshotId = result.snapshotId.value,
                context = mapOf(
                    "trigger" to trigger.diagnosticName(),
                    "refreshOutcome" to "replaced"
                )
            )

            is SourceRefreshResult.RetainedPrior -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.HOME,
                event = "refresh-source",
                outcome = "failed",
                snapshotId = result.priorSnapshotId.value,
                context = mapOf(
                    "trigger" to trigger.diagnosticName(),
                    "refreshOutcome" to "retained-prior",
                    "message" to result.message
                )
            )

            is SourceRefreshResult.FailedWithoutSnapshot -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.HOME,
                event = "refresh-source",
                outcome = "failed",
                context = mapOf(
                    "trigger" to trigger.diagnosticName(),
                    "refreshOutcome" to "failed-without-snapshot",
                    "message" to result.message
                )
            )
        }
        return result
    }

    fun observeHomeState(): StateFlow<HomeSourceState> = stateOwner.observeHomeState()

    fun observeReadiness(): StateFlow<SourceReadiness> = stateOwner.observeReadiness()

    @Suppress("RedundantSuspendModifier")
    suspend fun readStartupReadiness(): SourceReadiness = stateOwner.readStartupReadiness()

    suspend fun revalidateReadiness(): SourceReadiness = stateOwner.revalidateReadiness()

    fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?> =
        stateOwner.observeAcceptedSourceSummary()

    suspend fun browse(request: BrowseRequest): BrowseResult {
        diagnosticsFacade?.record(
            domain = DiagnosticDomain.FILES,
            event = "browse",
            outcome = "started",
            snapshotId = request.snapshotId.value,
            context = mapOf("entryId" to request.entryId.value)
        )
        val result = browseService?.load(request)
            ?: BrowseResult.Failed(
                BrowseFailure.StandardResolver(
                    "SourceFacade is not wired yet"
                )
            )
        when (result) {
            is BrowseResult.Loaded -> diagnosticsFacade?.record(
                domain = if (result.mode == BrowseMode.ARCHIVE_SELECTION) {
                    DiagnosticDomain.ARCHIVE_SELECTION
                } else {
                    DiagnosticDomain.FILES
                },
                event = "browse",
                outcome = "succeeded",
                snapshotId = request.snapshotId.value,
                context = mapOf(
                    "entryId" to request.entryId.value,
                    "rowCount" to result.items.size.toString()
                )
            )

            is BrowseResult.Preparing -> diagnosticsFacade?.record(
                domain = if (result.mode == BrowseMode.ARCHIVE_SELECTION) {
                    DiagnosticDomain.ARCHIVE_SELECTION
                } else {
                    DiagnosticDomain.FILES
                },
                event = "browse",
                outcome = "preparing",
                snapshotId = request.snapshotId.value,
                context = mapOf(
                    "entryId" to request.entryId.value,
                    "statusLabel" to result.statusLabel.orEmpty()
                )
            )

            is BrowseResult.Failed -> diagnosticsFacade?.record(
                domain = DiagnosticDomain.FILES,
                event = "browse",
                outcome = "failed",
                snapshotId = request.snapshotId.value,
                context = mapOf(
                    "entryId" to request.entryId.value,
                    "message" to result.failure.toDiagnosticMessage()
                )
            )
        }
        return result
    }

    private fun SourceRefreshTrigger.diagnosticName(): String = when (this) {
        SourceRefreshTrigger.HomeManualRefresh -> "HomeManualRefresh"
        SourceRefreshTrigger.ColdLaunch -> "ColdLaunch"
        SourceRefreshTrigger.SettingsSave -> "SettingsSave"
    }

    private fun BrowseFailure.toDiagnosticMessage(): String = when (this) {
        is BrowseFailure.MissingEntry -> "Source entry ${entryId.value} is missing."
        is BrowseFailure.StandardResolver -> message
        is BrowseFailure.ArchiveResolver -> message
        is BrowseFailure.ArchiveEnumeration -> message
    }

    companion object {
        @Suppress("LongMethod")
        internal fun create(
            application: Application,
            realDebridFacade: RealDebridFacade,
            archiveContainerPreparationService: ArchiveContainerPreparationService,
            diagnosticsFacade: DiagnosticsFacade
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
                ),
                diagnosticsFacade = diagnosticsFacade
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
