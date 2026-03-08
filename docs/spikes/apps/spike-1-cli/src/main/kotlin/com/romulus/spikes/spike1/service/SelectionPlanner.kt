package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.model.RunCase
import com.romulus.spikes.spike1.model.ScopeKind
import com.romulus.spikes.spike1.model.SelectionResolution
import com.romulus.spikes.spike1.model.TorrentFileDto

class SelectionPlanner {
    fun resolve(runCase: RunCase, providerFiles: List<TorrentFileDto>): SelectionResolution {
        require(providerFiles.isNotEmpty()) { "Provider returned no files for ${runCase.id}" }
        val candidateFiles = when (runCase.scopeKind) {
            ScopeKind.ROOT -> providerFiles
            ScopeKind.DIRECTORY -> {
                val directory = normalizeDirectory(runCase.scopePath)
                providerFiles.filter { file ->
                    file.path == directory || file.path.startsWith("$directory/")
                }
            }
            ScopeKind.EXACT_PATH -> {
                val exactPath = normalizePath(runCase.scopePath)
                providerFiles.filter { it.path == exactPath }
            }
        }
        require(candidateFiles.isNotEmpty()) { "No provider files matched ${runCase.scopeKind} for ${runCase.id}" }
        if (runCase.scopeKind == ScopeKind.EXACT_PATH && candidateFiles.size != 1) {
            throw Spike1CliException("Exact path ${runCase.scopePath} matched ${candidateFiles.size} provider files in ${runCase.id}")
        }

        val desiredPaths = runCase.desiredPaths.map(::normalizePath)
        val resolvedFiles = desiredPaths.map { desiredPath ->
            candidateFiles.firstOrNull { it.path == desiredPath }
                ?: throw Spike1CliException("Desired path $desiredPath did not resolve inside ${runCase.id}")
        }.distinctBy { it.id }

        require(resolvedFiles.isNotEmpty()) { "No files selected for ${runCase.id}" }

        val useAllLiteral = resolvedFiles.size == providerFiles.size
        val payload = if (useAllLiteral) {
            "all"
        } else {
            resolvedFiles.joinToString(",") { it.id.toString() }
        }

        return SelectionResolution(
            scopeKind = runCase.scopeKind,
            scopePath = runCase.scopePath,
            desiredPaths = desiredPaths,
            candidateFiles = candidateFiles,
            resolvedFiles = resolvedFiles,
            payload = payload,
            usesAllLiteral = useAllLiteral,
        )
    }

    private fun normalizeDirectory(scopePath: String?): String {
        val normalized = normalizePath(scopePath)
        return if (normalized.length > 1 && normalized.endsWith('/')) normalized.dropLast(1) else normalized
    }

    private fun normalizePath(path: String?): String {
        val value = path?.trim().orEmpty()
        require(value.isNotEmpty()) { "Scope path must not be blank" }
        return if (value.startsWith('/')) value else "/$value"
    }
}
