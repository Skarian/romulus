package com.romulus.spikes.spike4

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant

@Serializable
data class TorrentSummaryDto(
    val id: String,
    val hash: String? = null,
    val status: String? = null,
)

@Serializable
data class AvailableHostDto(
    val host: String,
    @SerialName("max_file_size")
    val maxFileSize: Long? = null,
)

@Serializable
data class AddedMagnetDto(
    val id: String,
    val uri: String? = null,
)

@Serializable
data class TorrentInfoDto(
    val id: String,
    val hash: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val progress: Double? = null,
    val speed: Long? = null,
    val seeders: Int? = null,
    val ended: String? = null,
    val files: List<TorrentFileDto> = emptyList(),
    val links: List<String> = emptyList(),
)

@Serializable
data class TorrentFileDto(
    val id: Int,
    val path: String,
    val bytes: Long = 0,
    val selected: Int = 0,
    @SerialName("unrestricted")
    val unrestricted: String? = null,
    @SerialName("link")
    val link: String? = null,
)

@Serializable
data class UnrestrictedLinkDto(
    val id: String? = null,
    val filename: String,
    @SerialName("download")
    val downloadUrl: String,
    @SerialName("filesize")
    val fileSize: Long? = null,
)

@Serializable
data class ApiErrorPayload(
    @SerialName("error_code")
    val errorCode: Int? = null,
    @SerialName("error")
    val error: String? = null,
)

@Serializable
data class SelectionResolution(
    val desiredPaths: List<String>,
    val candidateFiles: List<TorrentFileDto>,
    val resolvedFiles: List<TorrentFileDto>,
    val payload: String,
    val usesAllLiteral: Boolean,
)

interface RealDebridApi {
    @GET("torrents")
    suspend fun getTorrents(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 100,
    ): Response<List<TorrentSummaryDto>>

    @GET("torrents/availableHosts")
    suspend fun getAvailableHosts(): Response<List<AvailableHostDto>>

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Field("magnet") magnet: String,
        @Field("host") host: String,
    ): Response<AddedMagnetDto>

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Path("id") torrentId: String,
    ): Response<TorrentInfoDto>

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Path("id") torrentId: String,
        @Field("files") files: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Field("link") link: String,
    ): Response<UnrestrictedLinkDto>
}

class RealDebridClient(
    private val api: RealDebridApi,
) {
    suspend fun getAvailableHosts(traceEvents: MutableList<TraceEvent>): List<AvailableHostDto> {
        return executeJson(
            method = "GET",
            endpoint = "/torrents/availableHosts",
            traceEvents = traceEvents,
            successSummary = "loaded available hosts",
        ) {
            api.getAvailableHosts()
        }
    }

    suspend fun addMagnet(magnet: String, host: String, traceEvents: MutableList<TraceEvent>): AddedMagnetDto {
        return executeJson(
            method = "POST",
            endpoint = "/torrents/addMagnet",
            traceEvents = traceEvents,
            successSummary = "added magnet via host=$host",
        ) {
            api.addMagnet(magnet = magnet, host = host)
        }
    }

    suspend fun getTorrentInfo(torrentId: String, traceEvents: MutableList<TraceEvent>): TorrentInfoDto {
        return executeJson(
            method = "GET",
            endpoint = "/torrents/info/$torrentId",
            traceEvents = traceEvents,
            successSummary = "loaded torrent info id=$torrentId",
        ) {
            api.getTorrentInfo(torrentId)
        }
    }

    suspend fun selectFiles(torrentId: String, filesPayload: String, traceEvents: MutableList<TraceEvent>) {
        executeUnit(
            method = "POST",
            endpoint = "/torrents/selectFiles/$torrentId",
            traceEvents = traceEvents,
            successSummary = "selected files payload=$filesPayload",
        ) {
            api.selectFiles(torrentId, filesPayload)
        }
    }

    suspend fun unrestrictLink(link: String, traceEvents: MutableList<TraceEvent>): UnrestrictedLinkDto {
        return executeJson(
            method = "POST",
            endpoint = "/unrestrict/link",
            traceEvents = traceEvents,
            successSummary = "unrestricted link",
        ) {
            api.unrestrictLink(link)
        }
    }

    private suspend fun <T> executeJson(
        method: String,
        endpoint: String,
        traceEvents: MutableList<TraceEvent>,
        successSummary: String,
        call: suspend () -> Response<T>,
    ): T {
        val response = call()
        if (response.isSuccessful) {
            traceEvents += TraceEvent(
                timestamp = Instant.now().toString(),
                stage = "http-success",
                method = method,
                endpoint = endpoint,
                statusCode = response.code(),
                summary = successSummary,
            )
            return response.body() ?: throw IllegalStateException("Empty response body for $method $endpoint")
        }
        throw toException(method, endpoint, response, traceEvents)
    }

    private suspend fun executeUnit(
        method: String,
        endpoint: String,
        traceEvents: MutableList<TraceEvent>,
        successSummary: String,
        call: suspend () -> Response<Unit>,
    ) {
        val response = call()
        if (response.isSuccessful) {
            traceEvents += TraceEvent(
                timestamp = Instant.now().toString(),
                stage = "http-success",
                method = method,
                endpoint = endpoint,
                statusCode = response.code(),
                summary = successSummary,
            )
            return
        }
        throw toException(method, endpoint, response, traceEvents)
    }

    private fun toException(
        method: String,
        endpoint: String,
        response: Response<*>,
        traceEvents: MutableList<TraceEvent>,
    ): RealDebridApiException {
        val body = response.errorBody()?.string().orEmpty()
        val parsed = runCatching { spike4Json.decodeFromString<ApiErrorPayload>(body) }.getOrNull()
        val providerCode = parsed?.errorCode
        val providerMessage = parsed?.error ?: body.ifBlank { response.message() }
        traceEvents += TraceEvent(
            timestamp = Instant.now().toString(),
            stage = "http-error",
            method = method,
            endpoint = endpoint,
            statusCode = response.code(),
            providerCode = providerCode,
            summary = providerMessage.orEmpty().ifBlank { "request failed" },
        )
        return RealDebridApiException(
            message = "$method $endpoint failed with HTTP ${response.code()}${providerCode?.let { " / provider $it" }.orEmpty()}",
            httpStatus = response.code(),
            providerCode = providerCode,
            providerMessage = providerMessage,
            endpoint = endpoint,
            method = method,
        )
    }

    companion object {
        private const val BASE_URL = "https://api.real-debrid.com/rest/1.0/"

        fun create(apiToken: String): RealDebridClient {
            val httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $apiToken")
                        .build()
                    chain.proceed(request)
                })
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(spike4Json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RealDebridClient(retrofit.create(RealDebridApi::class.java))
        }
    }
}

class SelectionPlanner {
    fun resolveExactPath(desiredPath: String, providerFiles: List<TorrentFileDto>): SelectionResolution {
        require(providerFiles.isNotEmpty()) { "Provider returned no files." }
        val exactPath = normalizeAbsolutePath(desiredPath)
        val candidateFiles = providerFiles.filter { it.path == exactPath }
        require(candidateFiles.isNotEmpty()) { "No provider files matched exact path $desiredPath" }
        require(candidateFiles.size == 1) { "Exact path $desiredPath matched ${candidateFiles.size} provider files." }

        val resolvedFiles = listOf(candidateFiles.single())
        val usesAllLiteral = resolvedFiles.size == providerFiles.size
        val payload = if (usesAllLiteral) {
            "all"
        } else {
            resolvedFiles.joinToString(",") { it.id.toString() }
        }
        return SelectionResolution(
            desiredPaths = listOf(exactPath),
            candidateFiles = candidateFiles,
            resolvedFiles = resolvedFiles,
            payload = payload,
            usesAllLiteral = usesAllLiteral,
        )
    }

    private fun normalizeAbsolutePath(path: String): String {
        val value = path.trim()
        require(value.isNotEmpty()) { "Path must not be blank." }
        return if (value.startsWith('/')) value else "/$value"
    }
}

data class ResolverOutcome(
    val record: ResolverRecord,
    val providerInfo: TorrentInfoDto,
    val exactZipFile: TorrentFileDto,
)

class Spike4Resolver(
    private val client: RealDebridClient,
    private val selectionPlanner: SelectionPlanner = SelectionPlanner(),
) {
    suspend fun resolveExactZip(
        runId: String,
        config: Spike4RuntimeConfig,
        diagnostics: Spike4DiagnosticsStore,
    ): ResolverOutcome {
        val traceEvents = mutableListOf<TraceEvent>()
        val acquisitionSamples = mutableListOf<ProviderAcquisitionSample>()
        diagnostics.record("archive-selection", "resolver-start", "started", runId = runId)
        val host = client.getAvailableHosts(traceEvents).firstOrNull()?.host
            ?: throw Spike4FailureException(Spike4Stage.RESOLVER, "NO_AVAILABLE_HOSTS", "Real-Debrid returned no available hosts.")
        val addedMagnet = client.addMagnet(config.magnet, host, traceEvents)
        val selectionInfo = waitForFiles(addedMagnet.id, traceEvents, acquisitionSamples)
        val selection = selectionPlanner.resolveExactPath(config.normalizedExactZipPath, selectionInfo.files)
        client.selectFiles(addedMagnet.id, selection.payload, traceEvents)
        val readyInfo = waitForLinksReady(addedMagnet.id, selection.resolvedFiles.map { it.id }.toSet(), traceEvents, acquisitionSamples)
        val restrictedLink = readyInfo.links.firstOrNull()
            ?: throw Spike4FailureException(Spike4Stage.LINK_HANDLING, "LINKS_EMPTY", "Resolver reached link-ready state without returned links.")
        val unrestricted = client.unrestrictLink(restrictedLink, traceEvents)
        diagnostics.record(
            "archive-selection",
            "resolver-complete",
            "succeeded",
            runId = runId,
            details = mapOf("torrentId" to addedMagnet.id),
        )
        return ResolverOutcome(
            record = ResolverRecord(
                runId = runId,
                host = host,
                torrentId = addedMagnet.id,
                exactZipPath = config.normalizedExactZipPath,
                selectedProviderFileIds = selection.resolvedFiles.map { it.id },
                selectedProviderPaths = selection.resolvedFiles.map { it.path },
                selectionPayload = selection.payload,
                restrictedLinks = readyInfo.links,
                unrestrictedDownloadUrl = unrestricted.downloadUrl,
                pollingSummary = PollingSummary(
                    attempts = acquisitionSamples.size,
                    elapsedMillis = acquisitionSamples.size * 2_000L,
                    lastObservedStatus = acquisitionSamples.lastOrNull()?.status,
                    firstReturnedLinksElapsedMillis = acquisitionSamples.indexOfFirst { it.linkCount > 0 }
                        .takeIf { it >= 0 }
                        ?.let { (it + 1) * 2_000L },
                ),
                traceEvents = traceEvents,
                providerAcquisitionSamples = acquisitionSamples,
            ),
            providerInfo = readyInfo,
            exactZipFile = selection.resolvedFiles.single(),
        )
    }

    private suspend fun waitForFiles(
        torrentId: String,
        traceEvents: MutableList<TraceEvent>,
        acquisitionSamples: MutableList<ProviderAcquisitionSample>,
    ): TorrentInfoDto {
        repeat(30) {
            val info = client.getTorrentInfo(torrentId, traceEvents)
            acquisitionSamples += info.toAcquisitionSample()
            if (info.files.isNotEmpty()) {
                return info
            }
            delay(2_000)
        }
        throw Spike4FailureException(Spike4Stage.PROVIDER_ACQUISITION, "FILES_NOT_READY", "Provider files did not materialize within the bounded wait window.")
    }

    private suspend fun waitForLinksReady(
        torrentId: String,
        expectedSelectedIds: Set<Int>,
        traceEvents: MutableList<TraceEvent>,
        acquisitionSamples: MutableList<ProviderAcquisitionSample>,
    ): TorrentInfoDto {
        repeat(60) {
            val info = client.getTorrentInfo(torrentId, traceEvents)
            acquisitionSamples += info.toAcquisitionSample()
            val status = info.status.orEmpty().lowercase()
            if (status in setOf("magnet_error", "error", "virus", "dead")) {
                throw Spike4FailureException(Spike4Stage.PROVIDER_ACQUISITION, "TERMINAL_PROVIDER_STATUS", "Provider entered terminal status ${info.status}.")
            }
            val selectedIds = info.files.filter { it.selected == 1 }.map { it.id }.toSet()
            if (!expectedSelectedIds.all { it in selectedIds }) {
                delay(2_000)
                return@repeat
            }
            if (info.links.isNotEmpty()) {
                return info
            }
            delay(2_000)
        }
        throw Spike4FailureException(Spike4Stage.LINK_HANDLING, "LINK_READY_TIMEOUT", "Provider links did not become ready within the bounded wait window.")
    }

    private fun TorrentInfoDto.toAcquisitionSample(): ProviderAcquisitionSample =
        ProviderAcquisitionSample(
            timestamp = nowUtc(),
            status = status,
            progress = progress,
            speed = speed,
            seeders = seeders,
            ended = ended,
            linkCount = links.size,
        )
}
