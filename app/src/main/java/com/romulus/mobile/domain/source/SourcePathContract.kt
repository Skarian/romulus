package com.romulus.mobile.domain.source

object SourcePathContract {
    private const val ROOT = "/"
    private const val FORWARD_SLASH_ERROR = "path must use forward slashes"
    private const val TRAVERSAL_ERROR = "path must be relative to torrent root and cannot contain .."

    fun normalizeConfiguredPath(rawPath: String?): ConfiguredPathNormalization {
        val trimmed = rawPath?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed == ".") {
            return ConfiguredPathNormalization.Valid(ROOT)
        }
        if (trimmed.contains('\\')) {
            return ConfiguredPathNormalization.Invalid(FORWARD_SLASH_ERROR)
        }

        val normalizedSegments = trimmed
            .split("/")
            .filter { segment -> segment.isNotEmpty() && segment != "." }
        if (normalizedSegments.any { it == ".." }) {
            return ConfiguredPathNormalization.Invalid(TRAVERSAL_ERROR)
        }
        if (normalizedSegments.isEmpty()) {
            return ConfiguredPathNormalization.Valid(ROOT)
        }
        return ConfiguredPathNormalization.Valid("$ROOT${normalizedSegments.joinToString("/")}")
    }

    fun matches(scopePath: String, candidatePath: String): Boolean {
        val normalizedScope = when (val normalized = normalizeConfiguredPath(scopePath)) {
            is ConfiguredPathNormalization.Valid -> normalized.path
            is ConfiguredPathNormalization.Invalid -> return false
        }
        if (normalizedScope == ROOT) {
            return true
        }

        val normalizedCandidate = candidatePath
            .replace('\\', '/')
            .trim()
            .split("/")
            .filter { segment -> segment.isNotEmpty() && segment != "." }
            .joinToString(
                prefix = ROOT,
                separator = "/"
            )

        return normalizedCandidate == normalizedScope || normalizedCandidate.startsWith("$normalizedScope/")
    }
}

sealed interface ConfiguredPathNormalization {
    data class Valid(val path: String) : ConfiguredPathNormalization

    data class Invalid(val message: String) : ConfiguredPathNormalization
}
