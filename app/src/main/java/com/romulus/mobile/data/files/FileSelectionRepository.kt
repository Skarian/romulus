package com.romulus.mobile.data.files

import com.romulus.mobile.data.realdebrid.RealDebridClient
import com.romulus.mobile.domain.files.FileOption
import com.romulus.mobile.domain.files.GlobIgnoreMatcher
import com.romulus.mobile.domain.files.RenameTransformer
import com.romulus.mobile.domain.source.SourceEntry

class FileSelectionRepository(
    private val realDebridClient: RealDebridClient
) {
    private var activeSnapshotId: String? = null
    private val cacheByEntryKey = linkedMapOf<String, List<FileOption>>()

    suspend fun resolveFiles(snapshotId: String, entry: SourceEntry, apiKey: String): Result<List<FileOption>> {
        if (activeSnapshotId != snapshotId) {
            cacheByEntryKey.clear()
            activeSnapshotId = snapshotId
        }
        val entryKey = "$snapshotId:${entry.index}"
        cacheByEntryKey[entryKey]?.let { cached ->
            return Result.success(cached)
        }

        return runCatching {
            val ignoreMatcher = GlobIgnoreMatcher.from(entry.ignoreGlobs).getOrElse {
                return@runCatching emptyList()
            }
            val resolved = mutableListOf<FileOption>()
            entry.torrents.forEachIndexed { partIndex, torrent ->
                val partName = torrent.partName ?: "Part ${partIndex + 1}"
                val torrentFiles = realDebridClient.resolveTorrentFiles(apiKey, torrent.url)
                torrentFiles.files.forEach { file ->
                    if (ignoreMatcher.matches(file.path)) return@forEach
                    val originalName = file.path.substringAfterLast('/').substringAfterLast('\\')
                    val displayName = RenameTransformer.apply(originalName, entry.rename)
                    resolved += FileOption(
                        partIndex = partIndex,
                        partName = partName,
                        magnetUrl = torrent.url,
                        torrentFileId = file.id,
                        originalName = originalName,
                        sizeBytes = file.bytes,
                        defaultDisplayName = displayName
                    )
                }
            }
            cacheByEntryKey[entryKey] = resolved
            resolved
        }
    }
}
