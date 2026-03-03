package com.romulus.mobile.data.realdebrid

import com.romulus.mobile.core.time.ClockProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDebridClientTest {
    @Test
    fun prefersTorrentFileIdWhenDuplicateNameAndSizeExist() = runBlocking {
        val api = FakeRealDebridApi(
            files = listOf(
                TorrentFileDto(id = 1, path = "/A/Episode.mkv", bytes = 1_000),
                TorrentFileDto(id = 2, path = "/B/Episode.mkv", bytes = 1_000)
            )
        )
        val client = RealDebridClient(
            api = api,
            clockProvider = FixedClockProvider()
        )

        val result = client.resolveFreshUnrestrictedLink(
            apiKey = "key",
            magnetUrl = "magnet:?dn=test",
            originalFilename = "Episode.mkv",
            sizeBytes = 1_000,
            torrentFileId = 2
        )

        assertEquals("2", api.lastSelectedCsv)
        assertEquals("https://host/2/download", result.downloadUrl)
    }

    @Test
    fun prefersTorrentFileIdOnHashReusePathWhenInfoHashPresent() = runBlocking {
        val api = FakeRealDebridApi(
            files = listOf(
                TorrentFileDto(id = 1, path = "/A/Episode.mkv", bytes = 1_000),
                TorrentFileDto(id = 2, path = "/B/Episode.mkv", bytes = 1_000)
            ),
            initiallySelectedIds = setOf(2)
        )
        val client = RealDebridClient(
            api = api,
            clockProvider = FixedClockProvider()
        )

        val result = client.resolveFreshUnrestrictedLink(
            apiKey = "key",
            magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=test",
            originalFilename = "Episode.mkv",
            sizeBytes = 1_000,
            torrentFileId = 2
        )

        assertTrue(api.listTorrentsCallCount > 0)
        assertEquals("https://host/2", api.lastUnrestrictLink)
        assertEquals("https://host/2/download", result.downloadUrl)
    }

    @Test
    fun fallsBackToNameAndSizeWhenTorrentFileIdMissing() = runBlocking {
        val api = FakeRealDebridApi(
            files = listOf(
                TorrentFileDto(id = 5, path = "/A/Unique.mkv", bytes = 500)
            )
        )
        val client = RealDebridClient(
            api = api,
            clockProvider = FixedClockProvider()
        )

        val result = client.resolveFreshUnrestrictedLink(
            apiKey = "key",
            magnetUrl = "magnet:?dn=test",
            originalFilename = "Unique.mkv",
            sizeBytes = 500,
            torrentFileId = 999
        )

        assertEquals("5", api.lastSelectedCsv)
        assertEquals("https://host/5/download", result.downloadUrl)
    }

    @Test
    fun keepsExistingFailureWhenNoUniqueMatchAndNoPreferredId() = runBlocking {
        val api = FakeRealDebridApi(
            files = listOf(
                TorrentFileDto(id = 1, path = "/A/Episode.mkv", bytes = 1_000),
                TorrentFileDto(id = 2, path = "/B/Episode.mkv", bytes = 1_000)
            )
        )
        val client = RealDebridClient(
            api = api,
            clockProvider = FixedClockProvider()
        )

        val throwable = runCatching {
            client.resolveFreshUnrestrictedLink(
                apiKey = "key",
                magnetUrl = "magnet:?dn=test",
                originalFilename = "Episode.mkv",
                sizeBytes = 1_000,
                torrentFileId = null
            )
        }.exceptionOrNull()

        assertTrue(throwable is FileRematchException)
    }

    private class FixedClockProvider : ClockProvider {
        override fun nowEpochMillis(): Long = 1_000
    }

    private class FakeRealDebridApi(
        private val files: List<TorrentFileDto>,
        initiallySelectedIds: Set<Int> = emptySet()
    ) : RealDebridApi {
        var lastSelectedCsv: String? = null
        var lastUnrestrictLink: String? = null
        var listTorrentsCallCount: Int = 0
        private var selectedIds: Set<Int> = initiallySelectedIds

        override suspend fun getUser(authHeader: String): RealDebridUserDto {
            return RealDebridUserDto(id = 1, username = "test")
        }

        override suspend fun addMagnet(authHeader: String, magnet: String): AddedMagnetDto {
            return AddedMagnetDto(id = TORRENT_ID)
        }

        override suspend fun selectFiles(authHeader: String, torrentId: String, fileIdsCsv: String) {
            lastSelectedCsv = fileIdsCsv
            selectedIds = fileIdsCsv
                .split(",")
                .mapNotNull { value -> value.trim().toIntOrNull() }
                .toSet()
        }

        override suspend fun getTorrentInfo(authHeader: String, torrentId: String): TorrentInfoDto {
            return TorrentInfoDto(
                id = torrentId,
                status = "downloaded",
                files = files.map { file ->
                    if (selectedIds.contains(file.id)) {
                        file.copy(selected = 1, unrestricted = "https://host/${file.id}")
                    } else {
                        file.copy(selected = 0, unrestricted = null, link = null)
                    }
                },
                links = emptyList()
            )
        }

        override suspend fun listTorrents(
            authHeader: String,
            page: Int?,
            limit: Int?
        ): List<TorrentSummaryDto> {
            listTorrentsCallCount += 1
            return emptyList()
        }

        override suspend fun unrestrictLink(authHeader: String, link: String): UnrestrictedLinkDto {
            lastUnrestrictLink = link
            return UnrestrictedLinkDto(downloadUrl = "$link/download")
        }
    }

    companion object {
        private const val TORRENT_ID = "torrent-1"
    }
}
