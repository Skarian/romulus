package com.romulus.mobile.source.snapshot

import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptedSourceConfigRecord
import com.romulus.mobile.source.ingest.SourceConfigStore
import com.romulus.mobile.source.ingest.SourceDocument
import com.romulus.mobile.source.ingest.SourceDocumentParser
import com.romulus.mobile.source.ingest.SourceEntryDocument
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.source.ingest.SourceValidation
import com.romulus.mobile.source.ingest.SourceValidationIssue
import com.romulus.mobile.source.ingest.StagedSourceConfig
import com.romulus.mobile.source.ingest.UnarchiveLayoutModeDocument
import com.romulus.mobile.source.ingest.normalizePath
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

internal interface SourceStateOwner {
    suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult

    suspend fun revalidateReadiness(): SourceReadiness

    fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?>

    fun observeHomeState(): StateFlow<HomeSourceState>

    fun observeReadiness(): StateFlow<SourceReadiness>

    suspend fun readStartupReadiness(): SourceReadiness
}

private const val SOURCE_ROOT_PATH = "/"
private const val BROKEN_FILE_MESSAGE = "Saved source file can no longer be read."
private const val LOAD_ERROR_MESSAGE = "Source could not be loaded."
private const val RETAINED_PRIOR_MESSAGE =
    "Source refresh failed. Using the previous source list."
private const val MISSING_SNAPSHOT_MESSAGE =
    "Source snapshot is unavailable. Showing no source rows."
private val UNCONFIGURED_HOME_STATE = HomeSourceState.SourceLoadError(
    message = "Source is not configured.",
    refreshAvailable = false
)
private val UNCONFIGURED_READINESS = SourceReadiness(
    acceptedMode = null,
    isUsable = false,
    hasUsableSnapshot = false,
    brokenReason = "Source is not configured."
)

@Suppress("TooManyFunctions")
internal class SnapshotPublisher(
    private val snapshotStore: SnapshotStore,
    private val sourceConfigStore: SourceConfigStore,
    private val activationStore: SourceActivationStore,
    private val parser: SourceDocumentParser,
    private val validation: SourceValidation,
    private val clock: Clock
) : SourceStateOwner {
    private val acceptedSourceSummary = MutableStateFlow<AcceptedSourceSummary?>(null)
    private val homeState = MutableStateFlow<HomeSourceState>(UNCONFIGURED_HOME_STATE)
    private val readiness = MutableStateFlow(UNCONFIGURED_READINESS)

    init {
        runBlocking {
            syncFromActive()
        }
    }

    override fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?> =
        acceptedSourceSummary.asStateFlow()

    override fun observeHomeState(): StateFlow<HomeSourceState> = homeState.asStateFlow()

    override fun observeReadiness(): StateFlow<SourceReadiness> = readiness.asStateFlow()

    override suspend fun readStartupReadiness(): SourceReadiness = revalidateReadiness()

    override suspend fun revalidateReadiness(): SourceReadiness {
        syncFromActive()
        return readiness.value
    }

    suspend fun stageReplacement(
        document: SourceDocument,
        acceptedAt: Instant
    ): Result<StagedSnapshot> = snapshotStore.writeStaged(document.toSnapshot(acceptedAt))

    suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord> {
        val priorActivation = activationStore.readActive()
        val activation = activationStore.activate(
            stagedSnapshot = stagedSnapshot,
            stagedConfig = stagedConfig,
            activatedAt = activatedAt
        )
        if (activation.isFailure) {
            return activation
        }

        cleanupSupersededActivePair(
            priorActivation = priorActivation,
            keepSnapshotId = stagedSnapshot.snapshotId,
            keepConfigStageId = stagedConfig.stageId
        )
        syncFromActive()
        return activation
    }

    suspend fun discard(staged: StagedSnapshot): Result<Unit> = snapshotStore.discard(staged)

    @Suppress("LongMethod", "ReturnCount")
    override suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult {
        val context = readRefreshContext()
            ?: return SourceRefreshResult.FailedWithoutSnapshot("Source is not configured.")
        if (context.config.mode != SourceMode.URL) {
            return resultForSnapshotAvailability(
                snapshotId = context.activation.snapshotId,
                hasSnapshot = context.hasSnapshot,
                message = "Only URL sources can be refreshed."
            )
        }

        val refreshedAt = clock.instant()
        val refreshCommand = AcceptSourceCommand(
            mode = context.config.mode,
            rawValue = context.config.rawValue,
            persistedUri = context.config.persistedUri
        )
        val document = parser.load(refreshCommand).getOrElse { error ->
            return handleRefreshFailure(
                context = context,
                refreshedAt = refreshedAt,
                message = error.message ?: "Source refresh failed."
            )
        }

        val issues = validation.validate(document)
        if (issues.isNotEmpty()) {
            return handleRefreshFailure(
                context = context,
                refreshedAt = refreshedAt,
                message = issues.toRefreshFailureMessage()
            )
        }

        val stagedSnapshotResult = stageReplacement(document, refreshedAt)
        val stagedSnapshot = stagedSnapshotResult.getOrElse { error ->
            return handleRefreshFailure(
                context = context,
                refreshedAt = refreshedAt,
                message = error.message ?: "Snapshot staging failed"
            )
        }
        val stagedConfigResult = sourceConfigStore.stageCandidate(
            context.config.copy(
                lastRefreshAt = refreshedAt,
                lastRefreshOutcome = SourceRefreshOutcome.ACCEPTED
            )
        )
        val stagedConfig = stagedConfigResult.getOrElse { error ->
            discard(stagedSnapshot)
            return handleRefreshFailure(
                context = context,
                refreshedAt = refreshedAt,
                message = error.message ?: "Accepted source record could not be staged"
            )
        }

        val activation = activate(
            stagedSnapshot = stagedSnapshot,
            stagedConfig = stagedConfig,
            activatedAt = refreshedAt
        )
        if (activation.isFailure) {
            sourceConfigStore.discardCandidate(stagedConfig)
            discard(stagedSnapshot)
            return handleRefreshFailure(
                context = context,
                refreshedAt = refreshedAt,
                message = "Source activation failed."
            )
        }

        return SourceRefreshResult.Replaced(stagedSnapshot.snapshotId)
    }

    private suspend fun readRefreshContext(): RefreshContext? {
        val activeActivation = activationStore.readActive()
        val activeConfig = activeActivation?.let { activation ->
            sourceConfigStore.readStaged(activation.configStageId)
        }
        val hasSnapshot = activeActivation?.let { activation ->
            snapshotStore.read(activation.snapshotId) != null
        } == true

        return if (activeActivation == null || activeConfig == null) {
            null
        } else {
            RefreshContext(
                activation = activeActivation,
                config = activeConfig,
                hasSnapshot = hasSnapshot
            )
        }
    }

    private suspend fun handleRefreshFailure(
        context: RefreshContext,
        refreshedAt: Instant,
        message: String
    ): SourceRefreshResult {
        val outcome = if (context.hasSnapshot) {
            SourceRefreshOutcome.RETAINED_PRIOR
        } else {
            SourceRefreshOutcome.FAILED_WITHOUT_SNAPSHOT
        }
        updateRefreshMetadata(
            activeActivation = context.activation,
            activeConfig = context.config,
            refreshedAt = refreshedAt,
            outcome = outcome
        )
        return resultForSnapshotAvailability(
            snapshotId = context.activation.snapshotId,
            hasSnapshot = context.hasSnapshot,
            message = message
        )
    }

    private fun resultForSnapshotAvailability(
        snapshotId: SnapshotId,
        hasSnapshot: Boolean,
        message: String
    ): SourceRefreshResult = if (hasSnapshot) {
        SourceRefreshResult.RetainedPrior(snapshotId, message)
    } else {
        SourceRefreshResult.FailedWithoutSnapshot(message)
    }

    private suspend fun updateRefreshMetadata(
        activeActivation: SourceActivationRecord,
        activeConfig: AcceptedSourceConfigRecord,
        refreshedAt: Instant,
        outcome: SourceRefreshOutcome
    ) {
        val stagedConfigResult = sourceConfigStore.stageCandidate(
            activeConfig.copy(
                lastRefreshAt = refreshedAt,
                lastRefreshOutcome = outcome
            )
        )
        val stagedConfig = stagedConfigResult.getOrNull() ?: return
        val activation = activate(
            stagedSnapshot = StagedSnapshot(activeActivation.snapshotId),
            stagedConfig = stagedConfig,
            activatedAt = refreshedAt
        )
        if (activation.isFailure) {
            sourceConfigStore.discardCandidate(stagedConfig)
        }
    }

    private suspend fun syncFromActive() {
        val projection = projectCurrentState()
        acceptedSourceSummary.value = projection.summary
        homeState.value = projection.homeState
        readiness.value = projection.readiness
    }

    private suspend fun projectCurrentState(): SourceStateProjection {
        val activeActivation = activationStore.readActive()
        val activeConfig = activeActivation?.let { activation ->
            sourceConfigStore.readStaged(activation.configStageId)
        }

        return if (activeActivation == null || activeConfig == null) {
            SourceStateProjection.unconfigured()
        } else {
            val snapshot = snapshotStore.read(activeActivation.snapshotId)
            val refreshAvailable = activeConfig.mode == SourceMode.URL
            val sourceFileReadable = isSourceFileReadable(activeConfig)
            when {
                !sourceFileReadable -> projectBrokenFileState(activeConfig, snapshot)
                snapshot == null -> {
                    projectMissingSnapshotState(
                        activation = activeActivation,
                        config = activeConfig,
                        refreshAvailable = refreshAvailable
                    )
                }

                else -> projectReadyState(activeConfig, snapshot, refreshAvailable)
            }
        }
    }

    private suspend fun isSourceFileReadable(activeConfig: AcceptedSourceConfigRecord): Boolean =
        if (activeConfig.mode == SourceMode.FILE) {
            val readResult = parser.canReadPersistedUri(
                activeConfig.persistedUri ?: activeConfig.rawValue
            )
            readResult.isSuccess
        } else {
            true
        }

    private fun projectBrokenFileState(
        config: AcceptedSourceConfigRecord,
        snapshot: SourceSnapshot?
    ): SourceStateProjection {
        val homeState = if (snapshot == null) {
            HomeSourceState.SourceLoadError(
                message = BROKEN_FILE_MESSAGE,
                refreshAvailable = false
            )
        } else {
            snapshot.toHomeContent(
                warning = null,
                refreshAvailable = false
            )
        }
        return SourceStateProjection(
            summary = config.toSummary(),
            homeState = homeState,
            readiness = SourceReadiness(
                acceptedMode = config.mode,
                isUsable = false,
                hasUsableSnapshot = snapshot != null,
                brokenReason = BROKEN_FILE_MESSAGE
            )
        )
    }

    private fun projectMissingSnapshotState(
        activation: SourceActivationRecord,
        config: AcceptedSourceConfigRecord,
        refreshAvailable: Boolean
    ): SourceStateProjection {
        val homeState = when (config.lastRefreshOutcome) {
            SourceRefreshOutcome.FAILED_WITHOUT_SNAPSHOT -> HomeSourceState.SourceLoadError(
                message = LOAD_ERROR_MESSAGE,
                refreshAvailable = refreshAvailable
            )

            else -> HomeSourceState.Content(
                snapshotId = activation.snapshotId,
                rows = emptyList(),
                warning = HomeSourceWarning.MissingSnapshotFallback(
                    MISSING_SNAPSHOT_MESSAGE
                ),
                refreshAvailable = refreshAvailable
            )
        }
        return SourceStateProjection(
            summary = config.toSummary(),
            homeState = homeState,
            readiness = SourceReadiness(
                acceptedMode = config.mode,
                isUsable = true,
                hasUsableSnapshot = false,
                brokenReason = null
            )
        )
    }

    private fun projectReadyState(
        config: AcceptedSourceConfigRecord,
        snapshot: SourceSnapshot,
        refreshAvailable: Boolean
    ): SourceStateProjection {
        val warning = when (config.lastRefreshOutcome) {
            SourceRefreshOutcome.RETAINED_PRIOR -> HomeSourceWarning.LatestRefreshFailed(
                RETAINED_PRIOR_MESSAGE
            )

            else -> null
        }
        return SourceStateProjection(
            summary = config.toSummary(),
            homeState = snapshot.toHomeContent(
                warning = warning,
                refreshAvailable = refreshAvailable
            ),
            readiness = SourceReadiness(
                acceptedMode = config.mode,
                isUsable = true,
                hasUsableSnapshot = true,
                brokenReason = null
            )
        )
    }

    private suspend fun cleanupSupersededActivePair(
        priorActivation: SourceActivationRecord?,
        keepSnapshotId: SnapshotId,
        keepConfigStageId: String
    ) {
        if (priorActivation == null) {
            return
        }
        if (priorActivation.configStageId != keepConfigStageId) {
            sourceConfigStore.discardCandidate(
                StagedSourceConfig(
                    stageId = priorActivation.configStageId,
                    acceptedAt = priorActivation.activatedAt
                )
            )
        }
        if (priorActivation.snapshotId != keepSnapshotId) {
            snapshotStore.discard(StagedSnapshot(priorActivation.snapshotId))
        }
    }
}

private data class RefreshContext(
    val activation: SourceActivationRecord,
    val config: AcceptedSourceConfigRecord,
    val hasSnapshot: Boolean
)

private data class SourceStateProjection(
    val summary: AcceptedSourceSummary?,
    val homeState: HomeSourceState,
    val readiness: SourceReadiness
) {
    companion object {
        fun unconfigured(): SourceStateProjection = SourceStateProjection(
            summary = null,
            homeState = UNCONFIGURED_HOME_STATE,
            readiness = UNCONFIGURED_READINESS
        )
    }
}

private fun SourceDocument.toSnapshot(acceptedAt: Instant): SourceSnapshot = SourceSnapshot(
    snapshotId = SnapshotId(UUID.randomUUID().toString()),
    acceptedAt = acceptedAt,
    entries = entries.mapIndexed { index, entry ->
        entry.toSnapshotEntry(index)
    }
)

private fun SourceEntryDocument.toSnapshotEntry(index: Int): SourceSnapshotEntry {
    val normalizedPath = normalizePath(path) ?: SOURCE_ROOT_PATH
    val rawIgnoreGlobs = ignore?.glob.orEmpty()
    val ignoreGlobs = rawIgnoreGlobs
        .map(String::trim)
        .filter(String::isNotBlank)
    return SourceSnapshotEntry(
        entryId = SourceEntryId(entryIdFor(index, normalizedPath)),
        displayName = displayName,
        subfolder = subfolder.trim(),
        torrents = torrents.map { torrent ->
            SourceTorrentRef(
                magnetUri = torrent.url,
                partLabel = torrent.partLabel
            )
        },
        normalizedPath = normalizedPath,
        ignoreGlobs = ignoreGlobs,
        renameRule = rename,
        unarchivePolicy = unarchive?.toPolicy()
    )
}

private fun com.romulus.mobile.source.ingest.UnarchiveDocument.toPolicy(): UnarchivePolicy =
    UnarchivePolicy(
        recursiveDefault = recursive,
        layout = ExtractionLayoutPolicy(
            mode = when (layout.mode) {
                UnarchiveLayoutModeDocument.FLAT -> ExtractionLayoutMode.FLAT
                UnarchiveLayoutModeDocument.DEDICATED_FOLDER ->
                    ExtractionLayoutMode.DEDICATED_FOLDER
            },
            folderRenameRule = layout.rename
        )
    )

private fun SourceEntryDocument.entryIdFor(index: Int, normalizedPath: String): String {
    val seed = buildString {
        append(index)
        append('|')
        append(displayName)
        append('|')
        append(subfolder)
        append('|')
        append(normalizedPath)
        torrents.forEach { torrent ->
            append('|')
            append(torrent.url)
            append('|')
            append(torrent.partLabel.orEmpty())
        }
    }
    return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
}

private fun AcceptedSourceConfigRecord.toSummary(): AcceptedSourceSummary = AcceptedSourceSummary(
    mode = mode,
    rawValue = rawValue,
    persistedUri = persistedUri,
    lastRefreshOutcome = lastRefreshOutcome
)

private fun SourceSnapshot.toHomeContent(
    warning: HomeSourceWarning?,
    refreshAvailable: Boolean
): HomeSourceState.Content = HomeSourceState.Content(
    snapshotId = snapshotId,
    rows = entries.map { entry ->
        HomeSourceRow(
            entryId = entry.entryId,
            displayName = entry.displayName,
            folderContext = entry.subfolder
        )
    },
    warning = warning,
    refreshAvailable = refreshAvailable
)

private fun List<SourceValidationIssue>.toRefreshFailureMessage(): String =
    firstOrNull()?.toRefreshFailureMessage() ?: "Source refresh failed."

private fun SourceValidationIssue.toRefreshFailureMessage(): String = when (this) {
    is SourceValidationIssue.InvalidVersion -> "Unsupported source version: $found."
    is SourceValidationIssue.InvalidSubfolder -> "Invalid subfolder: $subfolder."
    is SourceValidationIssue.InvalidPath -> "Invalid path: $path."
    is SourceValidationIssue.InvalidIgnoreRule -> "Invalid ignore rule: $pattern."
    is SourceValidationIssue.InvalidRenameRule -> message
    is SourceValidationIssue.InvalidUnarchiveRule -> message
}
