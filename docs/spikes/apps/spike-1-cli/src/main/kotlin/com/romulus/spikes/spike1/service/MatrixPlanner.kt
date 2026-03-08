package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.config.Spike1Config
import com.romulus.spikes.spike1.model.MatrixPlan
import com.romulus.spikes.spike1.model.RunCase
import com.romulus.spikes.spike1.model.ScopeKind

class MatrixPlanner {
    fun plan(config: Spike1Config): MatrixPlan {
        return MatrixPlan(
            runs = listOf(
                RunCase(
                    id = "run-b-add-directory",
                    specLabel = "Run B",
                    executionOrder = 1,
                    scopeKind = ScopeKind.DIRECTORY,
                    scopePath = config.directoryScope,
                    desiredPaths = config.directorySelectedPaths,
                ),
                RunCase(
                    id = "run-a-add-root",
                    specLabel = "Run A",
                    executionOrder = 2,
                    scopeKind = ScopeKind.ROOT,
                    desiredPaths = config.rootSelectedPaths,
                ),
                RunCase(
                    id = "run-c-add-exact-zip",
                    specLabel = "Run C",
                    executionOrder = 3,
                    scopeKind = ScopeKind.EXACT_PATH,
                    scopePath = config.exactZipPath,
                    desiredPaths = listOf(config.exactZipPath),
                ),
                RunCase(
                    id = "run-d-selected-only-download",
                    specLabel = "Run D",
                    executionOrder = 4,
                    scopeKind = ScopeKind.EXACT_PATH,
                    scopePath = config.exactZipPath,
                    desiredPaths = listOf(config.exactZipPath),
                    downloadsSelectedFiles = true,
                ),
                RunCase(
                    id = "run-e-deterministic-failure",
                    specLabel = "Run E",
                    executionOrder = 5,
                    scopeKind = ScopeKind.EXACT_PATH,
                    deterministicFailure = true,
                ),
            )
        )
    }
}
