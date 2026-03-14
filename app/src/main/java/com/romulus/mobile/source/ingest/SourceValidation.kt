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

            entry.unarchive?.layout?.rename?.let { renameRule ->
                val validationIssue = validateRenameRule(
                    renameRule = renameRule,
                    missingMessage = DEDICATED_FOLDER_RENAME_REQUIRED_MESSAGE,
                    invalidPatternPrefix = DEDICATED_FOLDER_RENAME_INVALID_PREFIX
                )
                if (validationIssue != null) {
                    issues += validationIssue
                }
            }
        }

        return issues
    }

    private fun validateRenameRule(
        renameRule: RenameRule,
        missingMessage: String = "Rename rule requires both pattern and replacement.",
        invalidPatternPrefix: String = "Rename pattern is invalid"
    ): SourceValidationIssue? {
        if (renameRule.pattern.trim().isBlank() || renameRule.replacement.trim().isBlank()) {
            return SourceValidationIssue.InvalidRenameRule(
                missingMessage
            )
        }
        return runCatching {
            Regex(renameRule.pattern)
        }.fold(
            onSuccess = { null },
            onFailure = { error ->
                SourceValidationIssue.InvalidRenameRule(
                    error.message?.let { "$invalidPatternPrefix: $it" }
                        ?: "$invalidPatternPrefix."
                )
            }
        )
    }

    private companion object {
        const val SUPPORTED_VERSION = 1
        const val DEDICATED_FOLDER_RENAME_REQUIRED_MESSAGE =
            "Dedicated-folder rename rule requires both pattern and replacement."
        const val DEDICATED_FOLDER_RENAME_INVALID_PREFIX =
            "Dedicated-folder rename pattern is invalid"
    }
}
