package com.romulus.spikes.spike1.service

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.romulus.spikes.spike1.model.AddedMagnetDto
import com.romulus.spikes.spike1.model.ApiErrorPayload
import com.romulus.spikes.spike1.model.AvailableHostDto
import com.romulus.spikes.spike1.model.TorrentInfoDto
import com.romulus.spikes.spike1.model.TorrentSummaryDto
import com.romulus.spikes.spike1.model.TraceEvent
import com.romulus.spikes.spike1.model.UnrestrictedLinkDto
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import java.time.Instant

class RealDebridClient(
    private val api: RealDebridApi,
    private val json: Json,
) {
    suspend fun findAccountTorrentByHash(hash: String, traceEvents: MutableList<TraceEvent>): TorrentSummaryDto? {
        var page = 1
        while (true) {
            val torrents = executeJson(
                method = "GET",
                endpoint = "/torrents?page=$page&limit=100",
                traceEvents = traceEvents,
                successSummary = "listed account torrents page=$page",
            ) {
                api.getTorrents(page = page, limit = 100)
            }
            val match = torrents.firstOrNull { torrent ->
                torrent.hash?.equals(hash, ignoreCase = true) == true
            }
            if (match != null || torrents.size < 100) {
                return match
            }
            page += 1
        }
    }

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
            api.getTorrentInfo(torrentId = torrentId)
        }
    }

    suspend fun selectFiles(torrentId: String, filesPayload: String, traceEvents: MutableList<TraceEvent>) {
        executeUnit(
            method = "POST",
            endpoint = "/torrents/selectFiles/$torrentId",
            traceEvents = traceEvents,
            successSummary = "selected files payload=$filesPayload",
        ) {
            api.selectFiles(torrentId = torrentId, files = filesPayload)
        }
    }

    suspend fun unrestrictLink(link: String, traceEvents: MutableList<TraceEvent>): UnrestrictedLinkDto {
        return executeJson(
            method = "POST",
            endpoint = "/unrestrict/link",
            traceEvents = traceEvents,
            successSummary = "unrestricted link",
        ) {
            api.unrestrictLink(link = link)
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
            return response.body() ?: throw Spike1CliException("Empty response body for $method $endpoint")
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
        val parsed = runCatching { json.decodeFromString<ApiErrorPayload>(body) }.getOrNull()
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
        private const val baseUrl = "https://api.real-debrid.com/rest/1.0/"

        fun create(apiToken: String, json: Json = spikeJson()): RealDebridClient {
            val httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", "Bearer $apiToken")
                        .build()
                    chain.proceed(request)
                })
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RealDebridClient(
                api = retrofit.create(RealDebridApi::class.java),
                json = json,
            )
        }

        fun extractInfoHash(magnet: String): String? = MagnetInfoHash.extract(magnet)
    }
}
