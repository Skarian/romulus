package com.romulus.spikes.spike5.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

class WorkspacePaths private constructor(
    val projectDir: Path,
    val spikesDir: Path,
) {
    val defaultEnvFile: Path = projectDir.resolve(".env.local")
    val artifactRoot: Path = spikesDir.resolve("fixtures/generated/spike-5/run-artifacts")

    companion object {
        fun discover(start: Path = Path.of("")): WorkspacePaths {
            val configuredProjectDir = System.getProperty("spike5.projectDir")
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) }
                ?.toAbsolutePath()
                ?.normalize()

            val projectDir = configuredProjectDir
                ?: findProjectDir(start.toAbsolutePath().normalize())
                ?: error("Unable to locate docs/spikes/apps/spike-5-try-all-api from ${start.toAbsolutePath().normalize()}")

            val spikesDir = projectDir.parent?.parent?.normalize()
                ?: error("Unable to resolve docs/spikes from $projectDir")
            return WorkspacePaths(projectDir = projectDir, spikesDir = spikesDir)
        }

        private fun findProjectDir(start: Path): Path? {
            val directCandidate = start.resolve("docs/spikes/apps/spike-5-try-all-api")
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
