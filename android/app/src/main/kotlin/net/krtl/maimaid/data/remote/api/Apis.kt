package net.krtl.maimaid.data.remote.api

import net.krtl.maimaid.data.remote.dto.RemoteDataResponse
import net.krtl.maimaid.data.remote.dto.SongIdItem
import retrofit2.http.GET

interface StaticDataApi {
    @GET("maimai/data.json")
    suspend fun getRemoteData(): RemoteDataResponse

    @GET("https://maimaid.shikoch.in/songid.json")
    suspend fun getProviderIds(): List<SongIdItem>
}
