package com.romulus.mobile.core.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameCollisionResolverTest {
    private val resolver = FilenameCollisionResolver.Default()

    @Test
    fun appendsNumericSuffixWhenNameCollides() {
        val resolved = resolver.resolve(
            existingNames = setOf("movie.mkv", "movie (1).mkv"),
            candidateName = "movie.mkv"
        )

        assertEquals("movie (2).mkv", resolved)
    }
}
