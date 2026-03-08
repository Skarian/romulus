package com.romulus.spikes.spike1.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

class WorkspacePaths private constructor(
    val projectDir: Path,
    val spikesDir: Path,
) {
    val defaultEnvFile: Path = spikesDir.resolve(".env.local")
    val artifactRoot: Path = spikesDir.resolve("fixtures/generated/spike-1/run-artifacts")
    val knownUncachedStateFile: Path = artifactRoot.resolve("known-uncached-active.json")

    companion object {
        fun discover(start: Path = Path.of("")): WorkspacePaths {
            val configuredProjectDir = System.getProperty("spike1.projectDir")
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) }
                ?.toAbsolutePath()
                ?.normalize()

            val projectDir = configuredProjectDir
                ?: findProjectDir(start.toAbsolutePath().normalize())
                ?: error("Unable to locate docs/spikes/apps/spike-1-cli from ${start.toAbsolutePath().normalize()}")

            val spikesDir = projectDir.parent?.parent?.normalize()
                ?: error("Unable to resolve docs/spikes from $projectDir")
            return WorkspacePaths(projectDir = projectDir, spikesDir = spikesDir)
        }

        private fun findProjectDir(start: Path): Path? {
            val directCandidate = start.resolve("docs/spikes/apps/spike-1-cli")
            if (isProjectDir(directCandidate)) {
                return directCandidate.normalize()
            }

            var current: Path? = start
            while (current != null) {
                if (isProjectDir(current)) {
                    return current.normalize()
                }
                current = current.parent
            }
            return null
        }

        private fun isProjectDir(path: Path): Boolean {
            return path.isDirectory() && Files.exists(path.resolve("README.md")) && Files.exists(path.resolve("PLAN.md"))
        }
    }
}
