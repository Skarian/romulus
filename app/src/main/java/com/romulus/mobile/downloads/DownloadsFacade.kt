@file:Suppress(
    "BinaryExpressionWrapping",
    "FunctionLiteral",
    "FunctionSignature",
    "ImportOrdering",
    "MaximumLineLength"
)

package com.romulus.mobile.downloads

import android.app.Application
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.downloads.attempts.ArchiveEntryAttemptRunner
import com.romulus.mobile.downloads.attempts.FacadeRemoteZipCopyGateway
import com.romulus.mobile.downloads.attempts.OkHttpDownloadTransport
import com.romulus.mobile.downloads.attempts.SharedArchiveContainerGateway
import com.romulus.mobile.downloads.attempts.RealDebridProviderRuntimeGateway
import com.romulus.mobile.downloads.attempts.StandardAttemptRunner
import com.romulus.mobile.downloads.config.AndroidOutputDirectoryAccess
import com.romulus.mobile.downloads.config.DownloadLimits
import com.romulus.mobile.downloads.config.DownloadSettingsDraft
import com.romulus.mobile.downloads.config.DownloadSettingsReadiness
import com.romulus.mobile.downloads.config.DownloadSettingsService
import com.romulus.mobile.downloads.config.DownloadSettingsState
import com.romulus.mobile.downloads.config.SharedPreferencesDownloadSettingsStore
import com.romulus.mobile.downloads.queue.DownloadLedgerStore
import com.romulus.mobile.downloads.queue.DownloadsProjection
import com.romulus.mobile.downloads.queue.EnqueueResult
import com.romulus.mobile.downloads.queue.FileDownloadLedgerStore
import com.romulus.mobile.downloads.queue.QueueActionCommand
import com.romulus.mobile.downloads.queue.QueueRecoveryPolicy
import com.romulus.mobile.downloads.queue.QueueService
import com.romulus.mobile.downloads.queue.QueueSummary
import com.romulus.mobile.downloads.queue.QueueSummaryProjector
import com.romulus.mobile.downloads.queue.QueueTaskInput
import com.romulus.mobile.downloads.output.AndroidOutputFilesystem
import com.romulus.mobile.downloads.output.ArchiveExtractionController
import com.romulus.mobile.downloads.output.OutputCleanupService
import com.romulus.mobile.downloads.output.OutputFinalizer
import com.romulus.mobile.downloads.output.OutputReservationService
import com.romulus.mobile.downloads.output.OutputRootResolver
import com.romulus.mobile.downloads.output.SevenZipArchiveRuntime
import com.romulus.mobile.downloads.work.DownloadWorkerEntryPoint
import com.romulus.mobile.downloads.work.AndroidNotificationApi
import com.romulus.mobile.downloads.work.ExecutionControlRegistry
import com.romulus.mobile.downloads.work.QueueNotificationPresenter
import com.romulus.mobile.downloads.work.WorkManagerWorkerLauncher
import com.romulus.mobile.downloads.work.WorkScheduler
import com.romulus.mobile.downloads.work.WorkWakeReason
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.remotezip.RemoteZipFacade
import com.romulus.mobile.source.browse.ArchiveContainerPreparationService
import java.io.File
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@Suppress("TooManyFunctions")
class DownloadsFacade internal constructor(
    private val settingsService: DownloadSettingsService?,
    private val queueService: QueueService?,
    private val summaryProjector: QueueSummaryProjector?,
    private val workScheduler: WorkScheduler?,
    private val workerEntryPoint: DownloadWorkerEntryPoint?
) {
    private val workerRunMutex = Mutex()
    private val scaffoldSettingsState = MutableStateFlow(
        DownloadSettingsState(
            outputDirectoryUri = null,
            maxConcurrency = DownloadLimits.DEFAULT_CONCURRENCY
        )
    )
    private val scaffoldSettingsReadiness = MutableStateFlow(
        DownloadSettingsReadiness(
            isUsable = false,
            brokenReason = "DownloadsFacade is not wired yet"
        )
    )
    private val scaffoldDownloadsProjection = MutableStateFlow(
        DownloadsProjection(
            summary = QueueSummary(
                completed = 0,
                total = 0,
                failed = 0,
                cancelled = 0
            ),
            rows = emptyList(),
            activeDownloads = false
        )
    )
    private val scaffoldActiveDownloadsFlag = MutableStateFlow(false)

    constructor() : this(
        settingsService = null,
        queueService = null,
        summaryProjector = null,
        workScheduler = null,
        workerEntryPoint = null
    )

    fun observeSettings(): StateFlow<DownloadSettingsState> =
        settingsService?.observeState() ?: scaffoldSettingsState.asStateFlow()

    fun observeSettingsReadiness(): StateFlow<DownloadSettingsReadiness> =
        settingsService?.observeReadiness() ?: scaffoldSettingsReadiness.asStateFlow()

    fun readSettingsReadiness(): DownloadSettingsReadiness =
        settingsService?.readReadiness() ?: scaffoldSettingsReadiness.value

    suspend fun revalidateSettingsReadiness(): DownloadSettingsReadiness =
        settingsService?.revalidateReadiness() ?: scaffoldSettingsReadiness.value

    suspend fun updateSettings(draft: DownloadSettingsDraft): Result<Unit> =
        settingsService?.update(draft)
            ?: Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    suspend fun enqueue(inputs: List<QueueTaskInput>): EnqueueResult =
        queueService?.enqueue(inputs) ?: EnqueueResult.Failed("DownloadsFacade is not wired yet")

    fun observeDownloadsProjection(): StateFlow<DownloadsProjection> =
        summaryProjector?.observeProjection() ?: scaffoldDownloadsProjection.asStateFlow()

    fun observeActiveDownloadsFlag(): StateFlow<Boolean> =
        summaryProjector?.observeActiveDownloads() ?: scaffoldActiveDownloadsFlag.asStateFlow()

    suspend fun performAction(command: QueueActionCommand): Result<Unit> =
        queueService?.performAction(command)
            ?: Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    suspend fun clearHistory(includeFailed: Boolean): Result<Unit> =
        queueService?.clearHistory(includeFailed)
            ?: Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    suspend fun requestAppLaunchRecovery(): Result<Unit> =
        workScheduler?.requestWake(WorkWakeReason.APP_LAUNCH_RECOVERY)
            ?: Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    internal suspend fun runWorkerUntilDrained(): Result<Unit> =
        workerEntryPoint?.let { entryPoint ->
            workerRunMutex.withLock {
                entryPoint.runUntilDrained()
            }
        }
            ?: Result.failure(UnsupportedOperationException("DownloadsFacade is not wired yet"))

    companion object {
        @Suppress("InjectDispatcher", "LongMethod")
        internal fun create(
            application: Application,
            realDebridFacade: RealDebridFacade,
            remoteZipFacade: RemoteZipFacade,
            archiveContainerPreparationService: ArchiveContainerPreparationService,
            diagnosticsFacade: DiagnosticsFacade
        ): DownloadsFacade {
            val clock = Clock.systemUTC()
            val json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                classDiscriminator = "kind"
            }
            val settingsService = runBlocking {
                DownloadSettingsService.create(
                    store = SharedPreferencesDownloadSettingsStore(application),
                    outputAccess = AndroidOutputDirectoryAccess(application)
                )
            }
            val ledgerStore: DownloadLedgerStore = FileDownloadLedgerStore(
                ledgerFile = File(application.filesDir, "downloads/queue-ledger.json"),
                json = json,
                clock = clock
            )
            val providerGateway = RealDebridProviderRuntimeGateway(realDebridFacade)
            val outputFilesystem = AndroidOutputFilesystem(application)
            val outputRootResolver = OutputRootResolver(
                settingsService = settingsService,
                clock = clock
            )
            val workScheduler = WorkScheduler(
                settingsService = settingsService,
                ledgerStore = ledgerStore,
                providerGateway = providerGateway,
                workerLauncher = WorkManagerWorkerLauncher(
                    context = application,
                    clock = clock
                ),
                clock = clock
            )
            val executionControlRegistry = ExecutionControlRegistry()
            val queueService = QueueService(
                ledgerStore = ledgerStore,
                executionControlRegistry = executionControlRegistry,
                outputCleanupService = OutputCleanupService(
                    outputFilesystem = outputFilesystem
                ),
                workScheduler = workScheduler,
                clock = clock,
                diagnosticsFacade = diagnosticsFacade
            )
            val outputReservationService = OutputReservationService(
                outputFilesystem = outputFilesystem,
                outputRootResolver = outputRootResolver,
                artifactRoot = File(application.filesDir, "downloads/runtime")
            )
            val summaryProjector = QueueSummaryProjector(
                ledgerStore = ledgerStore,
                dispatcher = Dispatchers.Default
            )
            val notificationPresenter = QueueNotificationPresenter(
                notificationApi = AndroidNotificationApi(application)
            )
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                summaryProjector.observeProjection().collect { projection ->
                    notificationPresenter.present(projection)
                    diagnosticsFacade.record(
                        domain = DiagnosticDomain.DOWNLOADS,
                        event = "projection-updated",
                        outcome = "observed",
                        context = mapOf(
                            "summaryCompleted" to projection.summary.completed.toString(),
                            "summaryTotal" to projection.summary.total.toString(),
                            "summaryFailed" to projection.summary.failed.toString(),
                            "summaryCancelled" to projection.summary.cancelled.toString()
                        )
                    )
                }
            }
            val workerEntryPoint = DownloadWorkerEntryPoint(
                queueService = queueService,
                recoveryPolicy = QueueRecoveryPolicy(outputReservationService),
                workScheduler = workScheduler,
                executionControlRegistry = executionControlRegistry,
                standardAttemptRunner = StandardAttemptRunner(
                    providerGateway = providerGateway,
                    downloadTransport = OkHttpDownloadTransport(okhttp3.OkHttpClient()),
                    outputReservationService = outputReservationService,
                    outputFinalizer = OutputFinalizer(
                        reservationService = outputReservationService,
                        extractionController = ArchiveExtractionController(
                            archiveRuntime = SevenZipArchiveRuntime(),
                            outputFilesystem = outputFilesystem
                        ),
                        outputFilesystem = outputFilesystem
                    ),
                    queueService = queueService,
                    clock = clock
                ),
                archiveEntryAttemptRunner = ArchiveEntryAttemptRunner(
                    archiveContainerGateway = SharedArchiveContainerGateway(
                        archiveContainerPreparationService
                    ),
                    remoteZipCopyGateway = FacadeRemoteZipCopyGateway(remoteZipFacade),
                    outputReservationService = outputReservationService,
                    outputFinalizer = OutputFinalizer(
                        reservationService = outputReservationService,
                        extractionController = ArchiveExtractionController(
                            archiveRuntime = SevenZipArchiveRuntime(),
                            outputFilesystem = outputFilesystem
                        ),
                        outputFilesystem = outputFilesystem
                    ),
                    queueService = queueService,
                    clock = clock
                ),
                clock = clock
            )
            return DownloadsFacade(
                settingsService = settingsService,
                queueService = queueService,
                summaryProjector = summaryProjector,
                workScheduler = workScheduler,
                workerEntryPoint = workerEntryPoint
            )
        }
    }
}
