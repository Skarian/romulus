package com.romulus.mobile.data.realdebrid

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Query

interface RealDebridApi {
    @GET("user")
    suspend fun getUser(
        @Header("Authorization") authHeader: String
    ): RealDebridUserDto

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") authHeader: String,
        @Field("magnet") magnet: String
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

    @GET("torrents")
    suspend fun listTorrents(
        @Header("Authorization") authHeader: String,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TorrentSummaryDto>

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") authHeader: String,
        @Field("link") link: String
    ): UnrestrictedLinkDto
}
