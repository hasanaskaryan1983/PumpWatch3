package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface GeckoOhlcvApi {

    @GET("networks/{network}/pools/{address}/ohlcv/hour")
    suspend fun poolOhlcvHour(
        @Path("network") network: String,
        @Path("address") address: String
    ): OhlcvResponse
}

object GeckoOhlcv {
    val api: GeckoOhlcvApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.geckoterminal.com/api/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeckoOhlcvApi::class.java)
    }
}

data class OhlcvResponse(val data: OhlcvData?)

data class OhlcvData(val attributes: OhlcvAttrs?)

data class OhlcvAttrs(val ohlcv_list: List<List<Double>>?)
