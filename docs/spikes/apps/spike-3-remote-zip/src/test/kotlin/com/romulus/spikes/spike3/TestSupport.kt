package com.romulus.spikes.spike3

import com.romulus.spikes.spike3.model.RangeMode
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

object TestSupport {
    val paths: Spike3Paths = Spike3Paths.discover()

    fun fixtureRoot(): Path {
        paths.requireFixtureRoot()
        return paths.fixtureHttpRoot
    }

    fun archiveUrl(baseUri: URI, mode: RangeMode, fixtureName: String): HttpUrl {
        return "$baseUri${mode.pathSegment}/$fixtureName".toHttpUrl()
    }

    fun fixtureSize(fixtureName: String): Long {
        return Files.size(fixtureRoot().resolve(fixtureName))
    }
}
