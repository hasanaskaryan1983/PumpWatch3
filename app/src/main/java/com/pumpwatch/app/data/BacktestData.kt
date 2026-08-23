package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class MarketChart(
    val prices: List<List<Double>>
)

interface CoinGeckoChartApi {
    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int
    ): MarketChart
}

object ChartClient {
    val api: CoinGeckoChartApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoChartApi::class.java)
    }
}

data class SimulatedTrade(
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlPercent: Double
)

data class BacktestResult(
    val totalTrades: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRatePercent: Double,
    val netPnlPercent: Double,
    val maxDrawdownPercent: Double,
    val trades: List<SimulatedTrade>
)

object BacktestEngine {

    fun run(
        prices: List<Pair<Long, Double>>,
        buyDrop: Double,
        sellRise: Double
    ): BacktestResult {
        val trades = mutableListOf<SimulatedTrade>()
        var inPosition = false
        var entryPrice = 0.0
        var peak = 0.0
        var equity = 1.0
        var peakEquity = 1.0
        var maxDd = 0.0

        fun close(price: Double) {
            val change = (price - entryPrice) / entryPrice * 100
            trades.add(SimulatedTrade(entryPrice, price, change))
            equity *= (1 + change / 100)
            peakEquity = maxOf(peakEquity, equity)
            maxDd = maxOf(maxDd, (peakEquity - equity) / peakEquity * 100)
            inPosition = false
            peak = price
        }

        for ((_, price) in prices) {
            if (!inPosition) {
                peak = maxOf(peak, price)
                if (peak > 0 && price <= peak * (1 - buyDrop / 100)) {
                    inPosition = true
                    entryPrice = price
                }
            } else {
                val change = (price - entryPrice) / entryPrice * 100
                if (change >= sellRise || change <= -sellRise) {
                    close(price)
                }
            }
        }
        if (inPosition && prices.isNotEmpty()) {
            close(prices.last().second)
        }

        val wins = trades.count { it.pnlPercent >= 0 }
        return BacktestResult(
            totalTrades = trades.size,
            winCount = wins,
            lossCount = trades.size - wins,
            winRatePercent = if (trades.isEmpty()) 0.0 else wins * 100.0 / trades.size,
            netPnlPercent = (equity - 1) * 100,
            maxDrawdownPercent = maxDd,
            trades = trades
        )
    }
}
