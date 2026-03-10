package com.romulus.mobile.source

import com.romulus.mobile.source.browse.BrowseFailure
import com.romulus.mobile.source.browse.BrowseRequest
import com.romulus.mobile.source.browse.BrowseResult
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptSourceResult
import com.romulus.mobile.source.snapshot.AcceptedSourceSummary
import com.romulus.mobile.source.snapshot.HomeSourceState
import com.romulus.mobile.source.snapshot.SourceReadiness
import com.romulus.mobile.source.snapshot.SourceRefreshResult
import com.romulus.mobile.source.snapshot.SourceRefreshTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Suppress("RedundantSuspendModifier", "UnusedParameter")
class SourceFacade {
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

    suspend fun accept(command: AcceptSourceCommand): AcceptSourceResult =
        AcceptSourceResult.Failed("SourceFacade is not wired yet")

    suspend fun refresh(trigger: SourceRefreshTrigger): SourceRefreshResult =
        SourceRefreshResult.FailedWithoutSnapshot("SourceFacade is not wired yet")

    fun observeHomeState(): StateFlow<HomeSourceState> = homeState.asStateFlow()

    fun observeReadiness(): StateFlow<SourceReadiness> = readiness.asStateFlow()

    suspend fun readStartupReadiness(): SourceReadiness = readiness.value

    fun observeAcceptedSourceSummary(): StateFlow<AcceptedSourceSummary?> =
        acceptedSourceSummary.asStateFlow()

    suspend fun browse(request: BrowseRequest): BrowseResult = BrowseResult.Failed(
        BrowseFailure.StandardResolver("SourceFacade is not wired yet")
    )
}
