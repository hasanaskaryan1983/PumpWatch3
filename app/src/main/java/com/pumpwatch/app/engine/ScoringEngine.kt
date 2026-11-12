package com.pumpwatch.app.engine

import com.pumpwatch.app.data.KlineCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * 🚀 Sprint 13 (F6b): موتور امتیازدهی استراتژی — مشترک بین ربات (TradesScreen)
 * و بک‌تست (BacktestEngine)
 *
 * این موتور از TradesScreen.kt استخراج شده تا:
 * - ربات زنده و بک‌تست از یک استراتژی استفاده کنند (نه دو کپی)
 * - موتور بک‌تست بتواند امتیازدهی روزانه را روی تاریخ اجرا کند
 * - تست‌های واحد روی scoring pure قابل نوشتن باشد
 *
 * منطق (مثل TradesScreen قبل از refactor):
 * - EMA50/200 (روند) + MACD (مومنتوم) + RSI (اشباع)
 * - Volume (تأیید) + Weekly EMA10/20 (روند بلندمدت) + OBV (جریان پول)
 * - فقط کندل‌های بسته‌شده استفاده می‌شوند (P0-6 در TradesScreen)
 */
object ScoringEngine {

    /**
     * امتیازدهی یک ارز روی کندل‌های بسته‌شده (بدون look-ahead)
     * @param live اگر true، آخرین کندل (در حال تشکیل) حذف می‌شود (ربات زنده)
     *             اگر false، همهٔ کندل‌ها استفاده می‌شوند (بک‌تست)
     * @return Pair(score [-100..100], atrPct)
     */
    suspend fun score(
        symbol: String,
        live: Boolean = true,
        days: Int = 300
    ): Pair<Int, Double> = withContext(Dispatchers.IO) {
        try {
            val kl = KlineCache.klines(
                "${symbol.uppercase(Locale.US)}USDT", "1d", days
            )
            val candles = if (live) kl.dropLast(1) else kl
            if (candles.size < 200) return@withContext 0 to 12.0
            scoreFromCandles(candles)
        } catch (_: Exception) { 0 to 12.0 }
    }

    /**
     * امتیازدهی روی یک لیست کندل آماده (برای بک‌تست: هر روز امتیاز از پنجرهٔ تاریخ تا آن روز)
     * candles باید شامل ستون‌های Binance باشد:
     * [0] openTime, [1] open, [2] high, [3] low, [4] close, [5] volume, ...
     */
    fun scoreFromCandles(candles: List<com.google.gson.JsonArray>): Pair<Int, Double> {
        if (candles.size < 200) return 0 to 12.0
        val closes = candles.map { it[4].asDouble }
        val vols = candles.map { it[5].asDouble }
        val weekly = weeklyClosesAligned(candles) // 🚀 F9: هفتهٔ تقویمی ISO، مستقل از شروع پنجره

        val price = closes.last()
        val e50 = emaL(closes, 50)
        val e200 = emaL(closes, 200)
        var s = when {
            price > e50 && e50 > e200 -> 50
            price > e50 -> 25
            price < e50 && e50 < e200 -> -50
            else -> -25
        }
        s += if (macdU(closes)) 20 else -20
        val r = rsi(closes)
        s += when {
            r in 45.0..65.0 -> 15
            r < 35 -> 20
            r > 75 -> -25
            else -> 5
        }
        if (vols.size > 40) {
            val rec = vols.takeLast(20).average()
            val prior = vols.dropLast(20).takeLast(20).average()
            if (prior > 0 && rec > prior * 1.2) s += 10
        }
        if (weekly.size >= 25) {
            val w = weekly.last()
            val e10 = emaL(weekly, 10)
            val e20 = emaL(weekly, 20)
            s += when {
                w > e10 && e10 > e20 -> 15
                w > e10 -> 8
                w < e10 && e10 < e20 -> -20
                else -> -8
            }
        }
        s += obvDeltaScore(closes, vols) // 🚀 F9: OBV تفاضلی نرمال‌شده (ضد باگ مقایسهٔ ضربی روی مقدار منفی)
        s = s.coerceIn(-100, 100)

        val atrPct = atrPctTrueRange(candles, price) // 🚀 F9: True Range کامل (high/low/gap)
        return s to atrPct
    }

    // 🚀 F9: هفتهٔ تقویمی ISO (دوشنبه→یکشنبه، UTC)؛ کلید هفته از openTime ساخته می‌شود نه از اندیس آرایه
    internal fun weeklyClosesAligned(candles: List<com.google.gson.JsonArray>): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val dayMs = 86_400_000L
        val weekMs = 7 * dayMs
        val out = mutableListOf<Double>()
        var currentKey = Long.MIN_VALUE
        var lastClose = 0.0
        for (c in candles) {
            val key = Math.floorDiv(c[0].asLong + 3 * dayMs, weekMs) // epoch=پنجشنبه؛ +3 روز → مرز دوشنبه
            val close = c[4].asDouble
            if (key != currentKey) {
                if (currentKey != Long.MIN_VALUE) out.add(lastClose)
                currentKey = key
            }
            lastClose = close
        }
        out.add(lastClose)
        return out
    }

    // 🚀 F9: OBV تفاضلی در پنجرهٔ ۲۰ روزه، نرمال‌شده به حجم همان پنجره (علامت‌دار و ایمن)
    internal fun obvDeltaScore(closes: List<Double>, vols: List<Double>): Int {
        if (closes.size < 30 || vols.size != closes.size) return 0
        var obv = 0.0
        val ser = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            obv += when {
                closes[i] > closes[i - 1] -> vols[i]
                closes[i] < closes[i - 1] -> -vols[i]
                else -> 0.0
            }
            ser.add(obv)
        }
        if (ser.size < 21) return 0
        val delta = ser.last() - ser[ser.size - 21]
        val volScale = vols.takeLast(20).sum().coerceAtLeast(1e-9)
        val rel = delta / volScale
        return when {
            rel > 0.05 -> 10
            rel > 0.0 -> 5
            rel < -0.05 -> -10
            else -> -5
        }
    }

    // 🚀 F9: ATR = میانگین True Range کامل روی ۱۴ کندل آخر (شامل gap و high/low)
    internal fun atrPctTrueRange(candles: List<com.google.gson.JsonArray>, price: Double): Double {
        if (candles.size < 15 || price <= 0) return 12.0
        var sum = 0.0
        for (i in candles.size - 14 until candles.size) {
            val high = candles[i][2].asDouble
            val low = candles[i][3].asDouble
            val prevClose = candles[i - 1][4].asDouble
            sum += maxOf(high - low, abs(high - prevClose), abs(low - prevClose))
        }
        return (sum / 14) / price * 100
    }

    // ---------- helpers pure (منتقل‌شده از TradesScreen) ----------

    fun emaL(d: List<Double>, p: Int): Double {
        if (d.size < p) return d.lastOrNull() ?: 0.0
        val k = 2.0 / (p + 1)
        var e = d.take(p).average()
        for (i in p until d.size) e = d[i] * k + e * (1 - k)
        return e
    }

    fun rsi(d: List<Double>, p: Int = 14): Double {
        if (d.size <= p) return 50.0
        var g = 0.0; var l = 0.0
        for (i in 1..p) {
            val x = d[i] - d[i - 1]
            if (x > 0) g += x else l -= x
        }
        var ag = g / p
        var al = l / p
        for (i in p + 1 until d.size) {
            val x = d[i] - d[i - 1]
            ag = (ag * (p - 1) + maxOf(x, 0.0)) / p
            al = (al * (p - 1) + maxOf(-x, 0.0)) / p
        }
        return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
    }

    fun macdU(d: List<Double>): Boolean {
        if (d.size < 35) return false
        val p = d.dropLast(1)
        return (emaL(d, 12) - emaL(d, 26)) > (emaL(p, 12) - emaL(p, 26))
    }
}
