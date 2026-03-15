package com.romulus.mobile.realdebrid

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.romulus.mobile.diagnostics.DiagnosticsFacade
import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.toDiagnosticContext
import com.romulus.mobile.realdebrid.auth.TokenService
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

internal interface RealDebridApi {
    suspend fun getAvailableHosts(): List<AvailableHostDto>

    suspend fun addMagnet(magnet: String, host: String): AddedMagnetDto

    suspend fun selectFiles(torrentId: String, fileIdsCsv: String)

    suspend fun getTorrentInfo(torrentId: String): TorrentInfoDto

    suspend fun deleteTorrent(torrentId: String)

    suspend fun unrestrictLink(link: String): UnrestrictedLinkDto
}

internal interface RealDebridAuthClient {
    suspend fun validateToken(candidate: String): Result<Unit>
}

internal class RetrofitRealDebridApi(
    private val endpoints: AuthenticatedRealDebridEndpoints,
    private val tokenService: TokenService
) : RealDebridApi {
    override suspend fun getAvailableHosts(): List<AvailableHostDto> = withAuth { authHeader ->
        endpoints.getAvailableHosts(authHeader)
    }

    override suspend fun addMagnet(magnet: String, host: String): AddedMagnetDto =
        withAuth { authHeader ->
            endpoints.addMagnet(
                authHeader = authHeader,
                magnet = magnet,
                host = host
            )
        }

    override suspend fun selectFiles(torrentId: String, fileIdsCsv: String) {
        withAuth { authHeader ->
            endpoints.selectFiles(
                authHeader = authHeader,
                torrentId = torrentId,
                fileIdsCsv = fileIdsCsv
            )
        }
    }

    override suspend fun getTorrentInfo(torrentId: String): TorrentInfoDto =
        withAuth { authHeader ->
            endpoints.getTorrentInfo(authHeader, torrentId)
        }

    override suspend fun deleteTorrent(torrentId: String) {
        withAuth { authHeader ->
            endpoints.deleteTorrent(authHeader, torrentId)
        }
    }

    override suspend fun unrestrictLink(link: String): UnrestrictedLinkDto =
        withAuth { authHeader ->
            endpoints.unrestrictLink(authHeader, link)
        }

    @Suppress("ThrowsCount")
    private suspend fun <T> withAuth(block: suspend (String) -> T): T {
        val authHeader = tokenService.readAuthHeader()
            ?: throw AuthRequiredException("Auth required")
        return try {
            block(authHeader)
        } catch (error: HttpException) {
            if (error.code() == HTTP_UNAUTHORIZED || error.code() == HTTP_FORBIDDEN) {
                tokenService.reportAuthFailure()
                throw AuthRequiredException("Auth required", error)
            }
            throw error
        }
    }
}

internal class RetrofitRealDebridAuthClient(private val endpoints: AuthValidationEndpoints) :
    RealDebridAuthClient {
    override suspend fun validateToken(candidate: String): Result<Unit> = try {
        endpoints.getUser("Bearer ${candidate.trim()}")
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: HttpException) {
        when (error.code()) {
            HTTP_UNAUTHORIZED,
            HTTP_FORBIDDEN -> Result.failure(InvalidTokenException())
            else -> Result.failure(
                IllegalStateException(
                    "API validation failed (${error.code()})",
                    error
                )
            )
        }
    } catch (error: IOException) {
        Result.failure(error)
    } catch (error: IllegalStateException) {
        Result.failure(error)
    }
}

internal object RealDebridHttpFactory {
    private val json = Json { ignoreUnknownKeys = true }

    fun createApi(tokenService: TokenService, diagnosticsFacade: DiagnosticsFacade): RealDebridApi =
        RetrofitRealDebridApi(
            endpoints = createRetrofit(diagnosticsFacade)
                .create(AuthenticatedRealDebridEndpoints::class.java),
            tokenService = tokenService
        )

    fun createAuthClient(diagnosticsFacade: DiagnosticsFacade): RealDebridAuthClient =
        RetrofitRealDebridAuthClient(
            endpoints = createRetrofit(diagnosticsFacade)
                .create(AuthValidationEndpoints::class.java)
        )

    @Suppress("ChainMethodContinuation")
    private fun createRetrofit(diagnosticsFacade: DiagnosticsFacade): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(RealDebridDiagnosticsInterceptor(diagnosticsFacade))
            .build()
        val builder = Retrofit.Builder()
        builder.baseUrl(BASE_URL)
        builder.client(client)
        builder.addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        return builder.build()
    }
}

internal class RealDebridDiagnosticsInterceptor(private val diagnosticsFacade: DiagnosticsFacade) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.nanoTime()
        return try {
            val response = chain.proceed(request)
            record(
                request = request.method to request.url.encodedPath,
                durationMillis = elapsedMillis(startedAt),
                outcome = "succeeded",
                extraContext = mapOf("status" to response.code.toString())
            )
            response
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IOException) {
            record(
                request = request.method to request.url.encodedPath,
                durationMillis = elapsedMillis(startedAt),
                outcome = "failed",
                extraContext = error.toDiagnosticContext()
            )
            throw error
        }
    }

    private fun record(
        request: Pair<String, String>,
        durationMillis: Long,
        outcome: String,
        extraContext: Map<String, String>
    ) {
        runBlocking {
            diagnosticsFacade.record(
                domain = DiagnosticDomain.REAL_DEBRID,
                event = "api-call",
                outcome = outcome,
                context = mapOf(
                    "method" to request.first,
                    "endpoint" to request.second,
                    "durationMs" to durationMillis.toString()
                ) + extraContext
            )
        }
    }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
}

internal interface AuthValidationEndpoints {
    @GET("user")
    suspend fun getUser(@Header("Authorization") authHeader: String): RealDebridUserDto
}

internal interface AuthenticatedRealDebridEndpoints {
    @GET("torrents/availableHosts")
    suspend fun getAvailableHosts(
        @Header("Authorization") authHeader: String
    ): List<AvailableHostDto>

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") authHeader: String,
        @Field("magnet") magnet: String,
        @Field("host") host: String
    ): AddedMagnetDto

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") authHeader: String,
        @Path("id") torrentId: String,
        @Field("files") fileIdsCsv: String
    )

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") authHeader: String,
        @Path("id") torrentId: String
    ): TorrentInfoDto

    @DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") authHeader: String,
        @Path("id") torrentId: String
    )

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") authHeader: String,
        @Field("link") link: String
    ): UnrestrictedLinkDto
}

internal class InvalidTokenException : IllegalArgumentException("Invalid API key")

private const val BASE_URL = "https://api.real-debrid.com/rest/1.0/"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val NANOS_PER_MILLISECOND = 1_000_000L
