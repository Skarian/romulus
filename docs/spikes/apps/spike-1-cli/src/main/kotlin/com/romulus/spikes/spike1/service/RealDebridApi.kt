package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.model.AddedMagnetDto
import com.romulus.spikes.spike1.model.AvailableHostDto
import com.romulus.spikes.spike1.model.TorrentInfoDto
import com.romulus.spikes.spike1.model.TorrentSummaryDto
import com.romulus.spikes.spike1.model.UnrestrictedLinkDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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
