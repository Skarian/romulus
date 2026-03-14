@file:Suppress("ChainMethodContinuation", "ClassSignature")

package com.romulus.mobile.source.browse

import android.app.Application
import com.romulus.mobile.realdebrid.AcquisitionStatus
import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.realdebrid.ExactZipRequest
import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.realdebrid.ProviderLocator
import com.romulus.mobile.realdebrid.ProviderResumeMarker
import com.romulus.mobile.realdebrid.ProviderSourceRef
import com.romulus.mobile.realdebrid.RealDebridFacade
import com.romulus.mobile.remotezip.EnumerateRemoteZipRequest
import com.romulus.mobile.remotezip.EnumeratedRemoteZip
import com.romulus.mobile.remotezip.RemoteZipFacade
import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceSnapshotEntry
import java.time.Clock
import java.time.Duration
import kotlinx.serialization.json.Json

internal sealed interface ArchiveBrowseLoadResult {
    data class Preparing(
        val statusLabel: String?,
        val progressPercent: Double?,
        val timeoutAtEpochMillis: Long
    ) : ArchiveBrowseLoadResult

    data class Ready(
        val outerZip: ArchiveContainerLocator,
        val entries: EnumeratedRemoteZip
    ) : ArchiveBrowseLoadResult
}

internal class ArchiveBrowseEnumerationException(
    message: String,
    cause: Throwable? = null
) :
    IllegalStateException(message, cause)

private typealias ExactZipMatcher =
    suspend (ExactZipRequest) -> Result<ProviderFileRecord>
private typealias ArchivePreparationStarter =
    suspend (ProviderLocator) -> Result<AcquisitionStatus>
private typealias ArchivePreparationResumer =
    suspend (ProviderResumeMarker) -> Result<AcquisitionStatus>
private typealias ArchiveContainerMaterializer = suspend (
    ProviderFileRecord,
    AcquisitionStatus.LinksReady
) -> Result<ArchiveContainerLocator>
private typealias RemoteZipEnumerator =
    suspend (EnumerateRemoteZipRequest) -> Result<EnumeratedRemoteZip>

@Suppress("LongParameterList")
internal class ArchiveContainerPreparationService(
    private val stateStore: ArchivePreparationStateStore,
    private val findExactZipMatch: ExactZipMatcher,
    private val startArchivePreparation: ArchivePreparationStarter,
    private val resumeArchivePreparation: ArchivePreparationResumer,
    private val materializeArchiveContainer: ArchiveContainerMaterializer,
    private val enumerateRemoteZip: RemoteZipEnumerator,
    private val clock: Clock
) {
    suspend fun loadForBrowse(
        snapshotId: SnapshotId,
        entry: SourceSnapshotEntry
    ): Result<ArchiveBrowseLoadResult> {
        val preparationKey = ArchivePreparationKey(
            snapshotId = snapshotId,
            entryId = entry.entryId
        )
        val cachedState = stateStore.read(preparationKey)
        return when (cachedState) {
            is ArchivePreparationCachedState.Preparing -> advancePreparing(
                preparationKey = preparationKey,
                cached = cachedState
            )
            is ArchivePreparationCachedState.Ready -> Result.success(
                ArchiveBrowseLoadResult.Ready(
                    outerZip = cachedState.outerZip,
                    entries = cachedState.entries
                )
            )

            null -> startPreparing(preparationKey, entry)
        }
    }

    @Suppress("ReturnCount")
    suspend fun resolveReadyContainer(
        preparationKey: ArchivePreparationKey
    ): Result<ArchiveContainerLocator> {
        val cachedState = stateStore.read(preparationKey)
            ?: return Result.failure(
                IllegalStateException("Exact zip container preparation is missing")
            )
        return when (cachedState) {
            is ArchivePreparationCachedState.Preparing -> Result.failure(
                IllegalStateException("Exact zip container is not ready yet")
            )

            is ArchivePreparationCachedState.Ready -> {
                val refreshedStatus = resumeArchivePreparation(
                    cachedState.resumeMarker
                ).getOrElse { failure ->
                    return Result.failure(failure)
                }
                when (refreshedStatus) {
                    is AcquisitionStatus.Waiting -> Result.failure(
                        IllegalStateException("Exact zip container is not ready yet")
                    )

                    is AcquisitionStatus.LinksReady -> {
                        val refreshedOuterZip = materializeArchiveContainer(
                            cachedState.exactMatch,
                            refreshedStatus
                        ).getOrElse { failure ->
                            return Result.failure(failure)
                        }
                        stateStore.write(
                            key = preparationKey,
                            state = cachedState.copy(
                                resumeMarker = refreshedStatus.resumeMarker,
                                outerZip = refreshedOuterZip
                            )
                        ).getOrElse { failure ->
                            return Result.failure(failure)
                        }
                        Result.success(refreshedOuterZip)
                    }
                }
            }
        }
    }

    private suspend fun startPreparing(
        preparationKey: ArchivePreparationKey,
        entry: SourceSnapshotEntry
    ): Result<ArchiveBrowseLoadResult> {
        val exactMatch = findExactZipMatch(
            ExactZipRequest(
                sources = entry.torrents.map { torrent ->
                    ProviderSourceRef(
                        magnetUri = torrent.magnetUri,
                        partLabel = torrent.partLabel
                    )
                },
                exactPath = entry.scope.normalizedPath
            )
        ).getOrElse { failure ->
            return Result.failure(failure)
        }
        val enteredAt = clock.instant()
        val timeoutAt = enteredAt.plus(PREPARING_TIMEOUT)
        return advance(
            preparationKey = preparationKey,
            exactMatch = exactMatch,
            enteredAtEpochMillis = enteredAt.toEpochMilli(),
            timeoutAtEpochMillis = timeoutAt.toEpochMilli(),
            resumeMarker = null
        )
    }

    private suspend fun advancePreparing(
        preparationKey: ArchivePreparationKey,
        cached: ArchivePreparationCachedState.Preparing
    ): Result<ArchiveBrowseLoadResult> {
        if (!cached.timeoutAt.isAfter(clock.instant())) {
            stateStore.clear(preparationKey)
            return Result.failure(
                IllegalStateException("Exact zip container preparation timed out after 24 hours")
            )
        }
        return advance(
            preparationKey = preparationKey,
            exactMatch = cached.exactMatch,
            enteredAtEpochMillis = cached.enteredAt.toEpochMilli(),
            timeoutAtEpochMillis = cached.timeoutAt.toEpochMilli(),
            resumeMarker = cached.resumeMarker
        )
    }

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun advance(
        preparationKey: ArchivePreparationKey,
        exactMatch: ProviderFileRecord,
        enteredAtEpochMillis: Long,
        timeoutAtEpochMillis: Long,
        resumeMarker: ProviderResumeMarker?
    ): Result<ArchiveBrowseLoadResult> {
        val status = when (resumeMarker) {
            null -> startArchivePreparation(exactMatch.locator)
            else -> resumeArchivePreparation(resumeMarker)
        }.getOrElse { failure ->
            return Result.failure(failure)
        }

        return when (status) {
            is AcquisitionStatus.Waiting -> {
                stateStore.write(
                    key = preparationKey,
                    state = ArchivePreparationCachedState.Preparing(
                        exactMatch = exactMatch,
                        enteredAt = java.time.Instant.ofEpochMilli(enteredAtEpochMillis),
                        timeoutAt = java.time.Instant.ofEpochMilli(timeoutAtEpochMillis),
                        lastProviderStatus = status.statusLabel,
                        lastProviderProgress = status.progressPercent,
                        resumeMarker = status.resumeMarker
                    )
                ).getOrElse { failure ->
                    return Result.failure(failure)
                }
                Result.success(
                    ArchiveBrowseLoadResult.Preparing(
                        statusLabel = status.statusLabel,
                        progressPercent = status.progressPercent,
                        timeoutAtEpochMillis = timeoutAtEpochMillis
                    )
                )
            }

            is AcquisitionStatus.LinksReady -> {
                stateStore.write(
                    key = preparationKey,
                    state = ArchivePreparationCachedState.Preparing(
                        exactMatch = exactMatch,
                        enteredAt = java.time.Instant.ofEpochMilli(enteredAtEpochMillis),
                        timeoutAt = java.time.Instant.ofEpochMilli(timeoutAtEpochMillis),
                        lastProviderStatus = "downloaded",
                        lastProviderProgress = 100.0,
                        resumeMarker = status.resumeMarker
                    )
                ).getOrElse { failure ->
                    return Result.failure(failure)
                }
                val outerZip = materializeArchiveContainer(
                    exactMatch,
                    status
                ).getOrElse { failure ->
                    return Result.failure(failure)
                }
                val remoteZip = enumerateRemoteZip(
                    EnumerateRemoteZipRequest(archiveUrl = outerZip.archiveUrl)
                ).getOrElse { failure ->
                    return Result.failure(
                        ArchiveBrowseEnumerationException(
                            failure.message ?: "Archive entries could not be enumerated.",
                            failure
                        )
                    )
                }
                stateStore.write(
                    key = preparationKey,
                    state = ArchivePreparationCachedState.Ready(
                        exactMatch = exactMatch,
                        enteredAt = java.time.Instant.ofEpochMilli(enteredAtEpochMillis),
                        timeoutAt = java.time.Instant.ofEpochMilli(timeoutAtEpochMillis),
                        resumeMarker = status.resumeMarker,
                        outerZip = outerZip,
                        entries = remoteZip
                    )
                ).getOrElse { failure ->
                    return Result.failure(failure)
                }
                Result.success(
                    ArchiveBrowseLoadResult.Ready(
                        outerZip = outerZip,
                        entries = remoteZip
                    )
                )
            }
        }
    }

    companion object {
        private val PREPARING_TIMEOUT: Duration = Duration.ofHours(24)

        fun create(
            application: Application,
            realDebridFacade: RealDebridFacade,
            remoteZipFacade: RemoteZipFacade
        ): ArchiveContainerPreparationService {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            return ArchiveContainerPreparationService(
                stateStore = FileArchivePreparationStateStore(
                    cacheDirectory = application.filesDir.resolve("archive_browse_cache"),
                    json = json
                ),
                findExactZipMatch = realDebridFacade::findExactZipMatch,
                startArchivePreparation = { locator ->
                    realDebridFacade.startAcquisition(locator)
                },
                resumeArchivePreparation = realDebridFacade::resumeAcquisition,
                materializeArchiveContainer = realDebridFacade::materializeArchiveContainer,
                enumerateRemoteZip = { request -> remoteZipFacade.enumerate(request) },
                clock = Clock.systemUTC()
            )
        }
    }
}
