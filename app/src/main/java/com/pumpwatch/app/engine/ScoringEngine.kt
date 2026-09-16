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
        val weekly = closes.chunked(7).map { it.last() }

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
        if (closes.size >= 30) {
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
            if (ser.size >= 21) {
                val now = ser.last()
                val past = ser[ser.size - 21]
                s += when {
                    now > past * 1.05 -> 10
                    now > past -> 5
                    now < past * 0.95 -> -10
                    else -> -5
                }
            }
        }
        s = s.coerceIn(-100, 100)

        var atr = 0.0
        if (closes.size >= 15) {
            for (i in closes.size - 14 until closes.size) {
                atr += abs(closes[i] - closes[i - 1])
            }
            atr /= 14
        }
        val atrPct = if (price > 0) atr / price * 100 else 12.0
        return s to atrPct
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
