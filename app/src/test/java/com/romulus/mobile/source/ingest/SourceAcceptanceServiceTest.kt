package com.romulus.mobile.source.ingest

import com.romulus.mobile.source.FakeSourceLoader
import com.romulus.mobile.source.InMemorySnapshotStore
import com.romulus.mobile.source.InMemorySourceActivationStore
import com.romulus.mobile.source.InMemorySourceConfigStore
import com.romulus.mobile.source.sourceSchemaValidator
import com.romulus.mobile.source.snapshot.HomeSourceState
import com.romulus.mobile.source.snapshot.SnapshotPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAcceptanceServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun acceptsValidUrlSourceAndPublishesState() = runTest {
        val loader = FakeSourceLoader().apply {
            setUrlBody(
                "https://example.com/source.json",
                validSourceJson(displayName = "Movies", subfolder = "movies")
            )
        }
        val sourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val publisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = SourceDocumentParser(loader, json, sourceSchemaValidator()),
            validation = SourceValidation(),
            clock = clock
        )
        val service = SourceAcceptanceService(
            parser = SourceDocumentParser(loader, json, sourceSchemaValidator()),
            validation = SourceValidation(),
            sourceConfigStore = sourceConfigStore,
            snapshotPublisher = publisher,
            clock = clock
        )

        val result = service.accept(
            AcceptSourceCommand(
                mode = SourceMode.URL,
                rawValue = "https://example.com/source.json",
                persistedUri = null
            )
        )

        assertTrue(result is AcceptSourceResult.Accepted)
        assertEquals(SourceMode.URL, publisher.observeAcceptedSourceSummary().value?.mode)
        assertTrue(publisher.readStartupReadiness().isUsable)
        assertTrue(publisher.observeHomeState().value is HomeSourceState.Content)
    }

    @Test
    fun invalidSourceLeavesPriorActiveSourceVisible() = runTest {
        val loader = FakeSourceLoader().apply {
            setUrlBody(
                "https://example.com/valid.json",
                validSourceJson(displayName = "Movies", subfolder = "movies")
            )
            setUrlBody(
                "https://example.com/invalid.json",
                validSourceJson(
                    displayName = "Broken",
                    subfolder = "broken",
                    path = "/movie.mkv"
                )
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
        val service = SourceAcceptanceService(
            parser = parser,
            validation = SourceValidation(),
            sourceConfigStore = sourceConfigStore,
            snapshotPublisher = publisher,
            clock = clock
        )

        val accepted = service.accept(
            AcceptSourceCommand(
                mode = SourceMode.URL,
                rawValue = "https://example.com/valid.json",
                persistedUri = null
            )
        ) as AcceptSourceResult.Accepted

        val rejected = service.accept(
            AcceptSourceCommand(
                mode = SourceMode.URL,
                rawValue = "https://example.com/invalid.json",
                persistedUri = null
            )
        )

        val homeState = publisher.observeHomeState().value as HomeSourceState.Content
        assertTrue(
            rejected is AcceptSourceResult.Rejected ||
                rejected is AcceptSourceResult.Failed
        )
        assertEquals("https://example.com/valid.json", publisher.observeAcceptedSourceSummary().value?.rawValue)
        assertEquals(accepted.snapshotId, homeState.snapshotId)
    }

    @Test
    fun schemaInvalidSourceDoesNotActivate() = runTest {
        val loader = FakeSourceLoader().apply {
            setUrlBody(
                "https://example.com/invalid-schema.json",
                validSourceJson(displayName = "   ", subfolder = "movies")
            )
        }
        val sourceConfigStore = InMemorySourceConfigStore()
        val activationStore = InMemorySourceActivationStore()
        val snapshotStore = InMemorySnapshotStore()
        val publisher = SnapshotPublisher(
            snapshotStore = snapshotStore,
            sourceConfigStore = sourceConfigStore,
            activationStore = activationStore,
            parser = SourceDocumentParser(loader, json, sourceSchemaValidator()),
            validation = SourceValidation(),
            clock = clock
        )
        val service = SourceAcceptanceService(
            parser = SourceDocumentParser(loader, json, sourceSchemaValidator()),
            validation = SourceValidation(),
            sourceConfigStore = sourceConfigStore,
            snapshotPublisher = publisher,
            clock = clock
        )

        val result = service.accept(
            AcceptSourceCommand(
                mode = SourceMode.URL,
                rawValue = "https://example.com/invalid-schema.json",
                persistedUri = null
            )
        )

        assertTrue(result is AcceptSourceResult.Failed)
        assertEquals(null, publisher.observeAcceptedSourceSummary().value)
    }

    private fun validSourceJson(
        displayName: String,
        subfolder: String,
        path: String = "/"
    ): String = """
        {
          "version": 1,
          "entries": [
            {
              "displayName": "$displayName",
              "subfolder": "$subfolder",
              "path": "$path",
              "torrents": [
                { "url": "magnet:?xt=urn:btih:one", "partName": "Part A" }
              ]
            }
          ]
        }
    """.trimIndent()
}
