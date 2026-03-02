package com.romulus.mobile.domain.files

import com.romulus.mobile.domain.source.RenameRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RenameTransformerTest {
    @Test
    fun appliesRegexToBasenameOnly() {
        val renamed = RenameTransformer.apply(
            originalPathOrName = "folder/movie.iso",
            renameRule = RenameRule("^(.*)\\.iso$", "$1-verified.iso")
        )

        assertEquals("folder/movie-verified.iso", renamed)
    }

    @Test
    fun leavesNameWhenRuleDoesNotMatch() {
        val renamed = RenameTransformer.apply(
            originalPathOrName = "movie.mkv",
            renameRule = RenameRule("^(.*)\\.iso$", "$1-verified.iso")
        )

        assertEquals("movie.mkv", renamed)
    }
}
