package com.romulus.mobile.source.ingest

import com.romulus.mobile.realdebrid.ProviderFileRecord
import java.nio.file.FileSystems
import java.nio.file.Paths

internal fun normalizePath(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.contains('\\')) {
        return null
    }
    val candidate = when {
        trimmed.isBlank() || trimmed == ROOT_PATH -> ROOT_PATH
        trimmed.startsWith(ROOT_PATH) -> trimmed
        else -> "$ROOT_PATH$trimmed"
    }
    val segments = candidate
        .split(PATH_SEPARATOR)
        .filter { it.isNotEmpty() }
    val hasTraversalSegments = segments.any { segment ->
        segment == CURRENT_DIRECTORY || segment == PARENT_DIRECTORY
    }
    return when {
        hasTraversalSegments -> null
        candidate.endsWith(PATH_SEPARATOR) -> candidate
        candidate.lowercase().endsWith(ZIP_SUFFIX) -> candidate
        else -> null
    }
}

internal fun isArchiveSelectionPath(path: String): Boolean = path.lowercase().endsWith(ZIP_SUFFIX)

internal fun String.isWithinScope(scope: String): Boolean {
    if (scope == ROOT_PATH) {
        return true
    }
    val normalizedProviderPath = this
        .replace('\\', PATH_SEPARATOR)
        .let { value -> if (value.startsWith(ROOT_PATH)) value else "$ROOT_PATH$value" }
    return if (scope.endsWith(PATH_SEPARATOR)) {
        normalizedProviderPath.startsWith(scope)
    } else {
        normalizedProviderPath == scope
    }
}

internal fun ProviderFileRecord.isWithinScope(scope: String): Boolean = path.isWithinScope(scope)

internal fun String.matchesIgnoreRules(ignoreGlobs: List<String>): Boolean {
    val basename = substringAfterLast(PATH_SEPARATOR).substringAfterLast('\\').lowercase()
    return ignoreGlobs.any { glob ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${glob.lowercase()}")
        matcher.matches(Paths.get(basename))
    }
}

internal fun isValidIgnoreRule(pattern: String): Boolean {
    val normalizedPattern = pattern.trim().lowercase()
    if (normalizedPattern.isBlank()) {
        return false
    }
    val matcherResult = runCatching {
        FileSystems.getDefault().getPathMatcher("glob:$normalizedPattern")
    }
    return matcherResult.isSuccess
}

private const val ROOT_PATH = "/"
private const val PATH_SEPARATOR = '/'
private const val CURRENT_DIRECTORY = "."
private const val PARENT_DIRECTORY = ".."
private const val ZIP_SUFFIX = ".zip"
