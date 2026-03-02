package com.romulus.mobile.domain.files

import com.romulus.mobile.domain.source.RenameRule

object RenameTransformer {
    fun apply(originalPathOrName: String, renameRule: RenameRule?): String {
        if (renameRule == null) return originalPathOrName
        val slashIndex = originalPathOrName.lastIndexOf('/')
        val prefix = if (slashIndex >= 0) originalPathOrName.substring(0, slashIndex + 1) else ""
        val basename = if (slashIndex >= 0) originalPathOrName.substring(slashIndex + 1) else originalPathOrName
        return runCatching {
            val regex = Regex(renameRule.pattern)
            val renamed = basename.replace(regex, renameRule.replacement)
            prefix + renamed
        }.getOrElse { originalPathOrName }
    }
}
