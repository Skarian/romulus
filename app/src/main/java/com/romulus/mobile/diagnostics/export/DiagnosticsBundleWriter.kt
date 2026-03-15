package com.romulus.mobile.diagnostics.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.romulus.mobile.diagnostics.events.DiagnosticsManifest
import com.romulus.mobile.diagnostics.store.DiagnosticsArtifactSnapshot
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface DiagnosticsExportFilesystem {
    fun openOutput(uri: Uri): OutputStream?
}

internal class AndroidDiagnosticsExportFilesystem(context: Context) : DiagnosticsExportFilesystem {
    private val contentResolver: ContentResolver = context.contentResolver

    override fun openOutput(uri: Uri): OutputStream? = contentResolver.openOutputStream(uri, "w")
}

internal class DiagnosticsBundleWriter(
    private val exportFilesystem: DiagnosticsExportFilesystem,
    private val json: Json
) {
    fun write(
        snapshot: DiagnosticsArtifactSnapshot,
        manifest: DiagnosticsManifest,
        destinationUri: Uri
    ): Result<Unit> = runCatching {
        exportFilesystem.openOutput(destinationUri)?.use { output ->
            writeArchive(output, snapshot, manifest).getOrThrow()
        } ?: error("Diagnostics export destination could not be opened")
    }

    internal fun writeArchive(
        output: OutputStream,
        snapshot: DiagnosticsArtifactSnapshot,
        manifest: DiagnosticsManifest
    ): Result<Unit> = runCatching {
        ZipOutputStream(BufferedOutputStream(output)).use { archive ->
            writeEntry(
                archive = archive,
                name = snapshot.manifest.name,
                bytes = json.encodeToString(manifest).encodeToByteArray()
            )
            writeFile(archive, snapshot.timeline)
            writeFile(archive, snapshot.failures)
            writeFile(archive, snapshot.summary)
        }
    }

    private fun writeFile(archive: ZipOutputStream, file: File) {
        writeEntry(archive, file.name, file.readBytes())
    }

    private fun writeEntry(archive: ZipOutputStream, name: String, bytes: ByteArray) {
        archive.putNextEntry(ZipEntry(name))
        archive.write(bytes)
        archive.closeEntry()
    }
}
