package com.pumpwatch.app.engine

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class SignalParams(
    val rsiPeriod: Int = 14,
    val adxMin: Double = 25.0,
    val volumeMin: Double = 1.5,
    val breakoutLookback: Int = 20,
    val minScore: Double = 70.0,
    val goldenScore: Double = 80.0,
    val atrMult: Double = 1.5,
    val rr: Double = 1.5
)

data class SignalResult(
    val coinId: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val mode: String,
    val pumpScore: Int,
    val dumpScore: Int,
    val side: String,
    val score: Int,
    val golden: Boolean,
    val ultra: Boolean,
    val mtfAligned: Boolean,
    val mtfTrend: String,
    val adx: Double,
    val rsi: Double,
    val volumeRatio: Double,
    val funding: Double?,
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val reasons: List<String>
)

// ---------- موتور سیگنال حالت ۹۰٪ ----------

object SignalEngine {

    fun analyze(
        coinId: String,
        symbol: String,
        name: String,
        candles1h: List<Candle>,
        mode: String,
        funding: Double? = null,
        params: SignalParams = SignalParams()
    ): SignalResult? {
        if (candles1h.size < 100) return null

        val closes = Indicators.closes(candles1h)
        val volumes = candles1h.map { it.volume }
        val price = closes.last()

        // ---------- اندیکاتورهای 1H ----------
        val ema20 = Indicators.emaLast(closes, 20)
        val ema50 = Indicators.emaLast(closes, 50)
        val rsi = Indicators.rsi(closes, params.rsiPeriod)
        val macd = Indicators.macd(closes)
        val macdPrev = Indicators.macd(closes.dropLast(1))
        val adx = Indicators.adx(candles1h)
        val atr = Indicators.atr(candles1h)
        val st = Indicators.supertrend(candles1h)
        val volRatio = Indicators.volumeRatio(volumes)
        val bb = Indicators.bollinger(closes)
        val vp = Indicators.volumeProfile(candles1h)
        val obs = Indicators.findOrderBlocks(candles1h)
        val bos = Indicators.detectBOS(candles1h)
        val srsi = Indicators.stochRsi(closes)

        // ---------- تایم‌فریم‌های 4H و Daily ----------
        val c4h = Indicators.aggregate(candles1h, 4)
        val cD = Indicators.aggregate(candles1h, 24)
        val closes4h = Indicators.closes(c4h)
        val closesD = Indicators.closes(cD)

        val t1hUp = ema20 > ema50 && st.direction > 0
        val t1hDn = ema20 < ema50 && st.direction < 0
        val t4hUp = closes4h.size >= 60 &&
                Indicators.emaLast(closes4h, 20) > Indicators.emaLast(closes4h, 50) &&
                Indicators.supertrend(c4h).direction > 0
        val t4hDn = closes4h.size >= 60 &&
                Indicators.emaLast(closes4h, 20) < Indicators.emaLast(closes4h, 50) &&
                Indicators.supertrend(c4h).direction < 0
        val tDUp = closesD.size >= 60 &&
                Indicators.emaLast(closesD, 20) > Indicators.emaLast(closesD, 50)
        val tDDn = closesD.size >= 60 &&
                Indicators.emaLast(closesD, 20) < Indicators.emaLast(closesD, 50)

        val mtfUp = t1hUp && t4hUp && tDUp
        val mtfDn = t1hDn && t4hDn && tDDn
        val mtfAligned = mtfUp || mtfDn
        val mtfTrend = if (mtfUp) "UP" else if (mtfDn) "DOWN" else "MIX"

        // ---------- شکست‌ها ----------
        val lookback = params.breakoutLookback
        val prevHigh = candles1h.dropLast(1).takeLast(lookback).maxOf { it.high }
        val prevLow = candles1h.dropLast(1).takeLast(lookback).minOf { it.low }
        val breakout = price > prevHigh
        val breakdown = price < prevLow

        val inBullOB = obs.any { it.isBullish && price in it.bottom..it.top * 1.02 }
        val inBearOB = obs.any { !it.isBullish && price in it.bottom * 0.98..it.top }

        // ---------- امتیاز PUMP ----------
        var pump = 0
        val pReasons = mutableListOf<String>()

        if (price > ema20 && ema20 > ema50) { pump += 10; pReasons.add("روند صعودی EMA") }
        if (mtfUp) { pump += 10; pReasons.add("تأیید سه تایم‌فریم 📊") }
        if (rsi in 45.0..68.0) { pump += 8; pReasons.add("RSI در محدوده قدرت") }
        if (macd.macd > macd.signal && macd.histogram > macdPrev.histogram) {
            pump += 10; pReasons.add("MACD صعودی")
        }
        if (volRatio >= params.volumeMin) {
            pump += 12; pReasons.add("حجم ${String.format(Locale.US, "%.1f", volRatio)}x")
        }
        if (breakout) { pump += 12; pReasons.add("شکست مقاومت $lookback کندلی") }
        if (bb.widthPct < Indicators.bollinger(closes.dropLast(24)).widthPct * 0.8 && volRatio > 1.2) {
            pump += 8; pReasons.add("خروج از فشردگی بولینگر")
        }
        if (price > vp.poc) { pump += 8; pReasons.add("بالای POC حجم 🎯") }
        if (inBullOB) { pump += 8; pReasons.add("واکنش به اردر بلاک صعودی 🏦") }
        if (bos.first) { pump += 8; pReasons.add("شکست ساختار (BOS) 🏗️") }
        if (st.direction > 0) { pump += 6; pReasons.add("Supertrend صعودی") }
        if (adx >= params.adxMin) { pump += 5; pReasons.add("قدرت روند ADX") }
        if (srsi < 80) pump += 3
        if (funding != null && funding <= -0.0003) { pump += 5; pReasons.add("فاندینگ منفی") }
        pump = min(100, pump)

        // ---------- امتیاز DUMP ----------
        var dump = 0
        val dReasons = mutableListOf<String>()

        if (price < ema20 && ema20 < ema50) { dump += 10; dReasons.add("روند نزولی EMA") }
        if (mtfDn) { dump += 10; dReasons.add("تأیید سه تایم‌فریم 📊") }
        if (rsi in 32.0..55.0) { dump += 8; dReasons.add("RSI ضعیف") }
        if (macd.macd < macd.signal && macd.histogram < macdPrev.histogram) {
            dump += 10; dReasons.add("MACD نزولی")
        }
        if (volRatio >= params.volumeMin && price < closes.dropLast(1).last()) {
            dump += 12; dReasons.add("حجم فروش بالا")
        }
        if (breakdown) { dump += 12; dReasons.add("شکست حمایت $lookback کندلی") }
        if (price < vp.poc) { dump += 8; dReasons.add("زیر POC حجم 🎯") }
        if (inBearOB) { dump += 8; dReasons.add("واکنش به اردر بلاک نزولی 🏦") }
        if (bos.second) { dump += 8; dReasons.add("شکست ساختار نزولی 🏗️") }
        if (st.direction < 0) { dump += 6; dReasons.add("Supertrend نزولی") }
        if (adx >= params.adxMin) dump += 5
        if (funding != null && funding >= 0.0005) { dump += 5; dReasons.add("فاندینگ مثبت شدید") }
        dump = min(100, dump)

        // ---------- تصمیم ----------
        val side = when {
            pump >= dump && pump >= params.minScore -> "PUMP"
            dump > pump && dump >= params.minScore -> "DUMP"
            else -> "NONE"
        }
        val score = max(pump, dump)
        val reasons = if (pump >= dump) pReasons else dReasons

        // ---------- چک‌لیست حالت ۹۰٪ (۰ شرط) ----------
        val ultraCount = when (side) {
            "PUMP" -> listOf(
                tDUp,
                t4hUp,
                st.direction > 0,
                rsi in 45.0..68.0,
                macd.macd > macd.signal,
                volRatio >= params.volumeMin,
                breakout || inBullOB,
                price > vp.poc,
                adx >= params.adxMin,
                srsi < 80
            ).count { it }
            "DUMP" -> listOf(
                tDDn,
                t4hDn,
                st.direction < 0,
                rsi in 32.0..55.0,
                macd.macd < macd.signal,
                volRatio >= params.volumeMin,
                breakdown || inBearOB,
                price < vp.poc,
                adx >= params.adxMin,
                srsi > 20
            ).count { it }
            else -> 0
        }

        val ultra = side != "NONE" && ultraCount >= 9
        val golden = side != "NONE" &&
                score >= params.goldenScore &&
                adx >= params.adxMin &&
                volRatio >= params.volumeMin &&
                mtfAligned &&
                ultra

        // ---------- ورود / استاپ / اهداف ----------
        val risk = atr * params.atrMult
        val entry = price
        val stopLoss = if (side == "DUMP") price + risk else price - risk
        val target1 = if (side == "DUMP") price - risk * params.rr else price + risk * params.rr
        val target2 = if (side == "DUMP") price - risk * params.rr * 2 else price + risk * params.rr * 2

        return SignalResult(
            coinId, symbol, name, price, mode,
            pump, dump, side, score, golden, ultra,
            mtfAligned, mtfTrend, adx, rsi, volRatio, funding,
            entry, stopLoss, target1, target2, reasons
        )
    }
}
