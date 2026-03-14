package com.romulus.mobile.source.ingest

import com.romulus.mobile.source.snapshot.SourcePathScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePathRulesTest {
    @Test
    fun omittedScopeDefaultsToShallowRoot() {
        assertEquals(SourcePathScope("/", false), normalizeScope(null))
    }

    @Test
    fun shallowRootMatchesOnlyTopLevelFiles() {
        val scope = SourcePathScope("/", false)

        assertTrue("/Episode.mkv".isWithinScope(scope))
        assertFalse("/Shows/Episode.mkv".isWithinScope(scope))
    }

    @Test
    fun nestedRootMatchesDescendants() {
        val scope = SourcePathScope("/", true)

        assertTrue("/Shows/Episode.mkv".isWithinScope(scope))
    }

    @Test
    fun shallowDirectoryMatchesOnlyDirectChildren() {
        val scope = SourcePathScope("/Shows/", false)

        assertTrue("/Shows/Episode.mkv".isWithinScope(scope))
        assertFalse("/Shows/Season 1/Episode.mkv".isWithinScope(scope))
        assertFalse("/Movies/Episode.mkv".isWithinScope(scope))
    }

    @Test
    fun nestedDirectoryMatchesDescendants() {
        val scope = SourcePathScope("/Shows/", true)

        assertTrue("/Shows/Season 1/Episode.mkv".isWithinScope(scope))
        assertFalse("/Movies/Episode.mkv".isWithinScope(scope))
    }

    @Test
    fun exactZipScopeMatchesOnlyExactPathAndRejectsNestedFlag() {
        val scope = SourcePathScope("/Shows/archive.zip", false)

        assertTrue("/Shows/archive.zip".isWithinScope(scope))
        assertFalse("/Shows/archive.zip/inside.bin".isWithinScope(scope))
        assertFalse(isScopeSemanticallyValid(SourcePathScope("/Shows/archive.zip", true)))
    }
}
