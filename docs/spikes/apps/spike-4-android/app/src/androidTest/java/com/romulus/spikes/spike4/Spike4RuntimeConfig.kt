package com.romulus.spikes.spike4

import android.content.res.AssetManager
import kotlinx.serialization.Serializable

@Serializable
data class Spike4RuntimeConfig(
    val rdApiToken: String,
    val magnet: String,
    val torrentPath: String,
    val exactZipPath: String,
    val selectedInternalPaths: List<String>,
    val ignoreGlobs: List<String>,
    val unarchive: Boolean,
    val destinationSubfolder: String,
    val renamePattern: String,
    val renameReplacement: String,
) {
    val normalizedTorrentPath: String
        get() = normalizeOptionalPath(torrentPath, "/")

    val normalizedExactZipPath: String
        get() = normalizeRequiredPath(exactZipPath)

    val normalizedSelectedInternalPaths: List<String>
        get() = selectedInternalPaths.map(::normalizeRequiredRelativePath)

    val normalizedIgnoreGlobs: List<String>
        get() = ignoreGlobs.map { it.trim() }.filter { it.isNotEmpty() }

    val renameRule: RenameRule?
        get() = renamePattern.takeIf { it.isNotBlank() }?.let { RenameRule(it, renameReplacement) }

    val normalizedDestinationSubfolder: String
        get() = destinationSubfolder.trim().ifEmpty { "default" }

    fun validate(): Spike4RuntimeConfig {
        check(rdApiToken.isNotBlank()) { "RD API token is missing." }
        check(magnet.isNotBlank()) { "Spike 4 magnet is missing." }
        normalizedExactZipPath
        check(normalizedSelectedInternalPaths.isNotEmpty()) { "Spike 4 selected internal paths are missing." }
        return this
    }

    private fun normalizeOptionalPath(path: String, fallback: String): String {
        val value = path.trim()
        if (value.isEmpty()) {
            return fallback
        }
        return normalizeRequiredPath(value)
    }

    private fun normalizeRequiredPath(path: String): String {
        val value = path.trim()
        check(value.isNotEmpty()) { "Path must not be blank." }
        return if (value.startsWith('/')) value else "/$value"
    }

    private fun normalizeRequiredRelativePath(path: String): String {
        val value = path.trim().replace('\\', '/')
        check(value.isNotEmpty()) { "Relative path must not be blank." }
        check(!value.startsWith('/')) { "Relative path must not start with /: $value" }
        return value
    }
}

object Spike4RuntimeConfigLoader {
    fun load(assetManager: AssetManager): Spike4RuntimeConfig {
        val raw = assetManager.open("config/runtime-config.json").bufferedReader().use { it.readText() }
        return spike4Json.decodeFromString<Spike4RuntimeConfig>(raw).validate()
    }
}
