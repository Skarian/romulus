package com.romulus.mobile.diagnostics

import android.net.Uri
import com.romulus.mobile.diagnostics.events.DiagnosticsManifest
import com.romulus.mobile.diagnostics.export.DiagnosticsBundleWriter
import com.romulus.mobile.diagnostics.export.DiagnosticsExportFilesystem
import com.romulus.mobile.diagnostics.store.DiagnosticsArtifactSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsBundleWriterTest {
    @Test
    fun writesManifestAndArtifactsIntoZipBundle() = runTest {
        val output = ByteArrayOutputStream()
        val writer = DiagnosticsBundleWriter(
            exportFilesystem = object : DiagnosticsExportFilesystem {
                override fun openOutput(uri: Uri) = output
            },
            json = Json { encodeDefaults = true }
        )
        val tempRoot = Files.createTempDirectory("diagnostics-bundle-test").toFile()
        val snapshot = DiagnosticsArtifactSnapshot(
            manifest = tempRoot.resolve("manifest.json").apply { writeText("{}") },
            timeline = tempRoot.resolve("timeline.jsonl").apply { writeText("timeline\n") },
            failures = tempRoot.resolve("failures.jsonl").apply { writeText("failure\n") },
            summary = tempRoot.resolve("summary.json").apply { writeText("{}") },
            totalBytes = 0L
        )

        val result = writer.writeArchive(
            output = output,
            snapshot = snapshot,
            manifest = DiagnosticsManifest(
                contractVersion = 1,
                appVersion = "1.0",
                buildNumber = "1",
                androidVersion = "16",
                deviceModel = "device",
                sessionSeed = "session",
                redactionPolicyVersion = 1,
                exportedAt = Instant.EPOCH
            )
        )

        assertTrue(result.isSuccess)
        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                entryNames += entry.name
                archive.closeEntry()
            }
        }
        assertEquals(
            listOf("manifest.json", "timeline.jsonl", "failures.jsonl", "summary.json"),
            entryNames
        )
        assertTrue(tempRoot.deleteRecursively())
    }
}
