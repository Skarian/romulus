package com.romulus.mobile.source.ingest

import com.romulus.mobile.realdebrid.ProviderFileRecord
import com.romulus.mobile.source.snapshot.SourcePathScope
import java.nio.file.FileSystems
import java.nio.file.Paths

internal fun defaultSourcePathScope(): SourcePathScope = SourcePathScope(
    normalizedPath = ROOT_PATH,
    includeNestedFiles = false
)

internal fun normalizeScope(scope: SourceScopeDocument?): SourcePathScope? = when (scope) {
    null -> defaultSourcePathScope()
    else -> normalizeDeclaredPath(scope.path)?.let { normalizedPath ->
        SourcePathScope(
            normalizedPath = normalizedPath,
            includeNestedFiles = scope.includeNestedFiles
        )
    }
}

internal fun normalizeDeclaredPath(raw: String): String? {
    val trimmed = raw.trim()
    val candidate = when {
        trimmed.contains('\\') || trimmed.isBlank() -> null
        trimmed == ROOT_PATH -> ROOT_PATH
        trimmed.startsWith(ROOT_PATH) -> trimmed
        else -> "$ROOT_PATH$trimmed"
    }
    val hasTraversalSegments = candidate
        ?.split(PATH_SEPARATOR)
        ?.filter { it.isNotEmpty() }
        ?.any { segment ->
            segment == CURRENT_DIRECTORY || segment == PARENT_DIRECTORY
        }
        ?: false
    return when {
        candidate == null -> null
        hasTraversalSegments -> null
        candidate.endsWith(PATH_SEPARATOR) -> candidate
        candidate.lowercase().endsWith(ZIP_SUFFIX) -> candidate
        else -> null
    }
}

internal fun isArchiveSelectionPath(path: String): Boolean = path.lowercase().endsWith(ZIP_SUFFIX)

internal fun isScopeSemanticallyValid(scope: SourcePathScope): Boolean = when {
    !isArchiveSelectionPath(scope.normalizedPath) -> true
    !scope.includeNestedFiles -> true
    else -> false
}

internal fun String.isWithinScope(scope: SourcePathScope): Boolean {
    val normalizedProviderPath = normalizeProviderPath(this)
    val normalizedScopePath = scope.normalizedPath
    return when {
        isArchiveSelectionPath(normalizedScopePath) -> normalizedProviderPath == normalizedScopePath
        normalizedScopePath == ROOT_PATH && scope.includeNestedFiles -> true
        normalizedScopePath == ROOT_PATH -> {
            normalizedProviderPath
                .removePrefix(ROOT_PATH)
                .contains(PATH_SEPARATOR)
                .not()
        }
        scope.includeNestedFiles -> normalizedProviderPath.startsWith(normalizedScopePath)
        else -> {
            if (!normalizedProviderPath.startsWith(normalizedScopePath)) {
                false
            } else {
                normalizedProviderPath
                    .removePrefix(normalizedScopePath)
                    .contains(PATH_SEPARATOR)
                    .not()
            }
        }
    }
}

internal fun ProviderFileRecord.isWithinScope(scope: SourcePathScope): Boolean =
    path.isWithinScope(scope)

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

private fun normalizeProviderPath(raw: String): String = raw
    .replace('\\', PATH_SEPARATOR)
    .let { value -> if (value.startsWith(ROOT_PATH)) value else "$ROOT_PATH$value" }

private const val ROOT_PATH = "/"
private const val PATH_SEPARATOR = '/'
private const val CURRENT_DIRECTORY = "."
private const val PARENT_DIRECTORY = ".."
private const val ZIP_SUFFIX = ".zip"
