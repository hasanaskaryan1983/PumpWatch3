package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** یک کندل برای replay */
data class Bar(val o: Double, val h: Double, val l: Double, val c: Double)

/** نتیجهٔ اجرای یک سیاست خروج روی یک سری کندل */
data class ReplayResult(
    val realizedR: Double,
    val exitReason: String,
    val barsHeld: Int,
    val partialTaken: Boolean = false
)

/**
 * 🚀 Sprint 15 (فاز ۱ / Commit 12): ماشین مقایسهٔ A/B سیاست‌های خروج
 *
 * LEGACY = منطق قبل از Commit 10: تریل ثابت به اندازهٔ ریسک،
 *          BE وقتی قیمت به تارگت۱ رسید، خروج اینترابار روی استاپ/تارگت۲
 * ENGINE  = ExitEngine فعلی: TP1 پله‌ای + تریل پلکانی + آشکارسازهای
 *          تغییر جهت + خروج اضطراری
 *
 * تفاوت صادقانه: LEGACY تارگت را اینترابار (high/low) می‌زند ولی
 * ENGINE تارگت را روی close تأیید می‌کند — محافظه‌کارانه‌تر.
 * همه‌چیز pure و قابل‌تست روی JVM؛ بدون شبکه، بدون state.
 */
object ExitComparator {

    fun replayLegacy(
        bars: List<Bar>, side: String, entry: Double, stop0: Double, t1: Double, t2: Double
    ): ReplayResult {
        val isLong = side == "PUMP"
        val risk = abs(entry - stop0)
        if (risk <= 0.0 || bars.isEmpty()) return ReplayResult(0.0, "NONE", 0)
        var stop = stop0
        for (i in bars.indices) {
            val b = bars[i]
            if (isLong) {
                if (b.l <= stop) return ReplayResult((stop - entry) / risk, "STOP", i + 1)
                if (b.h >= t2) return ReplayResult((t2 - entry) / risk, "TARGET", i + 1)
            } else {
                if (b.h >= stop) return ReplayResult((entry - stop) / risk, "STOP", i + 1)
                if (b.l <= t2) return ReplayResult((entry - t2) / risk, "TARGET", i + 1)
            }
            if (isLong) {
                if (b.c > entry) stop = max(stop, b.c - risk)
                if (b.c >= t1) stop = max(stop, entry)
            } else {
                if (b.c < entry) stop = if (stop <= 0.0) b.c + risk else min(stop, b.c + risk)
                if (b.c <= t1) stop = if (stop <= 0.0) entry else min(stop, entry)
            }
        }
        val last = bars.last().c
        return ReplayResult(
            if (isLong) (last - entry) / risk else (entry - last) / risk,
            "OPEN_END", bars.size
        )
    }

    fun replayEngine(
        bars: List<Bar>, side: String, entry: Double, stop0: Double, t1: Double, t2: Double
    ): ReplayResult {
        val isLong = side == "PUMP"
        val risk = abs(entry - stop0)
        if (risk <= 0.0 || bars.isEmpty()) return ReplayResult(0.0, "NONE", 0)
        var partial = false
        var partialR = 0.0
        var stop = stop0
        val closes = ArrayList<Double>(bars.size)
        for (i in bars.indices) {
            val b = bars[i]
            val stopLevel = if (isLong) max(stop0, stop)
                            else if (stop > 0.0) min(stop0, stop) else stop0
            if (isLong && b.l <= stopLevel) {
                val r = (stopLevel - entry) / risk
                return ReplayResult(mix(partial, partialR, r), "STOP", i + 1, partial)
            }
            if (!isLong && b.h >= stopLevel) {
                val r = (entry - stopLevel) / risk
                return ReplayResult(mix(partial, partialR, r), "STOP", i + 1, partial)
            }
            closes.add(b.c)
            val dec = ExitEngine.decide(
                ExitContext(side, entry, stop0, stop, t2, closes.toList(), b.c, partial)
            )
            when (dec.action) {
                "PARTIAL" -> {
                    partial = true
                    partialR = rOf(isLong, entry, risk, dec.exitPrice ?: b.c)
                }
                "CLOSE" -> {
                    val r = rOf(isLong, entry, risk, dec.exitPrice ?: b.c)
                    return ReplayResult(mix(partial, partialR, r), dec.reason ?: "CLOSE", i + 1, partial)
                }
                "TRAIL" -> if (dec.newStop != null) stop = dec.newStop
                else -> {}
            }
        }
        val r = rOf(isLong, entry, risk, bars.last().c)
        return ReplayResult(mix(partial, partialR, r), "OPEN_END", bars.size, partial)
    }

    private fun rOf(isLong: Boolean, entry: Double, risk: Double, price: Double): Double =
        if (isLong) (price - entry) / risk else (entry - price) / risk

    private fun mix(partial: Boolean, partialR: Double, finalR: Double): Double =
        if (partial) 0.5 * partialR + 0.5 * finalR else finalR
}
