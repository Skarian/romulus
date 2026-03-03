package com.romulus.mobile.domain.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePathContractTest {
    @Test
    fun matchesWhenScopeAndCandidateAreExact() {
        assertTrue(SourcePathContract.matches("/Series/Season 01", "/Series/Season 01"))
    }

    @Test
    fun matchesWhenCandidateIsNestedUnderScope() {
        assertTrue(
            SourcePathContract.matches(
                scopePath = "/Series/Season 01",
                candidatePath = "/Series/Season 01/Episode 01.mkv"
            )
        )
    }

    @Test
    fun doesNotMatchSiblingWithSamePrefix() {
        assertFalse(
            SourcePathContract.matches(
                scopePath = "/Season 1",
                candidatePath = "/Season 10/Episode 01.mkv"
            )
        )
    }

    @Test
    fun rootScopeMatchesAllFiles() {
        assertTrue(SourcePathContract.matches("/", "/Movies/Example.mkv"))
        assertTrue(SourcePathContract.matches("/", "/Series/Season 01/Episode 01.mkv"))
    }

    @Test
    fun normalizesMalformedDoubleSlashes() {
        val normalization = SourcePathContract.normalizeConfiguredPath("/Series//Season 01//")
        val normalizedPath = (normalization as ConfiguredPathNormalization.Valid).path

        assertEquals("/Series/Season 01", normalizedPath)
        assertTrue(
            SourcePathContract.matches(
                scopePath = "/Series//Season 01//",
                candidatePath = "//Series/Season 01///Episode 01.mkv"
            )
        )
    }
}
