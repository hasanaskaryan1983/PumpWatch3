package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.math.abs
import kotlin.math.sqrt

data class MarketChart(
    val prices: List<List<Double>>,
    @SerializedName("total_volumes") val totalVolumes: List<List<Double>>? = null
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

// ---------- تنظیمات استراتژی ----------

data class BacktestConfig(
    val buyDrop: Double = 5.0,
    val sellRise: Double = 6.0,
    val stopLoss: Double = 6.0,
    val rsiMax: Double = 40.0,
    val stochMax: Double = 25.0,
    val useTrend: Boolean = true,
    val useMacd: Boolean = true,
    val useBollinger: Boolean = true,
    val useVolume: Boolean = true,
    val useBreakEven: Boolean = true
)

// ---------- موتور بک‌تست نسخه ۳ ----------

object BacktestEngine {

    fun run(
        prices: List<Pair<Long, Double>>,
        config: BacktestConfig,
        volumes: List<Double> = emptyList()
    ): BacktestResult {
        val closes = prices.map { it.second }
        val n = closes.size
        if (n < 80) {
            return BacktestResult(0, 0, 0, 0.0, 0.0, 0.0, emptyList())
        }

        val ema20 = emaSeries(closes, 20)
        val ema50 = emaSeries(closes, 50)
        val rsi = rsiSeries(closes, 14)
        val stoch = stochSeries(closes, 14)
        val pb = bollingerPB(closes, 20)
        val hist = macdHistSeries(closes)
        val atrPct = atrPctSeries(closes, 14)

        val trades = mutableListOf<SimulatedTrade>()
        var inPosition = false
        var entry = 0.0
        var entryIndex = 0
        var breakEven = false

        var equity = 1.0
        var peakEquity = 1.0
        var maxDd = 0.0

        fun close(price: Double) {
            val pnl = (price - entry) / entry * 100
            trades.add(SimulatedTrade(entry, price, pnl))
            equity *= (1 + pnl / 100)
            peakEquity = maxOf(peakEquity, equity)
            maxDd = maxOf(maxDd, (peakEquity - equity) / peakEquity * 100)
            inPosition = false
            breakEven = false
        }

        for (i in 60 until n) {
            val price = closes[i]

            if (!inPosition) {
                val peak20 = closes.subList(i - 20, i + 1).maxOrNull() ?: price
                val dropped = (peak20 - price) / peak20 * 100 >= config.buyDrop
                val confirm = price > closes[i - 1]

                val trendOk = !config.useTrend ||
                        (ema20[i] > ema50[i] && ema50[i] > ema50[i - 10])
                val rsiOk = rsi[i] < config.rsiMax
                val stochOk = stoch[i] < config.stochMax
                val bollOk = !config.useBollinger || pb[i] < 0.2
                val macdOk = !config.useMacd ||
                        (hist[i] > hist[i - 1] && hist[i - 1] > hist[i - 2])
                val volOk = !config.useVolume || volumes.isEmpty() ||
                        (i >= 20 && volumes[i] > 1.3 * volumes.subList(i - 20, i).average())
                val atrOk = atrPct[i] in 0.5..6.0

                if (dropped && confirm && trendOk && rsiOk && stochOk &&
                    bollOk && macdOk && volOk && atrOk
                ) {
                    inPosition = true
                    entry = price
                    entryIndex = i
                }
            } else {
                val pnl = (price - entry) / entry * 100

                if (config.useBreakEven && !breakEven && pnl >= config.sellRise * 0.5) {
                    breakEven = true
                }

                when {
                    pnl >= config.sellRise -> close(price)
                    breakEven && price <= entry -> close(price)
                    !breakEven && pnl <= -config.stopLoss -> close(price)
                    rsi[i] >= 70 && pnl >= config.sellRise * 0.4 -> close(price)
                    i - entryIndex > 96 -> close(price)
                }
            }
        }
        if (inPosition) close(closes[n - 1])

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

    // ---------- EMA ----------

    private fun emaSeries(data: List<Double>, period: Int): List<Double> {
        val out = DoubleArray(data.size)
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in data.indices) {
            if (i < period - 1) out[i] = ema
            else {
                ema = data[i] * k + ema * (1 - k)
                out[i] = ema
            }
        }
        return out.toList()
    }

    // ---------- RSI ----------

    private fun rsiSeries(data: List<Double>, period: Int): List<Double> {
        val out = DoubleArray(data.size) { 50.0 }
        if (data.size < period + 1) return out.toList()
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val d = data[i] - data[i - 1]
            if (d > 0) avgGain += d else avgLoss -= d
        }
        avgGain /= period
        avgLoss /= period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        for (i in period + 1 until data.size) {
            val d = data[i] - data[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-d, 0.0)) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        return out.toList()
    }

    // ---------- Stochastic %K ----------

    private fun stochSeries(data: List<Double>, period: Int): List<Double> {
        val out = DoubleArray(data.size) { 50.0 }
        for (i in period - 1 until data.size) {
            val win = data.subList(i - period + 1, i + 1)
            val hh = win.maxOrNull() ?: data[i]
            val ll = win.minOrNull() ?: data[i]
            out[i] = if (hh > ll) (data[i] - ll) / (hh - ll) * 100 else 50.0
        }
        return out.toList()
    }

    // ---------- Bollinger %B ----------

    private fun bollingerPB(data: List<Double>, period: Int): List<Double> {
        val out = DoubleArray(data.size) { 0.5 }
        for (i in period - 1 until data.size) {
            val win = data.subList(i - period + 1, i + 1)
            val mid = win.average()
            val sd = sqrt(win.map { (it - mid) * (it - mid) }.average())
            val upper = mid + 2 * sd
            val lower = mid - 2 * sd
            out[i] = if (upper > lower) (data[i] - lower) / (upper - lower) else 0.5
        }
        return out.toList()
    }

    // ---------- MACD Histogram ----------

    private fun macdHistSeries(data: List<Double>): List<Double> {
        val ema12 = emaSeries(data, 12)
        val ema26 = emaSeries(data, 26)
        val macd = List(data.size) { ema12[it] - ema26[it] }
        val signal = emaSeries(macd, 9)
        return List(data.size) { macd[it] - signal[it] }
    }

    // ---------- ATR % ----------

    private fun atrPctSeries(data: List<Double>, period: Int): List<Double> {
        val out = DoubleArray(data.size) { 1.0 }
        if (data.size < period + 1) return out.toList()
        var atr = 0.0
        for (i in 1..period) atr += abs(data[i] - data[i - 1])
        atr /= period
        out[period] = if (data[period] > 0) atr / data[period] * 100 else 1.0
        for (i in period + 1 until data.size) {
            val tr = abs(data[i] - data[i - 1])
            atr = (atr * (period - 1) + tr) / period
            out[i] = if (data[i] > 0) atr / data[i] * 100 else 1.0
        }
        return out.toList()
    }
}
