package com.romulus.mobile.data.files

import com.romulus.mobile.core.time.ClockProvider
import com.romulus.mobile.data.realdebrid.AddedMagnetDto
import com.romulus.mobile.data.realdebrid.RealDebridApi
import com.romulus.mobile.data.realdebrid.RealDebridClient
import com.romulus.mobile.data.realdebrid.RealDebridUserDto
import com.romulus.mobile.data.realdebrid.TorrentFileDto
import com.romulus.mobile.data.realdebrid.TorrentInfoDto
import com.romulus.mobile.data.realdebrid.TorrentSummaryDto
import com.romulus.mobile.data.realdebrid.UnrestrictedLinkDto
import com.romulus.mobile.domain.source.RenameRule
import com.romulus.mobile.domain.source.SourceEntry
import com.romulus.mobile.domain.source.SourceTorrent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSelectionRepositoryTest {
    @Test
    fun resolvesOnlyFilesInsideConfiguredScope() = runBlocking {
        val repository = repositoryWith(
            filesByMagnet = mapOf(
                SHOW_MAGNET to listOf(
                    TorrentFileDto(id = 1, path = "/Show/Season 01/Episode 01.mkv", bytes = 1_000),
                    TorrentFileDto(id = 2, path = "/Show/Season 01/Subtitles/Episode 01.srt", bytes = 50),
                    TorrentFileDto(id = 3, path = "/Show/Season 02/Episode 01.mkv", bytes = 900),
                    TorrentFileDto(id = 4, path = "/Movies/Clip.mkv", bytes = 700)
                )
            )
        )
        val entry = sourceEntry(path = "/Show/Season 01")

        val resolved = repository.resolveFiles("snapshot-1", entry, "api-key").getOrThrow()

        assertEquals(2, resolved.size)
        assertEquals(listOf("Episode 01.mkv", "Episode 01.srt"), resolved.map { it.originalName })
    }

    @Test
    fun appliesIgnoreRulesAfterScopeFiltering() = runBlocking {
        val repository = repositoryWith(
            filesByMagnet = mapOf(
                SHOW_MAGNET to listOf(
                    TorrentFileDto(id = 1, path = "/Show/Season 01/Episode 01.mkv", bytes = 1_000),
                    TorrentFileDto(id = 2, path = "/Show/Season 01/Episode 01.srt", bytes = 50),
                    TorrentFileDto(id = 3, path = "/Show/Season 02/Episode 01.srt", bytes = 50)
                )
            )
        )
        val entry = sourceEntry(
            path = "/Show/Season 01",
            ignoreGlobs = listOf("*.srt")
        )

        val resolved = repository.resolveFiles("snapshot-1", entry, "api-key").getOrThrow()

        assertEquals(1, resolved.size)
        assertEquals("Episode 01.mkv", resolved.first().originalName)
    }

    @Test
    fun keepsRenameOutputForScopedFiles() = runBlocking {
        val repository = repositoryWith(
            filesByMagnet = mapOf(
                SHOW_MAGNET to listOf(
                    TorrentFileDto(id = 1, path = "/Show/Season 01/Episode 01.mkv", bytes = 1_000),
                    TorrentFileDto(id = 2, path = "/Show/Season 02/Episode 02.mkv", bytes = 1_000)
                )
            )
        )
        val entry = sourceEntry(
            path = "/Show/Season 01",
            rename = RenameRule("^(.+)\\.mkv$", "$1 - Renamed.mkv")
        )

        val resolved = repository.resolveFiles("snapshot-1", entry, "api-key").getOrThrow()

        assertEquals(1, resolved.size)
        assertEquals("Episode 01 - Renamed.mkv", resolved.first().defaultDisplayName)
    }

    @Test
    fun returnsEmptyListWhenScopeHasNoMatches() = runBlocking {
        val repository = repositoryWith(
            filesByMagnet = mapOf(
                SHOW_MAGNET to listOf(
                    TorrentFileDto(id = 1, path = "/Movies/Example.mkv", bytes = 1_000)
                )
            )
        )
        val entry = sourceEntry(path = "/Show/Season 01")

        val resolved = repository.resolveFiles("snapshot-1", entry, "api-key").getOrThrow()

        assertTrue(resolved.isEmpty())
    }

    private fun repositoryWith(filesByMagnet: Map<String, List<TorrentFileDto>>): FileSelectionRepository {
        val api = FakeRealDebridApi(filesByMagnet)
        val client = RealDebridClient(api = api, clockProvider = FixedClockProvider())
        return FileSelectionRepository(realDebridClient = client)
    }

    private fun sourceEntry(
        path: String,
        ignoreGlobs: List<String> = emptyList(),
        rename: RenameRule? = null
    ): SourceEntry {
        return SourceEntry(
            index = 0,
            displayName = "Show Pack",
            subfolder = "shows/show-pack",
            path = path,
            torrents = listOf(SourceTorrent(partIndex = 0, url = SHOW_MAGNET, partName = null)),
            rename = rename,
            ignoreGlobs = ignoreGlobs
        )
    }

    private class FixedClockProvider : ClockProvider {
        override fun nowEpochMillis(): Long = 1_000L
    }

    private class FakeRealDebridApi(
        private val filesByMagnet: Map<String, List<TorrentFileDto>>
    ) : RealDebridApi {
        private val torrentIdsByMagnet = linkedMapOf<String, String>()
        private val filesByTorrentId = linkedMapOf<String, List<TorrentFileDto>>()

        override suspend fun getUser(authHeader: String): RealDebridUserDto {
            return RealDebridUserDto(id = 1, username = "tester")
        }

        override suspend fun addMagnet(authHeader: String, magnet: String): AddedMagnetDto {
            val torrentId = torrentIdsByMagnet.getOrPut(magnet) {
                "torrent-${torrentIdsByMagnet.size + 1}"
            }
            filesByTorrentId[torrentId] = filesByMagnet[magnet].orEmpty()
            return AddedMagnetDto(id = torrentId)
        }

        override suspend fun selectFiles(authHeader: String, torrentId: String, fileIdsCsv: String) = Unit

        override suspend fun getTorrentInfo(authHeader: String, torrentId: String): TorrentInfoDto {
            return TorrentInfoDto(
                id = torrentId,
                status = "downloaded",
                files = filesByTorrentId[torrentId].orEmpty(),
                links = emptyList()
            )
        }

        override suspend fun listTorrents(authHeader: String, page: Int?, limit: Int?): List<TorrentSummaryDto> {
            return emptyList()
        }

        override suspend fun unrestrictLink(authHeader: String, link: String): UnrestrictedLinkDto {
            return UnrestrictedLinkDto(downloadUrl = link)
        }
    }

    companion object {
        private const val SHOW_MAGNET = "magnet:?dn=show-pack"
    }
}
