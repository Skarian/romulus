package com.romulus.mobile.realdebrid

import com.romulus.mobile.realdebrid.auth.CredentialVault
import com.romulus.mobile.realdebrid.auth.StoredTokenRecord
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque

internal data class AddMagnetCall(val magnet: String, val host: String)

internal data class SelectFilesCall(val torrentId: String, val fileIdsCsv: String)

internal class MutableClock(
    private var current: Instant = Instant.parse("2026-03-10T00:00:00Z"),
    private val zoneId: ZoneId = ZoneOffset.UTC
) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }
}

internal class FakeAuthClient(
    private val validator: suspend (String) -> Result<Unit> = { Result.success(Unit) }
) : RealDebridAuthClient {
    val validatedCandidates = mutableListOf<String>()

    override suspend fun validateToken(candidate: String): Result<Unit> {
        validatedCandidates += candidate
        return validator(candidate)
    }
}

internal class RecordingCredentialVault(
    private var storedToken: String? = null,
    private val readFailure: Throwable? = null,
    private val writeFailure: Throwable? = null
) : CredentialVault {
    override suspend fun readToken(): StoredTokenRecord? {
        val token = storedToken ?: return null
        return StoredTokenRecord(
            encryptedValue = token.encodeToByteArray(),
            savedAt = Instant.EPOCH
        )
    }

    override suspend fun readPlainToken(): Result<String?> = when {
        readFailure != null -> Result.failure(readFailure)
        else -> Result.success(storedToken)
    }

    override suspend fun writeToken(token: String, savedAt: Instant): Result<Unit> = when {
        writeFailure != null -> Result.failure(writeFailure)
        else -> {
            storedToken = token
            Result.success(Unit)
        }
    }

    override suspend fun clearToken(): Result<Unit> {
        storedToken = null
        return Result.success(Unit)
    }
}

internal class FakeRealDebridApi : RealDebridApi {
    var availableHosts: List<AvailableHostDto> = listOf(
        AvailableHostDto(host = "rd-host")
    )
    val addMagnetCalls = mutableListOf<AddMagnetCall>()
    val selectFilesCalls = mutableListOf<SelectFilesCall>()
    val torrentInfoCalls = mutableListOf<String>()
    val unrestrictCalls = mutableListOf<String>()

    private val addedMagnetResponses = ArrayDeque<AddedMagnetDto>()
    private val torrentInfoResponses = mutableMapOf<String, ArrayDeque<TorrentInfoDto>>()
    private val unrestrictResponses = mutableMapOf<String, Result<UnrestrictedLinkDto>>()

    fun enqueueAddedMagnet(response: AddedMagnetDto) {
        addedMagnetResponses.addLast(response)
    }

    fun enqueueTorrentInfo(torrentId: String, vararg responses: TorrentInfoDto) {
        val queue = torrentInfoResponses.getOrPut(torrentId) { ArrayDeque() }
        responses.forEach(queue::addLast)
    }

    fun respondUnrestrict(link: String, result: Result<UnrestrictedLinkDto>) {
        unrestrictResponses[link] = result
    }

    override suspend fun getAvailableHosts(): List<AvailableHostDto> = availableHosts

    override suspend fun addMagnet(magnet: String, host: String): AddedMagnetDto {
        addMagnetCalls += AddMagnetCall(magnet = magnet, host = host)
        check(addedMagnetResponses.isNotEmpty()) { "No addMagnet response queued" }
        return addedMagnetResponses.removeFirst()
    }

    override suspend fun selectFiles(torrentId: String, fileIdsCsv: String) {
        selectFilesCalls += SelectFilesCall(torrentId = torrentId, fileIdsCsv = fileIdsCsv)
    }

    override suspend fun getTorrentInfo(torrentId: String): TorrentInfoDto {
        torrentInfoCalls += torrentId
        val queue = torrentInfoResponses[torrentId]
            ?: error("No torrent info response queued for $torrentId")
        return if (queue.size > 1) {
            queue.removeFirst()
        } else {
            queue.first()
        }
    }

    override suspend fun unrestrictLink(link: String): UnrestrictedLinkDto {
        unrestrictCalls += link
        return unrestrictResponses[link]?.getOrThrow()
            ?: error("No unrestrict response configured for $link")
    }
}
