package com.romulus.spikes.spike3.runner

import com.romulus.spikes.spike3.TestSupport
import com.romulus.spikes.spike3.artifacts.ArtifactWriter
import com.romulus.spikes.spike3.model.Spike3CaseDefinitions
import com.romulus.spikes.spike3.server.FixtureHttpServer
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Spike3MatrixRunnerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun writesArtifactsAndKeepsSuccessPathOnRangeSemantics() {
        val cases = Spike3CaseDefinitions.defaultCases().filter {
            it.id in setOf(
                "run-a-regular-enumeration",
                "run-d-selected-only-duplicate",
                "run-e-no-range-failure",
            )
        }
        val runner = Spike3MatrixRunner(
            cases = cases,
            fixtureServer = FixtureHttpServer(TestSupport.fixtureRoot(), 0),
            artifactWriter = ArtifactWriter(tempDir.resolve("artifacts")),
        )

        val summary = runner.runAll()

        assertTrue(summary.allExpectationsMet)
        assertEquals(3, summary.results.size)
        assertTrue(Files.exists(summary.runDirectory.resolve("summary.md")))
        assertTrue(Files.exists(summary.runDirectory.resolve("http-trace.ndjson")))
        assertTrue(Files.exists(summary.runDirectory.resolve("cases/run-a-regular-enumeration/candidate-set.md")))
        assertTrue(Files.exists(summary.runDirectory.resolve("cases/run-d-selected-only-duplicate/download-manifest.md")))
        assertTrue(Files.isDirectory(summary.runDirectory.resolve("cases/run-d-selected-only-duplicate/downloads")))
        assertTrue(Files.exists(summary.runDirectory.resolve("cases/run-e-no-range-failure/failure.md")))
        assertTrue(Files.readString(summary.runDirectory.resolve("summary.md")).contains("Success-path ranged GETs downgraded to 200 responses: 0"))
    }
}
