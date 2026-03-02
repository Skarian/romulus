package com.romulus.mobile.domain.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobIgnoreMatcherTest {
    @Test
    fun matchesCaseInsensitiveBasename() {
        val matcher = GlobIgnoreMatcher.from(listOf("*sample*", "*.NFO")).getOrThrow()

        assertTrue(matcher.matches("folder/SAMPLE-video.mkv"))
        assertTrue(matcher.matches("movie.nfo"))
        assertFalse(matcher.matches("movie.mkv"))
    }
}
