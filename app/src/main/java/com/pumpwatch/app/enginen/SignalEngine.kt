package com.pumpwatch.app.engine

// ---------- پارامترهای قابل بهینه‌سازی ----------

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

// ---------- نتیجه سیگنال ----------

data class SignalResult(
    val coinId: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val mode: String,              // "SPOT" | "FUT"
    val pumpScore: Int,
    val dumpScore: Int,
    val side: String,              // "PUMP" | "DUMP" | "NONE"
    val score: Int,
    val golden: Boolean,
    val mtfAligned: Boolean,
    val mtfTrend: String,          // "UP" | "DOWN" | "MIX"
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

// ---------- موتور سیگنال ----------

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
        if (candles1h.size < 60) return null

        val closes = Indicators.closes(candles1h)
        val volumes = candles1h.map { it.volume }
        val last = candles1h.last()
        val price = last.close

        // ----- اندیکاتورهای 1H -----
        val ema20 = Indicators.emaLast(closes, 20)
        val ema50 = Indicators.emaLast(closes, 50)
        val ema200 = Indicators.emaLast(closes, 200)
        val ema20Series = Indicators.emaSeries(closes, 20)
        val ema20Rising = ema20Series.size > 3 &&
                ema20Series.last() > ema20Series[ema20Series.size - 4]

        val rsi = Indicators.rsi(closes, params.rsiPeriod)
        val rsiPrev = Indicators.rsi(closes.dropLast(1), params.rsiPeriod)
        val macd = Indicators.macd(closes)
        val macdPrev = Indicators.macd(closes.dropLast(1))
        val adx = Indicators.adx(candles1h)
        val volRatio = Indicators.volumeRatio(volumes)
        val atrVal = Indicators.atr(candles1h)

        // ----- شکست مقاومت / حمایت -----
        val lookback = params.breakoutLookback
        val prev = candles1h.dropLast(1).takeLast(lookback)
        val prevHigh = prev.maxOf { it.high }
        val prevLow = prev.minOf { it.low }
        val breakout = price > prevHigh
        val breakdown = price < prevLow

        // ----- فشردگی بولینگر -----
        val bbNow = Indicators.bollinger(closes)
        val bbPrev = Indicators.bollinger(closes.dropLast(24))
        val squeeze = bbPrev.widthPct > 0 && bbNow.widthPct < bbPrev.widthPct * 0.75

        // ----- MTF: تایم‌فریم 4H و Daily -----
        val candles4h = Indicators.aggregate(candles1h, 4)
        val candlesD = Indicators.aggregate(candles1h, 24)
        val trend1 = trendOf(candles1h)
        val trend4 = trendOf(candles4h)
        val trendD = trendOf(candlesD)
        val mtfUp = trend1 == "UP" && trend4 == "UP" && trendD == "UP"
        val mtfDown = trend1 == "DOWN" && trend4 == "DOWN" && trendD == "DOWN"
        val mtfTrend = if (mtfUp) "UP" else if (mtfDown) "DOWN" else "MIX"

        // ================= امتیاز پامپ =================
        var pump = 0
        val pumpReasons = mutableListOf<String>()

        if (price > ema20 && price > ema50) { pump += 8; pumpReasons.add("قیمت بالای EMA20 و EMA50") }
        if (ema20 > ema50) { pump += 6; pumpReasons.add("EMA20 بالای EMA50 (روند صعودی)") }
        if (price > ema200) { pump += 4; pumpReasons.add("قیمت بالای EMA200") }
        if (ema20Rising) { pump += 2; pumpReasons.add("شیب EMA20 صعودی") }

        if (rsi in 50.0..68.0) { pump += 8; pumpReasons.add("RSI در منطقه قدرت (${fmt(rsi)})") }
        if (rsiPrev < 50 && rsi >= 50) { pump += 5; pumpReasons.add("شکست سطح ۵۰ توسط RSI") }
        if (macd.macd > macd.signal) { pump += 5; pumpReasons.add("MACD بالای خط سیگنال") }
        if (macd.histogram > macdPrev.histogram) { pump += 2; pumpReasons.add("هیستوگرام MACD صعودی") }

        if (volRatio >= 3.0) { pump += 20; pumpReasons.add("حجم انفجاری (${fmt(volRatio)}x)") }
        else if (volRatio >= 2.0) { pump += 15; pumpReasons.add("حجم غیرعادی (${fmt(volRatio)}x)") }
        else if (volRatio >= 1.5) { pump += 10; pumpReasons.add("حجم بالاتر از میانگین") }

        if (breakout) {
            pump += 12; pumpReasons.add("شکست مقاومت ${lookback} کندلی")
            if (volRatio >= 1.5) { pump += 5; pumpReasons.add("شکست با حجم بالا") }
            pump += 3
        }

        if (squeeze && volRatio >= 1.2) { pump += 10; pumpReasons.add("فشردگی بولینگر + شروع حرکت") }
        else if (squeeze) { pump += 5; pumpReasons.add("فشردگی بولینگر (آماده انفجار)") }

        // ================= امتیاز دامپ =================
        var dump = 0
        val dumpReasons = mutableListOf<String>()

        if (breakdown) { dump += 25; dumpReasons.add("شکست حمایت ${lookback} کندلی") }
        if (volRatio >= 2.0 && last.close < last.open) { dump += 25; dumpReasons.add("حجم فروش سنگین") }
        else if (volRatio >= 1.5 && last.close < last.open) { dump += 15; dumpReasons.add("فشار فروش با حجم") }
        if (rsi < 45) { dump += 15; dumpReasons.add("RSI ضعیف (${fmt(rsi)})") }
        if (macd.macd < macd.signal) { dump += 15; dumpReasons.add("MACD زیر خط سیگنال") }
        if (price < ema20 && price < ema50) { dump += 15; dumpReasons.add("قیمت زیر EMA20 و EMA50") }
        if (price < ema200) { dump += 5; dumpReasons.add("قیمت زیر EMA200") }

        // ================= تنظیمات فیوچرز =================
        if (mode == "FUT" && funding != null) {
            if (funding <= -0.0005) { pump += 5; pumpReasons.add("فاندینگ منفی شدید → پتانسیل شورت‌اسکوییز") }
            if (funding >= 0.0005) { dump += 5; dumpReasons.add("فاندینگ مثبت شدید → بازار اشباع") }
        }

        pump = pump.coerceIn(0, 100)
        dump = dump.coerceIn(0, 100)

        // ================= تصمیم نهایی =================
        val side = when {
            pump >= dump && pump >= params.minScore -> "PUMP"
            dump > pump && dump >= params.minScore -> "DUMP"
            else -> "NONE"
        }
        val score = maxOf(pump, dump)
        val reasons = if (pump >= dump) pumpReasons else dumpReasons

        val golden = score >= params.goldenScore &&
                adx >= params.adxMin &&
                volRatio >= params.volumeMin &&
                ((side == "PUMP" && mtfUp) || (side == "DUMP" && mtfDown))

        // ================= ورود / هدف / استاپ =================
        val risk = atrVal * params.atrMult
        val (sl, t1, t2) = if (side == "DUMP") {
            Triple(price + risk, price - risk * params.rr, price - risk * params.rr * 2)
        } else {
            Triple(price - risk, price + risk * params.rr, price + risk * params.rr * 2)
        }

        return SignalResult(
            coinId = coinId,
            symbol = symbol,
            name = name,
            price = price,
            mode = mode,
            pumpScore = pump,
            dumpScore = dump,
            side = side,
            score = score,
            golden = golden,
            mtfAligned = mtfUp || mtfDown,
            mtfTrend = mtfTrend,
            adx = adx,
            rsi = rsi,
            volumeRatio = volRatio,
            funding = funding,
            entry = price,
            stopLoss = sl,
            target1 = t1,
            target2 = t2,
            reasons = reasons
        )
    }

    // ---------- روند یک تایم‌فریم ----------

    private fun trendOf(candles: List<Candle>): String {
        if (candles.size < 60) return "MIX"
        val closes = Indicators.closes(candles)
        val e20 = Indicators.emaLast(closes, 20)
        val e50 = Indicators.emaLast(closes, 50)
        val st = Indicators.supertrend(candles)
        return when {
            e20 > e50 && st.direction == 1 -> "UP"
            e20 < e50 && st.direction == -1 -> "DOWN"
            else -> "MIX"
        }
    }

    private fun fmt(d: Double): String = String.format(java.util.Locale.US, "%.1f", d)
}
