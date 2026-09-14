package com.pumpwatch.app.engine

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class UnifiedSignalParams(
    val rsiPeriod: Int = 14,
    val adxMin: Double = 25.0,
    val volumeMin: Double = 1.5,
    val breakoutLookback: Int = 20,
    val minScore: Int = 70,
    val goldenScore: Int = 85,
    val atrMult: Double = 1.5,
    val rr: Double = 1.5
)

data class UnifiedSignalResult(
    val coinId: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val mode: String, // "SPOT" or "FUT"
    val side: String, // "PUMP", "DUMP", "NONE"
    val score: Int,
    val golden: Boolean,
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
    val reasons: List<String>,
    // P0-6: زمان بسته شدن آخرین کندلی که سیگنال بر اساس آن صادر شده (epoch millis).
    // برای شفافیت در UI/لاگ و جلوگیری از سیگنال‌های «کندل ناتمام».
    val candleCloseTs: Long = 0L
)

object UnifiedSignalEngine {

    /**
     * تحلیل سیگنال قطعی.
     *
     * 🟢 P0-6 — سیگنال فقط با کندل بسته:
     *  - آخرین کندل ورودی (candles1h.last()) همیشه "در حال تشکیل" است
     *    چون klines آن را برمی‌گرداند قبل از بسته شدن.
     *  - این تابع به‌طور خودکار آخرین کندل را حذف می‌کند و تحلیل را
     *    **فقط روی کندل‌های بسته‌شده** انجام می‌دهد.
     *  - بنابراین سیگنال قطعی فقط زمانی صادر می‌شود که یک کندل کامل بسته شده باشد.
     *  - `candleCloseTs` در نتیجه = زمان بسته شدن آن کندل؛ اگر صفر بود
     *    (مثلاً مصرف‌کننده time را پر نکرده)، از System.currentTimeMillis استفاده می‌شود.
     *
     * مصرف‌کننده‌ها (QuickScanner، BatchScanner، SpotEngine، TradesScreen) نیازی
     * به تغییر ندارند — این تابع به‌طور خودکار آخرین ردیف را کنار می‌گذارد.
     */
    fun analyze(
        coinId: String,
        symbol: String,
        name: String,
        candles1h: List<Candle>,
        mode: String,
        funding: Double? = null,
        params: UnifiedSignalParams = UnifiedSignalParams()
    ): UnifiedSignalResult? {
        // P0-6: حذف کندلِ در حال تشکیل — سیگنال قطعی فقط پس از close
        if (candles1h.size < 61) return null
        val closed = candles1h.dropLast(1)
        if (closed.size < 60) return null

        // timestamp بسته شدن آخرین کندل معتبر
        val lastClosedTime = closed.last().time
        val candleCloseTs = if (lastClosedTime > 0L) lastClosedTime else System.currentTimeMillis()

        val closes = Indicators.closes(closed)
        val volumes = closed.map { it.volume }
        val price = closes.last()  // حالا آخرین close بسته‌شده است (نه کندل forming)

        // ۱. لایه تشخیص رژیم بازار (Daily/4H) - الهام گرفته از SpotEngine
        val c4h = Indicators.aggregate(closed, 4)
        val cD = Indicators.aggregate(closed, 24)
        val closes4h = Indicators.closes(c4h)
        val closesD = Indicators.closes(cD)

        val t1hUp = Indicators.emaLast(closes, 20) > Indicators.emaLast(closes, 50) && Indicators.supertrend(closed).direction > 0
        val t1hDn = Indicators.emaLast(closes, 20) < Indicators.emaLast(closes, 50) && Indicators.supertrend(closed).direction < 0

        val t4hUp = closes4h.size >= 60 && Indicators.emaLast(closes4h, 50) > Indicators.emaLast(closes4h, 200)
        val t4hDn = closes4h.size >= 60 && Indicators.emaLast(closes4h, 50) < Indicators.emaLast(closes4h, 200)

        val tDUp = closesD.size >= 60 && Indicators.emaLast(closesD, 50) > Indicators.emaLast(closesD, 200)
        val tDDn = closesD.size >= 60 && Indicators.emaLast(closesD, 50) < Indicators.emaLast(closesD, 200)

        val mtfUp = t1hUp && t4hUp && tDUp
        val mtfDn = t1hDn && t4hDn && tDDn
        val mtfAligned = mtfUp || mtfDn
        val mtfTrend = if (mtfUp) "UP" else if (mtfDn) "DOWN" else "MIX"

        // ۲. لایه تشخیص Setup و اندیکاتورها — همه روی closed
        val rsi = Indicators.rsi(closes, params.rsiPeriod)
        val adx = Indicators.adx(closed)
        val atr = Indicators.atr(closed)
        val volRatio = Indicators.volumeRatio(volumes)
        val macd = Indicators.macd(closes)
        val macdPrev = Indicators.macd(closes.dropLast(1))
        val st = Indicators.supertrend(closed)

        val lookback = params.breakoutLookback
        val prevHigh = closed.takeLast(lookback).maxOf { it.high }
        val prevLow = closed.takeLast(lookback).minOf { it.low }
        val breakout = price > prevHigh
        val breakdown = price < prevLow

        // ۳. لایه امتیازدهی ساختاریافته (مطابق گزارش PDF)
        var score = 0
        val reasons = mutableListOf<String>()

        if (mtfAligned) {
            score += 30
            reasons.add("هم‌جهتی ۳ تایم‌فریم (Daily/4H/1H)")
        } else {
            if (t4hUp || tDUp) { score += 15; reasons.add("روند بلندمدت صعودی") }
            if (t4hDn || tDDn) { score += 15; reasons.add("روند بلندمدت نزولی") }
        }

        val isBullishSetup = breakout && volRatio >= params.volumeMin && rsi in 50.0..70.0
        val isBearishSetup = breakdown && volRatio >= params.volumeMin && rsi in 30.0..50.0

        if (isBullishSetup) {
            score += 20
            reasons.add("Setup شکست مقاومت با حجم")
        } else if (isBearishSetup) {
            score += 20
            reasons.add("Setup شکست حمایت با حجم")
        }

        if (volRatio >= params.volumeMin) {
            score += 15
            reasons.add("حجم ${String.format(Locale.US, "%.1f", volRatio)}x میانگین")
        }

        val macdUp = macd.macd > macd.signal && macd.histogram > macdPrev.histogram
        val macdDn = macd.macd < macd.signal && macd.histogram < macdPrev.histogram

        if (isBullishSetup && macdUp) {
            score += 10
            reasons.add("تأیید مومنتوم MACD صعودی")
        } else if (isBearishSetup && macdDn) {
            score += 10
            reasons.add("تأیید مومنتوم MACD نزولی")
        }

        if (adx >= params.adxMin) {
            score += 10
            reasons.add("قدرت روند تأییدشده (ADX)")
        }

        if (funding != null) {
            if (isBullishSetup && funding <= -0.0003) { score += 10; reasons.add("فاندینگ منفی (فرصت Long)") }
            if (isBearishSetup && funding >= 0.0005) { score += 10; reasons.add("فاندینگ مثبت شدید (فرصت Short)") }
        }

        score = min(100, score)
        val side = when {
            isBullishSetup && score >= params.minScore -> "PUMP"
            isBearishSetup && score >= params.minScore -> "DUMP"
            else -> "NONE"
        }

        val golden = score >= params.goldenScore && mtfAligned && adx >= params.adxMin

        // ۴. مدیریت ریسک و خروجی
        val risk = atr * params.atrMult
        val entry = price  // آخرین close بسته‌شده
        val stopLoss = if (side == "DUMP") price + risk else price - risk
        val target1 = if (side == "DUMP") price - risk * params.rr else price + risk * params.rr
        val target2 = if (side == "DUMP") price - risk * params.rr * 2.0 else price + risk * params.rr * 2.0

        return UnifiedSignalResult(
            coinId, symbol, name, price, mode, side, score, golden,
            mtfAligned, mtfTrend, adx, rsi, volRatio, funding,
            entry, stopLoss, target1, target2, reasons,
            candleCloseTs = candleCloseTs
        )
    }
}
