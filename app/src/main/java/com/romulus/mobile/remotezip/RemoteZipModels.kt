package com.romulus.mobile.remotezip

import java.nio.file.Path

data class ArchiveEntryIdentity(
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long,
    val normalizedPath: String
)

data class ArchiveEntryDescriptor(
    val identity: ArchiveEntryIdentity,
    val entryPath: String,
    val sizeBytes: Long
)

data class EnumerateRemoteZipRequest(val archiveUrl: String)

data class EnumeratedRemoteZip(val entries: List<ArchiveEntryDescriptor>)

data class CopySelectedEntryRequest(
    val archiveUrl: String,
    val identity: ArchiveEntryIdentity,
    val destination: Path,
    val resumeByteOffset: Long,
    val onProgress: suspend (downloadedBytes: Long) -> Unit
)
