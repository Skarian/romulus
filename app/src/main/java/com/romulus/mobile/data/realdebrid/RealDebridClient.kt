package com.romulus.mobile.data.realdebrid

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.romulus.mobile.core.time.ClockProvider
import com.romulus.mobile.core.validation.ValidationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale

class RealDebridClient(
    private val api: RealDebridApi,
    private val clockProvider: ClockProvider,
    private val requestBudgetPerMinute: Int = REQUEST_BUDGET_PER_MINUTE
) {

    private val requestGate = RequestGate(clockProvider, requestBudgetPerMinute)
    private val sessionMutex = Mutex()
    private val cachedSessionsByHash = mutableMapOf<String, CachedTorrentSession>()
    private val inFlightSessionsByHash = mutableMapOf<String, CompletableDeferred<TorrentSession>>()

    suspend fun validateApiKey(apiKey: String): ValidationResult {
        return runCatching {
            withRateLimit {
                api.getUser(bearer(apiKey))
            }
            ValidationResult.Valid
        }.getOrElse { throwable ->
            val message = when (throwable) {
                is HttpException -> when (throwable.code()) {
                    401, 403 -> "Invalid API key"
                    else -> "API validation failed (${throwable.code()})"
                }

                is IOException -> "Network error while validating API key"
                else -> throwable.message ?: "API validation failed"
            }
            ValidationResult.Invalid(message)
        }
    }

    suspend fun resolveTorrentFiles(apiKey: String, magnetUrl: String): ResolvedTorrentFiles {
        val authHeader = bearer(apiKey)
        val infoHash = extractInfoHash(magnetUrl)
        val torrentId = if (infoHash != null) {
            val existing = findAccountTorrentByHash(authHeader, infoHash)
            existing?.id ?: withRateLimit { api.addMagnet(authHeader, magnetUrl) }.id
        } else {
            withRateLimit { api.addMagnet(authHeader, magnetUrl) }.id
        }
        val info = getTorrentInfoWithRetry(authHeader, torrentId)
        return ResolvedTorrentFiles(
            torrentId = info.id,
            files = info.files
        )
    }

    suspend fun resolveFreshUnrestrictedLink(
        apiKey: String,
        magnetUrl: String,
        originalFilename: String,
        sizeBytes: Long,
        torrentFileId: Int? = null
    ): UnrestrictedLinkDto {
        val infoHash = extractInfoHash(magnetUrl)
            ?: return resolveFreshUnrestrictedLinkLegacy(
                apiKey = apiKey,
                magnetUrl = magnetUrl,
                originalFilename = originalFilename,
                sizeBytes = sizeBytes,
                torrentFileId = torrentFileId
            )
        return runCatching {
            resolveFreshUnrestrictedLinkHashReuseStyle(
                apiKey = apiKey,
                magnetUrl = magnetUrl,
                infoHash = infoHash,
                originalFilename = originalFilename,
                sizeBytes = sizeBytes,
                torrentFileId = torrentFileId
            )
        }.getOrElse { primaryFailure ->
            if (isAuthError(primaryFailure)) {
                throw primaryFailure
            }
            invalidateSession(infoHash)
            resolveFreshUnrestrictedLinkLegacy(
                apiKey = apiKey,
                magnetUrl = magnetUrl,
                originalFilename = originalFilename,
                sizeBytes = sizeBytes,
                torrentFileId = torrentFileId
            )
        }
    }

    private fun rematchFileId(
        files: List<TorrentFileDto>,
        originalFilename: String,
        sizeBytes: Long,
        preferredFileId: Int? = null
    ): Int {
        preferredFileId?.let { preferredId ->
            files.firstOrNull { file -> file.id == preferredId }?.let { matched ->
                return matched.id
            }
        }
        val expectedName = originalFilename.trim().lowercase()
        val matches = files.filter { file ->
            file.bytes == sizeBytes && basename(file.path).lowercase() == expectedName
        }
        return when (matches.size) {
            1 -> matches.first().id
            0 -> throw FileRematchException("Could not rematch selected file by name and size")
            else -> throw FileRematchException("Multiple files matched selected name and size")
        }
    }

    private fun basename(path: String): String {
        return path.substringAfterLast('/').substringAfterLast('\\')
    }

    private suspend fun resolveFreshUnrestrictedLinkHashReuseStyle(
        apiKey: String,
        magnetUrl: String,
        infoHash: String,
        originalFilename: String,
        sizeBytes: Long,
        torrentFileId: Int?
    ): UnrestrictedLinkDto {
        val authHeader = bearer(apiKey)
        val session = getOrPrepareSession(
            authHeader = authHeader,
            magnetUrl = magnetUrl,
            infoHash = infoHash
        )
        val deadline = clockProvider.nowEpochMillis() + READY_TIMEOUT_MS
        var info = getTorrentInfoWithRetry(authHeader, session.torrentId)
        info = ensureTorrentPrepared(
            authHeader = authHeader,
            torrentId = session.torrentId,
            initialInfo = info,
            deadlineMs = deadline
        )

        val fileId = rematchFileId(
            files = info.files,
            originalFilename = originalFilename,
            sizeBytes = sizeBytes,
            preferredFileId = torrentFileId
        )
        if (!isFileSelected(info.files, fileId)) {
            val selected = info.files
                .filter { it.selected == 1 }
                .map { it.id }
                .toMutableSet()
            selected += fileId
            withRateLimit {
                api.selectFiles(
                    authHeader = authHeader,
                    torrentId = session.torrentId,
                    fileIdsCsv = selected.sorted().joinToString(",")
                )
            }
            info = waitForStatus(
                authHeader = authHeader,
                torrentId = session.torrentId,
                targetStatus = STATUS_DOWNLOADED,
                deadlineMs = deadline
            )
        }

        if (!hasAnyHostLink(info)) {
            info = waitForHostLinks(
                authHeader = authHeader,
                torrentId = session.torrentId,
                deadlineMs = deadline,
                initialInfo = info
            )
        }

        val hostLink = resolveHostLinkForFile(
            info = info,
            fileId = fileId
        ) ?: throw TorrentNotReadyException()

        return withRateLimit {
            api.unrestrictLink(authHeader, hostLink)
        }
    }

    private suspend fun resolveFreshUnrestrictedLinkLegacy(
        apiKey: String,
        magnetUrl: String,
        originalFilename: String,
        sizeBytes: Long,
        torrentFileId: Int?
    ): UnrestrictedLinkDto {
        val authHeader = bearer(apiKey)
        val added = withRateLimit {
            api.addMagnet(authHeader, magnetUrl)
        }
        val initialInfo = getTorrentInfoWithRetry(authHeader, added.id)
        val fileId = rematchFileId(
            files = initialInfo.files,
            originalFilename = originalFilename,
            sizeBytes = sizeBytes,
            preferredFileId = torrentFileId
        )
        withRateLimit {
            api.selectFiles(
                authHeader = authHeader,
                torrentId = added.id,
                fileIdsCsv = fileId.toString()
            )
        }

        val readinessDeadlineMs = clockProvider.nowEpochMillis() + READY_TIMEOUT_MS
        var pollCount = 0
        while (clockProvider.nowEpochMillis() < readinessDeadlineMs) {
            val info = withRateLimit {
                api.getTorrentInfo(authHeader, added.id)
            }
            val selectedFile = info.files.firstOrNull { it.id == fileId }
            val hostLink = selectedFile?.unrestricted
                ?.takeIf { it.isNotBlank() }
                ?: selectedFile?.link?.takeIf { it.isNotBlank() }
                ?: info.links.firstOrNull { it.isNotBlank() }

            if (!hostLink.isNullOrBlank()) {
                return withRateLimit {
                    api.unrestrictLink(authHeader, hostLink)
                }
            }

            delay(pollDelayMs(pollCount))
            pollCount += 1
        }
        throw TorrentNotReadyException()
    }

    private suspend fun getOrPrepareSession(
        authHeader: String,
        magnetUrl: String,
        infoHash: String
    ): TorrentSession {
        val normalizedHash = infoHash.lowercase()
        val leaderDeferred = CompletableDeferred<TorrentSession>()
        var cached: TorrentSession? = null
        var waitingDeferred: CompletableDeferred<TorrentSession>? = null
        var isLeader = false

        sessionMutex.withLock {
            val now = clockProvider.nowEpochMillis()
            val existingCached = cachedSessionsByHash[normalizedHash]
            if (existingCached != null && existingCached.expiresAtEpochMs > now) {
                cached = existingCached.session
                return@withLock
            }
            if (existingCached != null) {
                cachedSessionsByHash.remove(normalizedHash)
            }
            val inFlight = inFlightSessionsByHash[normalizedHash]
            if (inFlight != null) {
                waitingDeferred = inFlight
            } else {
                inFlightSessionsByHash[normalizedHash] = leaderDeferred
                isLeader = true
            }
        }

        if (cached != null) {
            return cached!!
        }
        if (!isLeader) {
            return waitingDeferred!!.await()
        }

        try {
            val prepared = prepareSession(
                authHeader = authHeader,
                magnetUrl = magnetUrl,
                infoHash = normalizedHash
            )
            sessionMutex.withLock {
                cachedSessionsByHash[normalizedHash] = CachedTorrentSession(
                    session = prepared,
                    expiresAtEpochMs = clockProvider.nowEpochMillis() + SESSION_CACHE_TTL_MS
                )
                inFlightSessionsByHash.remove(normalizedHash)
            }
            leaderDeferred.complete(prepared)
            return prepared
        } catch (throwable: Throwable) {
            sessionMutex.withLock {
                if (inFlightSessionsByHash[normalizedHash] === leaderDeferred) {
                    inFlightSessionsByHash.remove(normalizedHash)
                }
                cachedSessionsByHash.remove(normalizedHash)
            }
            leaderDeferred.completeExceptionally(throwable)
            throw throwable
        }
    }

    private suspend fun prepareSession(
        authHeader: String,
        magnetUrl: String,
        infoHash: String
    ): TorrentSession {
        val existing = findAccountTorrentByHash(authHeader, infoHash)
        val torrentId = existing?.id ?: withRateLimit {
            api.addMagnet(authHeader, magnetUrl)
        }.id
        val deadline = clockProvider.nowEpochMillis() + READY_TIMEOUT_MS
        var info = getTorrentInfoWithRetry(authHeader, torrentId)
        info = ensureTorrentPrepared(
            authHeader = authHeader,
            torrentId = torrentId,
            initialInfo = info,
            deadlineMs = deadline
        )
        if (!hasAnyHostLink(info)) {
            info = waitForHostLinks(
                authHeader = authHeader,
                torrentId = torrentId,
                deadlineMs = deadline,
                initialInfo = info
            )
        }
        return TorrentSession(
            infoHash = infoHash,
            torrentId = torrentId
        )
    }

    private suspend fun ensureTorrentPrepared(
        authHeader: String,
        torrentId: String,
        initialInfo: TorrentInfoDto,
        deadlineMs: Long
    ): TorrentInfoDto {
        var info = initialInfo
        val status = normalizeStatus(info.status)
        if (status in FATAL_TORRENT_STATUSES) {
            throw TorrentTerminalStateException(info.status)
        }
        return when (status) {
            STATUS_DOWNLOADED -> info
            STATUS_WAITING_FILES_SELECTION -> {
                selectAllFiles(authHeader, torrentId)
                waitForStatus(
                    authHeader = authHeader,
                    torrentId = torrentId,
                    targetStatus = STATUS_DOWNLOADED,
                    deadlineMs = deadlineMs,
                    initialInfo = info
                )
            }

            STATUS_QUEUED, STATUS_DOWNLOADING -> waitForStatus(
                authHeader = authHeader,
                torrentId = torrentId,
                targetStatus = STATUS_DOWNLOADED,
                deadlineMs = deadlineMs,
                initialInfo = info
            )

            else -> {
                info = waitForStatus(
                    authHeader = authHeader,
                    torrentId = torrentId,
                    targetStatus = STATUS_WAITING_FILES_SELECTION,
                    deadlineMs = deadlineMs,
                    initialInfo = info
                )
                selectAllFiles(authHeader, torrentId)
                waitForStatus(
                    authHeader = authHeader,
                    torrentId = torrentId,
                    targetStatus = STATUS_DOWNLOADED,
                    deadlineMs = deadlineMs,
                    initialInfo = info
                )
            }
        }
    }

    private suspend fun selectAllFiles(
        authHeader: String,
        torrentId: String
    ) {
        withRateLimit {
            api.selectFiles(
                authHeader = authHeader,
                torrentId = torrentId,
                fileIdsCsv = SELECT_ALL_FILES
            )
        }
    }

    private suspend fun waitForStatus(
        authHeader: String,
        torrentId: String,
        targetStatus: String,
        deadlineMs: Long,
        initialInfo: TorrentInfoDto? = null
    ): TorrentInfoDto {
        val normalizedTarget = normalizeStatus(targetStatus)
        var info = initialInfo
        if (info != null) {
            val currentStatus = normalizeStatus(info.status)
            if (currentStatus == normalizedTarget) {
                return info
            }
            if (currentStatus in FATAL_TORRENT_STATUSES) {
                throw TorrentTerminalStateException(info.status)
            }
        }

        var pollCount = 0
        while (clockProvider.nowEpochMillis() < deadlineMs) {
            info = withRateLimit {
                api.getTorrentInfo(authHeader, torrentId)
            }
            val currentStatus = normalizeStatus(info.status)
            if (currentStatus == normalizedTarget) {
                return info
            }
            if (currentStatus in FATAL_TORRENT_STATUSES) {
                throw TorrentTerminalStateException(info.status)
            }
            delay(pollDelayMs(pollCount))
            pollCount += 1
        }
        throw TorrentNotReadyException()
    }

    private suspend fun waitForHostLinks(
        authHeader: String,
        torrentId: String,
        deadlineMs: Long,
        initialInfo: TorrentInfoDto
    ): TorrentInfoDto {
        var info = initialInfo
        if (hasAnyHostLink(info)) {
            return info
        }
        var pollCount = 0
        while (clockProvider.nowEpochMillis() < deadlineMs) {
            info = withRateLimit {
                api.getTorrentInfo(authHeader, torrentId)
            }
            if (hasAnyHostLink(info)) {
                return info
            }
            val status = normalizeStatus(info.status)
            if (status in FATAL_TORRENT_STATUSES) {
                throw TorrentTerminalStateException(info.status)
            }
            delay(pollDelayMs(pollCount))
            pollCount += 1
        }
        throw TorrentNotReadyException()
    }

    private suspend fun findAccountTorrentByHash(
        authHeader: String,
        infoHash: String
    ): TorrentSummaryDto? {
        val normalizedHash = infoHash.lowercase()
        val seenIds = mutableSetOf<String>()
        for (page in 1..TORRENT_LIST_MAX_PAGES) {
            val torrents = withRateLimit {
                api.listTorrents(
                    authHeader = authHeader,
                    page = page,
                    limit = TORRENT_LIST_PAGE_SIZE
                )
            }
            if (torrents.isEmpty()) {
                return null
            }
            torrents.firstOrNull { summary ->
                summary.hash?.lowercase() == normalizedHash
            }?.let { return it }

            val newCount = torrents.count { summary ->
                seenIds.add(summary.id)
            }
            if (newCount == 0) {
                return null
            }
            if (page == 1 && torrents.size > TORRENT_LIST_PAGE_SIZE) {
                return null
            }
            if (torrents.size < TORRENT_LIST_PAGE_SIZE) {
                return null
            }
        }
        return null
    }

    private suspend fun getTorrentInfoWithRetry(
        authHeader: String,
        torrentId: String
    ): TorrentInfoDto {
        var attempt = 0
        while (attempt < INITIAL_TORRENT_INFO_MAX_ATTEMPTS) {
            attempt += 1
            try {
                return withRateLimit {
                    api.getTorrentInfo(authHeader, torrentId)
                }
            } catch (throwable: Throwable) {
                if (attempt >= INITIAL_TORRENT_INFO_MAX_ATTEMPTS || !shouldRetryInfoFetch(throwable)) {
                    throw throwable
                }
                delay(INITIAL_TORRENT_INFO_RETRY_BASE_MS * attempt)
            }
        }
        throw TorrentNotReadyException()
    }

    private fun shouldRetryInfoFetch(throwable: Throwable): Boolean {
        if (throwable !is HttpException) {
            return false
        }
        if (throwable.code() == 404) {
            return true
        }
        val errorBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull()?.lowercase() ?: return false
        return errorBody.contains("unknown_ressource") || errorBody.contains("resource not found")
    }

    private fun resolveHostLinkForFile(
        info: TorrentInfoDto,
        fileId: Int
    ): String? {
        val selectedFiles = info.files.filter { it.selected == 1 }
        val selectedIndex = selectedFiles.indexOfFirst { it.id == fileId }
        if (selectedIndex >= 0 && selectedIndex < info.links.size) {
            val candidate = info.links[selectedIndex]
            if (candidate.isNotBlank()) {
                return candidate
            }
        }
        val selectedFile = info.files.firstOrNull { it.id == fileId } ?: return null
        selectedFile.unrestricted?.takeIf { it.isNotBlank() }?.let { return it }
        selectedFile.link?.takeIf { it.isNotBlank() }?.let { return it }
        if (selectedFiles.size == 1 && info.links.size == 1) {
            return info.links.firstOrNull { it.isNotBlank() }
        }
        return null
    }

    private fun hasAnyHostLink(info: TorrentInfoDto): Boolean {
        if (info.links.any { it.isNotBlank() }) {
            return true
        }
        return info.files.any { file ->
            !file.unrestricted.isNullOrBlank() || !file.link.isNullOrBlank()
        }
    }

    private fun isFileSelected(files: List<TorrentFileDto>, fileId: Int): Boolean {
        return files.firstOrNull { it.id == fileId }?.selected == 1
    }

    private fun normalizeStatus(status: String?): String {
        return status?.trim()?.lowercase().orEmpty()
    }

    private fun pollDelayMs(pollCount: Int): Long {
        return if (pollCount < FAST_POLL_COUNT) {
            FAST_POLL_DELAY_MS
        } else {
            SLOW_POLL_DELAY_MS
        }
    }

    private fun extractInfoHash(magnetUrl: String): String? {
        val rawToken = MAGNET_INFO_HASH_REGEX.find(magnetUrl)?.groupValues?.getOrNull(1)
            ?.trim()
            ?: return null
        if (rawToken.length == HEX_INFO_HASH_LENGTH && rawToken.all { it.isHexDigit() }) {
            return rawToken.lowercase()
        }
        if (rawToken.length == BASE32_INFO_HASH_LENGTH && rawToken.all { it.isBase32Char() }) {
            return decodeBase32InfoHash(rawToken)
        }
        return null
    }

    private fun decodeBase32InfoHash(rawToken: String): String? {
        var bitBuffer = 0
        var bitCount = 0
        val bytes = ArrayList<Byte>(INFO_HASH_BYTES)
        for (char in rawToken.uppercase(Locale.US)) {
            val index = BASE32_ALPHABET.indexOf(char)
            if (index < 0) {
                return null
            }
            bitBuffer = (bitBuffer shl 5) or index
            bitCount += 5
            while (bitCount >= 8) {
                bitCount -= 8
                val nextByte = ((bitBuffer shr bitCount) and 0xFF).toByte()
                bytes += nextByte
            }
        }
        if (bytes.size != INFO_HASH_BYTES) {
            return null
        }
        return bytes.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xFF)
        }
    }

    private fun Char.isHexDigit(): Boolean {
        val lower = lowercaseChar()
        return this in '0'..'9' || lower in 'a'..'f'
    }

    private fun Char.isBase32Char(): Boolean {
        val upper = uppercaseChar()
        return upper in 'A'..'Z' || upper in '2'..'7'
    }

    private fun isAuthError(throwable: Throwable): Boolean {
        return throwable is HttpException && throwable.code() in setOf(401, 403)
    }

    private suspend fun invalidateSession(infoHash: String) {
        sessionMutex.withLock {
            cachedSessionsByHash.remove(infoHash.lowercase())
        }
    }

    private suspend fun <T> withRateLimit(block: suspend () -> T): T {
        requestGate.awaitSlot()
        return block()
    }

    private fun bearer(apiKey: String): String = "Bearer ${apiKey.trim()}"

    companion object {
        private const val READY_TIMEOUT_MS = 3 * 60 * 1000L
        private const val FAST_POLL_DELAY_MS = 2_000L
        private const val SLOW_POLL_DELAY_MS = 5_000L
        private const val FAST_POLL_COUNT = 15
        private const val REQUEST_BUDGET_PER_MINUTE = 220
        private const val TORRENT_LIST_PAGE_SIZE = 100
        private const val TORRENT_LIST_MAX_PAGES = 100
        private const val INITIAL_TORRENT_INFO_MAX_ATTEMPTS = 3
        private const val INITIAL_TORRENT_INFO_RETRY_BASE_MS = 500L
        private const val SESSION_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val STATUS_WAITING_FILES_SELECTION = "waiting_files_selection"
        private const val STATUS_QUEUED = "queued"
        private const val STATUS_DOWNLOADING = "downloading"
        private const val STATUS_DOWNLOADED = "downloaded"
        private const val SELECT_ALL_FILES = "all"
        private const val HEX_INFO_HASH_LENGTH = 40
        private const val BASE32_INFO_HASH_LENGTH = 32
        private const val INFO_HASH_BYTES = 20
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        private val MAGNET_INFO_HASH_REGEX = Regex("""(?i)(?:^|[?&])xt=urn:btih:([a-z0-9]{32,40})""")
        private val FATAL_TORRENT_STATUSES = setOf("magnet_error", "error", "virus", "dead")

        fun create(clockProvider: ClockProvider): RealDebridClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()
            val json = Json { ignoreUnknownKeys = true }
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.real-debrid.com/rest/1.0/")
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .client(okHttpClient)
                .build()
            val api = retrofit.create(RealDebridApi::class.java)
            return RealDebridClient(api, clockProvider)
        }
    }
}

class TorrentNotReadyException : IllegalStateException("Torrent not ready")

class FileRematchException(message: String) : IllegalStateException(message)

class TorrentTerminalStateException(status: String?) :
    IllegalStateException("Torrent cannot be downloaded due to status: ${status ?: "unknown"}")

data class ResolvedTorrentFiles(
    val torrentId: String,
    val files: List<TorrentFileDto>
)

private data class TorrentSession(
    val infoHash: String,
    val torrentId: String
)

private data class CachedTorrentSession(
    val session: TorrentSession,
    val expiresAtEpochMs: Long
)

private class RequestGate(
    private val clockProvider: ClockProvider,
    private val budgetPerMinute: Int
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun awaitSlot() {
        while (true) {
            val waitMs = mutex.withLock {
                val now = clockProvider.nowEpochMillis()
                while (timestamps.isNotEmpty() && now - timestamps.first() >= 60_000L) {
                    timestamps.removeFirst()
                }
                if (timestamps.size < budgetPerMinute) {
                    timestamps.addLast(now)
                    0L
                } else {
                    val oldest = timestamps.first()
                    (60_000L - (now - oldest)).coerceAtLeast(100L)
                }
            }
            if (waitMs == 0L) {
                return
            }
            delay(waitMs)
        }
    }
}
