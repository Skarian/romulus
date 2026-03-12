package com.romulus.mobile.source.snapshot

import com.romulus.mobile.source.FakeSourceLoader
import com.romulus.mobile.source.InMemorySnapshotStore
import com.romulus.mobile.source.InMemorySourceActivationStore
import com.romulus.mobile.source.InMemorySourceConfigStore
import com.romulus.mobile.source.sourceSchemaValidator
import com.romulus.mobile.source.ingest.AcceptSourceCommand
import com.romulus.mobile.source.ingest.AcceptedSourceConfigRecord
import com.romulus.mobile.source.ingest.SourceDocumentParser
import com.romulus.mobile.source.ingest.SourceConfigStore
import com.romulus.mobile.source.ingest.SourceMode
import com.romulus.mobile.source.ingest.SourceValidation
import com.romulus.mobile.source.ingest.StagedSourceConfig
import com.romulus.mobile.source.snapshot.SourceActivationStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPublisherTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun failedRefreshRetainsPriorSnapshotAndWarnsHome() = runTest {
        val loader = FakeSourceLoader().apply {
            setUrlBody(
                "https://example.com/source.json",
                """
                    {
                      "version": 1,
                      "entries": [
                        {
                          "displayName": "Movies",
                          "subfolder": "movies",
                          "torrents": [{ "url": "magnet:?xt=urn:btih:one" }]
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
        val sourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val parser = SourceDocumentParser(loader, json, sourceSchemaValidator())
        val publisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )

        val initialConfig = sourceConfigStore.stageCandidate(
            AcceptedSourceConfigRecord(
                mode = SourceMode.URL,
                rawValue = "https://example.com/source.json",
                persistedUri = null,
                acceptedAt = clock.instant(),
                lastRefreshAt = null,
                lastRefreshOutcome = null
            )
        ).getOrThrow()
        val initialSnapshot = publisher.stageReplacement(
            document = parser.load(
                AcceptSourceCommand(
                    mode = SourceMode.URL,
                    rawValue = "https://example.com/source.json",
                    persistedUri = null
                )
            ).getOrThrow(),
            acceptedAt = clock.instant()
        ).getOrThrow()
        publisher.activate(
            stagedSnapshot = initialSnapshot,
            stagedConfig = initialConfig,
            activatedAt = clock.instant()
        ).getOrThrow()

        loader.setFailure(
            "https://example.com/source.json",
            IllegalStateException("Source request failed")
        )

        val result = publisher.refresh(SourceRefreshTrigger.HomeManualRefresh)

        val homeState = publisher.observeHomeState().value as HomeSourceState.Content
        assertTrue(result is SourceRefreshResult.RetainedPrior)
        assertTrue(homeState.warning is HomeSourceWarning.LatestRefreshFailed)
        assertTrue(publisher.readStartupReadiness().isUsable)
        assertTrue(publisher.readStartupReadiness().hasUsableSnapshot)
    }

    @Test
    fun unreadableFileSourceReportsBrokenReadinessOnStartup() = runTest {
        val loader = FakeSourceLoader().apply {
            setUriBody(
                "content://sources/current.json",
                """
                    {
                      "version": 1,
                      "entries": [
                        {
                          "displayName": "Movies",
                          "subfolder": "movies",
                          "torrents": [{ "url": "magnet:?xt=urn:btih:one" }]
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
        val sourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val parser = SourceDocumentParser(loader, json, sourceSchemaValidator())

        val publisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )
        val stagedConfig = sourceConfigStore.stageCandidate(
            AcceptedSourceConfigRecord(
                mode = SourceMode.FILE,
                rawValue = "content://sources/current.json",
                persistedUri = "content://sources/current.json",
                acceptedAt = clock.instant(),
                lastRefreshAt = null,
                lastRefreshOutcome = null
            )
        ).getOrThrow()
        val stagedSnapshot = publisher.stageReplacement(
            document = parser.load(
                AcceptSourceCommand(
                    mode = SourceMode.FILE,
                    rawValue = "content://sources/current.json",
                    persistedUri = "content://sources/current.json"
                )
            ).getOrThrow(),
            acceptedAt = clock.instant()
        ).getOrThrow()
        publisher.activate(
            stagedSnapshot = stagedSnapshot,
            stagedConfig = stagedConfig,
            activatedAt = clock.instant()
        ).getOrThrow()

        loader.setUnreadable("content://sources/current.json")

        val rehydratedPublisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = SourceDocumentParser(loader, json, sourceSchemaValidator()),
            validation = SourceValidation(),
            clock = clock
        )

        val readiness = rehydratedPublisher.readStartupReadiness()
        assertEquals(SourceMode.FILE, readiness.acceptedMode)
        assertTrue(!readiness.isUsable)
        assertEquals("Saved source file can no longer be read.", readiness.brokenReason)
    }

    @Test
    fun revalidateReadinessPublishesBrokenFileAfterRuntimeChange() = runTest {
        val loader = FakeSourceLoader().apply {
            setUriBody(
                "content://sources/current.json",
                """
                    {
                      "version": 1,
                      "entries": [
                        {
                          "displayName": "Movies",
                          "subfolder": "movies",
                          "torrents": [{ "url": "magnet:?xt=urn:btih:one" }]
                        }
                      ]
                    }
                """.trimIndent()
            )
        }
        val sourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val parser = SourceDocumentParser(loader, json, sourceSchemaValidator())
        val publisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )
        val stagedConfig = sourceConfigStore.stageCandidate(
            AcceptedSourceConfigRecord(
                mode = SourceMode.FILE,
                rawValue = "content://sources/current.json",
                persistedUri = "content://sources/current.json",
                acceptedAt = clock.instant(),
                lastRefreshAt = null,
                lastRefreshOutcome = null
            )
        ).getOrThrow()
        val stagedSnapshot = publisher.stageReplacement(
            document = parser.load(
                AcceptSourceCommand(
                    mode = SourceMode.FILE,
                    rawValue = "content://sources/current.json",
                    persistedUri = "content://sources/current.json"
                )
            ).getOrThrow(),
            acceptedAt = clock.instant()
        ).getOrThrow()
        publisher.activate(
            stagedSnapshot = stagedSnapshot,
            stagedConfig = stagedConfig,
            activatedAt = clock.instant()
        ).getOrThrow()

        loader.setUnreadable("content://sources/current.json")
        val readiness = publisher.revalidateReadiness()

        assertTrue(!readiness.isUsable)
        assertEquals("Saved source file can no longer be read.", readiness.brokenReason)
    }

    @Test
    fun snapshotStagingFailureRetainsPriorSnapshotDuringRefresh() = runTest {
        val loader = seededLoader()
        val sourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val baseSnapshotStore = InMemorySnapshotStore()
        val parser = SourceDocumentParser(loader, json, sourceSchemaValidator())
        val seedPublisher = SnapshotPublisher(
            snapshotStore = baseSnapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )
        activateInitialUrlSource(
            publisher = seedPublisher,
            parser = parser,
            store = sourceConfigStore
        )
        loader.setUrlBody(
            "https://example.com/source.json",
            """
                {
                  "version": 1,
                  "entries": [
                    {
                      "displayName": "Shows",
                      "subfolder": "shows",
                      "torrents": [{ "url": "magnet:?xt=urn:btih:two" }]
                    }
                  ]
                }
            """.trimIndent()
        )
        val failingPublisher = SnapshotPublisher(
            snapshotStore = FailingSnapshotStore(
                delegate = baseSnapshotStore,
                writeFailure = IllegalStateException("Snapshot staging failed")
            ),
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )

        val result = failingPublisher.refresh(SourceRefreshTrigger.HomeManualRefresh)

        assertTrue(result is SourceRefreshResult.RetainedPrior)
        assertEquals(SourceRefreshOutcome.RETAINED_PRIOR, currentRefreshOutcome(sourceConfigStore, activationStore))
    }

    @Test
    fun configStagingFailureRetainsPriorSnapshotDuringRefresh() = runTest {
        val loader = seededLoader()
        val baseSourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val parser = SourceDocumentParser(loader, json, sourceSchemaValidator())
        val seedPublisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = baseSourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )
        activateInitialUrlSource(
            publisher = seedPublisher,
            parser = parser,
            store = baseSourceConfigStore
        )
        loader.setUrlBody(
            "https://example.com/source.json",
            """
                {
                  "version": 1,
                  "entries": [
                    {
                      "displayName": "Shows",
                      "subfolder": "shows",
                      "torrents": [{ "url": "magnet:?xt=urn:btih:two" }]
                    }
                  ]
                }
            """.trimIndent()
        )
        val failingStore = FailingSourceConfigStore(
            delegate = baseSourceConfigStore,
            stageFailure = IllegalStateException("Accepted source record could not be staged")
        )
        val failingPublisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = failingStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )

        val result = failingPublisher.refresh(SourceRefreshTrigger.HomeManualRefresh)

        assertTrue(result is SourceRefreshResult.RetainedPrior)
        assertTrue(failingPublisher.readStartupReadiness().isUsable)
        assertTrue(failingPublisher.readStartupReadiness().hasUsableSnapshot)
    }

    @Test
    fun activationFailureRetainsPriorMetadataAndWarning() = runTest {
        val loader = seededLoader()
        val sourceConfigStore = InMemorySourceConfigStore()
        val baseActivationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val parser = SourceDocumentParser(loader, json, sourceSchemaValidator())
        val seedPublisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = baseActivationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )
        activateInitialUrlSource(
            publisher = seedPublisher,
            parser = parser,
            store = sourceConfigStore
        )
        loader.setUrlBody(
            "https://example.com/source.json",
            """
                {
                  "version": 1,
                  "entries": [
                    {
                      "displayName": "Shows",
                      "subfolder": "shows",
                      "torrents": [{ "url": "magnet:?xt=urn:btih:two" }]
                    }
                  ]
                }
            """.trimIndent()
        )
        val activationStore = FailingOnceSourceActivationStore(baseActivationStore)
        val publisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = parser,
            validation = SourceValidation(),
            clock = clock
        )

        val result = publisher.refresh(SourceRefreshTrigger.HomeManualRefresh)

        assertTrue(result is SourceRefreshResult.RetainedPrior)
        assertEquals(
            SourceRefreshOutcome.RETAINED_PRIOR,
            currentRefreshOutcome(sourceConfigStore, baseActivationStore)
        )
        val homeState = publisher.observeHomeState().value as HomeSourceState.Content
        assertTrue(homeState.warning is HomeSourceWarning.LatestRefreshFailed)
        assertTrue(publisher.readStartupReadiness().isUsable)
        assertTrue(publisher.readStartupReadiness().hasUsableSnapshot)
    }

    private suspend fun activateInitialUrlSource(
        publisher: SnapshotPublisher,
        parser: SourceDocumentParser,
        store: InMemorySourceConfigStore
    ) {
        val initialConfig = store.stageCandidate(
            AcceptedSourceConfigRecord(
                mode = SourceMode.URL,
                rawValue = "https://example.com/source.json",
                persistedUri = null,
                acceptedAt = clock.instant(),
                lastRefreshAt = null,
                lastRefreshOutcome = null
            )
        ).getOrThrow()
        val initialSnapshot = publisher.stageReplacement(
            document = parser.load(
                AcceptSourceCommand(
                    mode = SourceMode.URL,
                    rawValue = "https://example.com/source.json",
                    persistedUri = null
                )
            ).getOrThrow(),
            acceptedAt = clock.instant()
        ).getOrThrow()
        publisher.activate(
            stagedSnapshot = initialSnapshot,
            stagedConfig = initialConfig,
            activatedAt = clock.instant()
        ).getOrThrow()
    }

    private suspend fun currentRefreshOutcome(
        store: InMemorySourceConfigStore,
        activationStore: InMemorySourceActivationStore
    ): SourceRefreshOutcome? =
        store.readStaged(checkNotNull(activationStore.active).configStageId)?.lastRefreshOutcome

    private fun seededLoader(): FakeSourceLoader = FakeSourceLoader().apply {
        setUrlBody(
            "https://example.com/source.json",
            """
                {
                  "version": 1,
                  "entries": [
                    {
                      "displayName": "Movies",
                      "subfolder": "movies",
                      "torrents": [{ "url": "magnet:?xt=urn:btih:one" }]
                    }
                  ]
                }
            """.trimIndent()
        )
    }
}

private class FailingSnapshotStore(
    private val delegate: SnapshotStore,
    private val writeFailure: Throwable
) : SnapshotStore by delegate {
    override suspend fun writeStaged(snapshot: SourceSnapshot): Result<StagedSnapshot> =
        Result.failure(writeFailure)
}

private class FailingSourceConfigStore(
    private val delegate: SourceConfigStore,
    private val stageFailure: Throwable
) : SourceConfigStore by delegate {
    override suspend fun stageCandidate(next: AcceptedSourceConfigRecord): Result<StagedSourceConfig> =
        Result.failure(stageFailure)
}

private class FailingOnceSourceActivationStore(
    private val delegate: InMemorySourceActivationStore
) : SourceActivationStore by delegate {
    private var failNextActivation = true

    override suspend fun activate(
        stagedSnapshot: StagedSnapshot,
        stagedConfig: StagedSourceConfig,
        activatedAt: Instant
    ): Result<SourceActivationRecord> {
        if (failNextActivation) {
            failNextActivation = false
            return Result.failure(IllegalStateException("Source activation failed."))
        }
        return delegate.activate(stagedSnapshot, stagedConfig, activatedAt)
    }
}
