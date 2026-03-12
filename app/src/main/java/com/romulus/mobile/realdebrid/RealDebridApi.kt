package com.romulus.mobile.realdebrid

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.romulus.mobile.realdebrid.auth.TokenService
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
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

    fun createApi(tokenService: TokenService): RealDebridApi = RetrofitRealDebridApi(
        endpoints = createRetrofit().create(AuthenticatedRealDebridEndpoints::class.java),
        tokenService = tokenService
    )

    fun createAuthClient(): RealDebridAuthClient = RetrofitRealDebridAuthClient(
        endpoints = createRetrofit().create(AuthValidationEndpoints::class.java)
    )

    private fun createRetrofit(): Retrofit {
        val builder = Retrofit.Builder()
        builder.baseUrl(BASE_URL)
        builder.client(OkHttpClient())
        builder.addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        return builder.build()
    }
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
