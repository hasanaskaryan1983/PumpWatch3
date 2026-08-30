package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName

// ---------- مدل نمودار قیمت (CoinGecko) ----------

data class MarketChart(
    val prices: List<List<Double>>,
    @SerializedName("total_volumes") val totalVolumes: List<List<Double>>? = null
)
