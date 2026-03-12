package com.romulus.mobile.downloads.config

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DownloadSettingsService(
    private val store: DownloadSettingsStore,
    private val outputAccess: OutputDirectoryAccess,
    initialState: DownloadSettingsState
) {
    private val state = MutableStateFlow(initialState)
    private val readiness = MutableStateFlow<DownloadSettingsReadiness>(
        DownloadSettingsReadiness(
            isUsable = false,
            brokenReason = "Download settings are still loading"
        )
    )

    @Suppress("TooGenericExceptionCaught")
    suspend fun hydrate(): Result<Unit> = try {
        refreshReadiness()
        Result.success(Unit)
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    fun observeState(): StateFlow<DownloadSettingsState> = state.asStateFlow()

    fun observeReadiness(): StateFlow<DownloadSettingsReadiness> = readiness.asStateFlow()

    fun readState(): DownloadSettingsState = state.value

    fun readReadiness(): DownloadSettingsReadiness = readiness.value

    suspend fun revalidateReadiness(): DownloadSettingsReadiness {
        val nextReadiness = outputAccess.check(state.value.outputDirectoryUri)
        readiness.value = nextReadiness
        return nextReadiness
    }

    suspend fun update(draft: DownloadSettingsDraft): Result<Unit> = runCatching {
        normalize(draft)
    }.fold(
        onSuccess = { normalized ->
            val nextState = DownloadSettingsState(
                outputDirectoryUri = normalized.outputDirectoryUri,
                maxConcurrency = normalized.maxConcurrency
            )
            val nextReadiness = outputAccess.check(nextState.outputDirectoryUri)
            if (!nextReadiness.isUsable) {
                Result.failure(
                    IllegalStateException(
                        nextReadiness.brokenReason ?: "Download directory is not usable"
                    )
                )
            } else {
                store.write(nextState).fold(
                    onSuccess = {
                        state.value = nextState
                        readiness.value = nextReadiness
                        Result.success(Unit)
                    },
                    onFailure = { throwable -> Result.failure(throwable) }
                )
            }
        },
        onFailure = { throwable -> Result.failure(throwable) }
    )

    private suspend fun refreshReadiness() {
        revalidateReadiness()
    }

    private fun normalize(draft: DownloadSettingsDraft): DownloadSettingsDraft {
        val outputDirectoryUri = draft.outputDirectoryUri.trim()
        require(outputDirectoryUri.isNotBlank()) { "Download directory is required" }
        require(
            draft.maxConcurrency in
                DownloadLimits.MIN_CONCURRENCY..DownloadLimits.MAX_CONCURRENCY
        ) {
            "Max concurrency must be between ${DownloadLimits.MIN_CONCURRENCY} and ${DownloadLimits.MAX_CONCURRENCY}"
        }
        return DownloadSettingsDraft(
            outputDirectoryUri = outputDirectoryUri,
            maxConcurrency = draft.maxConcurrency
        )
    }

    companion object {
        suspend fun create(
            store: DownloadSettingsStore,
            outputAccess: OutputDirectoryAccess
        ): DownloadSettingsService {
            val initialState = store.read() ?: DownloadSettingsState(
                outputDirectoryUri = null,
                maxConcurrency = DownloadLimits.DEFAULT_CONCURRENCY
            )
            return DownloadSettingsService(
                store = store,
                outputAccess = outputAccess,
                initialState = initialState
            ).also { service ->
                service.hydrate().getOrThrow()
            }
        }
    }
}
