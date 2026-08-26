package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.math.abs
import kotlin.math.max
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
    val pnlPercent: Double,
    val rr: Double,           // نسبت RR این معامله
    val exitReason: String    // دلیل خروج
)

data class BacktestResult(
    val totalTrades: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRatePercent: Double,
    val netPnlPercent: Double,
    val maxDrawdownPercent: Double,
    val avgRR: Double,        // میانگین RR
    val trades: List<SimulatedTrade>
)

// ---------- تنظیمات استراتژی ----------

data class BacktestConfig(
    val buyDrop: Double = 5.0,
    val sellRise: Double = 9.0,       // RR 1:1.5 با استاپ ۶٪
    val stopLoss: Double = 6.0,
    val rsiMax: Double = 32.0,        // پایین‌تر = سیگنال قوی‌تر
    val stochMax: Double = 20.0,      // پایین‌تر = سیگنال قوی‌تر
    val atrMult: Double = 2.5,        // ATR × 2.5 برای استاپ هوشمند
    val useTrend: Boolean = true,
    val useMacd: Boolean = true,
    val useBollinger: Boolean = true,
    val useVolume: Boolean = true,    // اجباری
    val useBreakEven: Boolean = true,
    val useMfi: Boolean = true,       // جدید
    val useAdx: Boolean = true,       // جدید
    val useTrailing: Boolean = true   // جدید
)

// ---------- موتور بک‌تست نسخه ۴ (بهینه‌شده) ----------

object BacktestEngine {

    fun run(
        prices: List<Pair<Long, Double>>,
        config: BacktestConfig,
        volumes: List<Double> = emptyList()
    ): BacktestResult {
        val closes = prices.map { it.second }
        val n = closes.size
        if (n < 80) {
            return BacktestResult(0, 0, 0, 0.0, 0.0, 0.0, 0.0, emptyList())
        }

        val ema20 = emaSeries(closes, 20)
        val ema50 = emaSeries(closes, 50)
        val rsi = rsiSeries(closes, 14)
        val stoch = stochSeries(closes, 14)
        val pb = bollingerPB(closes, 20)
        val hist = macdHistSeries(closes)
        val atrPct = atrPctSeries(closes, 14)
        val mfi = mfiSeries(closes, volumes, 14)       // جدید
        val adx = adxSeries(closes, 14)                 // جدید

        val trades = mutableListOf<SimulatedTrade>()
        var inPosition = false
        var entry = 0.0
        var entryIndex = 0
        var breakEven = false
        var trailingStop = 0.0
        var highestPrice = 0.0

        var equity = 1.0
        var peakEquity = 1.0
        var maxDd = 0.0

        fun close(price: Double, reason: String) {
            val pnl = (price - entry) / entry * 100
            val rr = if (config.stopLoss > 0) abs(pnl) / config.stopLoss else 0.0
            trades.add(SimulatedTrade(entry, price, pnl, rr, reason))
            equity *= (1 + pnl / 100)
            peakEquity = maxOf(peakEquity, equity)
            maxDd = maxOf(maxDd, (peakEquity - equity) / peakEquity * 100)
            inPosition = false
            breakEven = false
            trailingStop = 0.0
            highestPrice = 0.0
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
                
                // فیلتر حجم اجباری
                val volOk = !config.useVolume || volumes.isEmpty() ||
                        (i >= 20 && volumes[i] > 1.5 * volumes.subList(i - 20, i).average())
                
                // فیلتر MFI
                val mfiOk = !config.useMfi || mfi[i] < 35.0
                
                // فیلتر ADX
                val adxOk = !config.useAdx || adx[i] >= 20.0
                
                // ATR filter - از کوین‌های بیش‌ازحد پرنوسان جلوگیری کند
                val atrOk = atrPct[i] in 0.5..8.0

                if (dropped && confirm && trendOk && rsiOk && stochOk &&
                    bollOk && macdOk && volOk && mfiOk && adxOk && atrOk
                ) {
                    inPosition = true
                    entry = price
                    entryIndex = i
                    highestPrice = price
                    
                    // ATR-based stop
                    val atrBasedStop = price * (1 - config.atrMult * atrPct[i] / 100)
                    trailingStop = atrBasedStop
                }
            } else {
                val pnl = (price - entry) / entry * 100
                highestPrice = maxOf(highestPrice, price)

                // Break-even
                if (config.useBreakEven && !breakEven && pnl >= config.sellRise * 0.5) {
                    breakEven = true
                }

                // Trailing stop
                if (config.useTrailing && pnl >= 4.0) {
                    val newTrailing = highestPrice * (1 - config.atrMult * atrPct[i] / 100)
                    if (newTrailing > trailingStop) {
                        trailingStop = newTrailing
                    }
                }

                // ATR-based stop loss (داینامیک)
                val atrStop = entry * (1 - config.atrMult * atrPct[i] / 100)
                val effectiveStop = maxOf(atrStop, trailingStop)

                when {
                    pnl >= config.sellRise -> close(price, "هدف فروش رسید")
                    breakEven && price <= entry -> close(price, "بیک‌ایون")
                    price <= effectiveStop -> close(price, "استاپ ATR")
                    rsi[i] >= 70 && pnl >= config.sellRise * 0.4 -> close(price, "RSI اشباع + سود")
                    i - entryIndex > 96 -> close(price, "تایم‌اوت ۹۶ کندل")
                }
            }
        }
        if (inPosition) close(closes[n - 1], "پایان داده")

        val wins = trades.count { it.pnlPercent >= 0 }
        val avgRR = if (trades.isEmpty()) 0.0 else trades.map { it.rr }.average()
        
        return BacktestResult(
            totalTrades = trades.size,
            winCount = wins,
            lossCount = trades.size - wins,
            winRatePercent = if (trades.isEmpty()) 0.0 else wins * 100.0 / trades.size,
            netPnlPercent = (equity - 1) * 100,
            maxDrawdownPercent = maxDd,
            avgRR = avgRR,
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

    // ---------- MFI Series (جدید) ----------

    private fun mfiSeries(
        closes: List<Double>,
        volumes: List<Double>,
        period: Int
    ): List<Double> {
        val out = DoubleArray(closes.size) { 50.0 }
        if (closes.size != volumes.size || closes.size < period + 1) return out.toList()
        
        val tp = DoubleArray(closes.size)
        for (i in closes.indices) {
            tp[i] = closes[i] // simplified: using close as typical price proxy
        }
        
        for (i in period until closes.size) {
            var posFlow = 0.0
            var negFlow = 0.0
            for (j in i - period + 1..i) {
                val mf = tp[j] * volumes[j]
                if (j > 0) {
                    if (tp[j] > tp[j - 1]) posFlow += mf else negFlow += mf
                }
            }
            out[i] = if (negFlow == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + posFlow / negFlow))
        }
        return out.toList()
    }

    // ---------- ADX Series (جدید - ساده‌شده) ----------

    private fun adxSeries(data: List<Double>, period: Int): List<Double> {
        val out = DoubleArray(data.size) { 20.0 }
        if (data.size < period * 2) return out.toList()
        
        val plusDM = DoubleArray(data.size)
        val minusDM = DoubleArray(data.size)
        val tr = DoubleArray(data.size)
        
        for (i in 1 until data.size) {
            val up = data[i] - data[i - 1]
            plusDM[i] = if (up > 0) up else 0.0
            minusDM[i] = if (up < 0) -up else 0.0
            tr[i] = abs(up)
        }
        
        for (i in period * 2 until data.size) {
            var sTR = 0.0
            var sP = 0.0
            var sM = 0.0
            for (j in i - period + 1..i) {
                sTR += tr[j]
                sP += plusDM[j]
                sM += minusDM[j]
            }
            val pDI = if (sTR > 0) 100 * sP / sTR else 0.0
            val mDI = if (sTR > 0) 100 * sM / sTR else 0.0
            val dx = if (pDI + mDI > 0) 100 * abs(pDI - mDI) / (pDI + mDI) else 0.0
            out[i] = dx
        }
        return out.toList()
    }
}
