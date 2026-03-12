package com.romulus.mobile.source.ingest

internal class SourceValidation {
    fun validate(document: SourceDocument): List<SourceValidationIssue> {
        val issues = mutableListOf<SourceValidationIssue>()
        if (document.version != SUPPORTED_VERSION) {
            issues += SourceValidationIssue.InvalidVersion(document.version)
        }

        document.entries.forEach { entry ->
            if (entry.subfolder.trim().isBlank()) {
                issues += SourceValidationIssue.InvalidSubfolder(entry.subfolder)
            }

            val normalizedPath = normalizePath(entry.path)
            if (normalizedPath == null) {
                issues += SourceValidationIssue.InvalidPath(entry.path.orEmpty())
            }

            entry.ignore?.glob.orEmpty().forEach { pattern ->
                if (!isValidIgnoreRule(pattern)) {
                    issues += SourceValidationIssue.InvalidIgnoreRule(pattern)
                }
            }

            entry.rename?.let { renameRule ->
                val validationIssue = validateRenameRule(renameRule)
                if (validationIssue != null) {
                    issues += validationIssue
                }
            }

            if (entry.recursiveUnarchive != null && entry.unarchive != true) {
                issues += SourceValidationIssue.InvalidRecursiveUnarchive(
                    "Recursive unarchive requires unarchive=true."
                )
            }
        }

        return issues
    }

    private fun validateRenameRule(renameRule: RenameRule): SourceValidationIssue? {
        if (renameRule.pattern.trim().isBlank() || renameRule.replacement.trim().isBlank()) {
            return SourceValidationIssue.InvalidRenameRule(
                "Rename rule requires both pattern and replacement."
            )
        }
        return runCatching {
            Regex(renameRule.pattern)
        }.fold(
            onSuccess = { null },
            onFailure = { error ->
                SourceValidationIssue.InvalidRenameRule(
                    error.message ?: "Rename pattern is invalid."
                )
            }
        )
    }

    private companion object {
        const val SUPPORTED_VERSION = 1
    }
}
