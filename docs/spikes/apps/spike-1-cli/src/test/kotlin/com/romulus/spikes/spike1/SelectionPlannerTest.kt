package com.romulus.spikes.spike1

import com.romulus.spikes.spike1.model.RunCase
import com.romulus.spikes.spike1.model.ScopeKind
import com.romulus.spikes.spike1.model.TorrentFileDto
import com.romulus.spikes.spike1.service.SelectionPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionPlannerTest {
    private val planner = SelectionPlanner()
    private val providerFiles = listOf(
        TorrentFileDto(id = 1, path = "/Root/one.mkv"),
        TorrentFileDto(id = 2, path = "/Root/Folder/two.zip"),
        TorrentFileDto(id = 3, path = "/Root/Folder/three.srt"),
    )

    @Test
    fun resolvesDirectoryScopeToExplicitIds() {
        val resolution = planner.resolve(
            RunCase(
                id = "run-b-add-directory",
                specLabel = "Run B",
                executionOrder = 1,
                scopeKind = ScopeKind.DIRECTORY,
                scopePath = "/Root/Folder",
                desiredPaths = listOf("/Root/Folder/two.zip"),
            ),
            providerFiles,
        )

        assertEquals(listOf(2), resolution.resolvedFiles.map { it.id })
        assertEquals(listOf(2, 3), resolution.candidateFiles.map { it.id })
        assertEquals("2", resolution.payload)
        assertFalse(resolution.usesAllLiteral)
    }

    @Test
    fun resolvesExactPathToSingleCandidate() {
        val resolution = planner.resolve(
            RunCase(
                id = "run-c-add-exact-zip",
                specLabel = "Run C",
                executionOrder = 1,
                scopeKind = ScopeKind.EXACT_PATH,
                scopePath = "/Root/Folder/two.zip",
                desiredPaths = listOf("/Root/Folder/two.zip"),
            ),
            providerFiles,
        )

        assertEquals(listOf(2), resolution.resolvedFiles.map { it.id })
        assertEquals(listOf(2), resolution.candidateFiles.map { it.id })
    }

    @Test
    fun usesAllLiteralOnlyWhenEveryProviderFileIsSelected() {
        val resolution = planner.resolve(
            RunCase(
                id = "run-a-add-root",
                specLabel = "Run A",
                executionOrder = 1,
                scopeKind = ScopeKind.ROOT,
                desiredPaths = providerFiles.map { it.path },
            ),
            providerFiles,
        )

        assertTrue(resolution.usesAllLiteral)
        assertEquals("all", resolution.payload)
    }
}
