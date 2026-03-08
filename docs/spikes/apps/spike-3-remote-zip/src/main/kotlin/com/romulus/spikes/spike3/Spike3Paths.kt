package com.romulus.spikes.spike3

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

private const val PROJECT_MARKER = "docs/spikes/apps/spike-3-remote-zip"

data class Spike3Paths(
    val repoRoot: Path,
    val projectRoot: Path,
    val fixtureHttpRoot: Path,
    val artifactRoot: Path,
) {
    companion object {
        fun discover(start: Path = Path.of("").toAbsolutePath()): Spike3Paths {
            var current: Path? = start.absolute().normalize()
            while (current != null) {
                val projectRoot = current.resolve(PROJECT_MARKER)
                if (projectRoot.exists()) {
                    return Spike3Paths(
                        repoRoot = current,
                        projectRoot = projectRoot,
                        fixtureHttpRoot = current.resolve("docs/spikes/fixtures/generated/spike-3/http-root"),
                        artifactRoot = current.resolve("docs/spikes/fixtures/generated/spike-3/run-artifacts"),
                    )
                }
                current = current.parent
            }
            error("Unable to locate repo root from $start")
        }
    }

    fun requireFixtureRoot() {
        require(Files.isDirectory(fixtureHttpRoot)) {
            "Fixture root not found at $fixtureHttpRoot. Run `cd docs/spikes && just fixtures` first."
        }
    }
}
