package com.romulus.spikes.spike3.zip

import com.romulus.spikes.spike3.TestSupport
import com.romulus.spikes.spike3.errors.FailureCodes
import com.romulus.spikes.spike3.errors.Spike3FailureException
import com.romulus.spikes.spike3.filter.IgnoreGlobFilter
import com.romulus.spikes.spike3.http.HttpTraceRecorder
import com.romulus.spikes.spike3.http.OkHttpRangeClient
import com.romulus.spikes.spike3.model.EntryIdentity
import com.romulus.spikes.spike3.model.RangeMode
import com.romulus.spikes.spike3.model.Spike3CaseDefinitions
import com.romulus.spikes.spike3.server.FixtureHttpServer
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteZipSessionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun enumeratesRegularFixtureMetadataFirst() {
        FixtureHttpServer(TestSupport.fixtureRoot(), 0).use { server ->
            val baseUri = server.start()
            val client = OkHttpRangeClient(recorder = HttpTraceRecorder())
            val archiveUrl = TestSupport.archiveUrl(baseUri, RangeMode.FULL, "regular.zip")

            RemoteZipSession("regular-enumeration", "regular.zip", archiveUrl, client).use { session ->
                val entries = session.enumerate()
                val visiblePaths = IgnoreGlobFilter.apply(entries, emptyList(), emptyList())
                    .visibleEntries
                    .map { it.identity.entryPath }
                assertEquals(listOf("a/b/c/d.txt", "baz.txt", "foo/bar.txt"), visiblePaths)
                assertEquals(4, entries.count { it.isDirectory })
            }
        }
    }

    @Test
    fun copiesOnlySelectedDuplicateEntries() {
        val duplicateCase = Spike3CaseDefinitions.defaultCases().single { it.id == "run-d-selected-only-duplicate" }
        FixtureHttpServer(TestSupport.fixtureRoot(), 0).use { server ->
            val baseUri = server.start()
            val client = OkHttpRangeClient(recorder = HttpTraceRecorder())
            val archiveUrl = TestSupport.archiveUrl(baseUri, RangeMode.FULL, duplicateCase.fixtureName)

            RemoteZipSession(duplicateCase.id, duplicateCase.fixtureName, archiveUrl, client).use { session ->
                val entries = session.enumerate()
                val filtered = IgnoreGlobFilter.apply(entries, duplicateCase.ignoreGlobs, duplicateCase.selectedEntries)
                val downloads = session.copySelected(duplicateCase.selectedEntries, tempDir)
                assertEquals(
                    listOf("0000000315__sample_text_large.txt", "0000005450__sample_text1.txt"),
                    downloads.map { it.fileName.toString() },
                )
                assertTrue(filtered.visibleEntries.any { it.identity.localHeaderOffset == 0L })
                assertFalse(downloads.any { it.fileName.toString() == "0000000000__sample_text1.txt" })
            }
        }
    }

    @Test
    fun failsExplicitlyForMalformedAndEncryptedFixtures() {
        FixtureHttpServer(TestSupport.fixtureRoot(), 0).use { server ->
            val baseUri = server.start()
            val client = OkHttpRangeClient(recorder = HttpTraceRecorder())
            val malformedUrl = TestSupport.archiveUrl(baseUri, RangeMode.FULL, "malformed.zip")
            val malformedFailure = assertFailsWith<Spike3FailureException> {
                RemoteZipSession("malformed", "malformed.zip", malformedUrl, client).use { session ->
                    session.enumerate()
                }
            }
            assertEquals(FailureCodes.INVALID_ZIP, malformedFailure.errorCode)

            val encryptedSelection = listOf(
                EntryIdentity(
                    fixtureName = "strong_encrypted.zip",
                    entryPath = "test.txt",
                    localHeaderOffset = 0,
                    compressedSize = 326,
                    uncompressedSize = 6,
                    crc32 = 0x0972d361,
                ),
            )
            val encryptedUrl = TestSupport.archiveUrl(baseUri, RangeMode.FULL, "strong_encrypted.zip")
            RemoteZipSession("encrypted", "strong_encrypted.zip", encryptedUrl, client).use { session ->
                session.enumerate()
                val encryptedFailure = assertFailsWith<Spike3FailureException> {
                    session.copySelected(encryptedSelection, tempDir.resolve("encrypted"))
                }
                assertEquals(FailureCodes.UNSUPPORTED_ENCRYPTION, encryptedFailure.errorCode)
            }
        }
    }
}
