package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 🚀 Sprint 15 (Commit 11): موتور خروج با Take-profit پله‌ای
 *
 * Priority: STOP -> TARGET -> VOL_SPIKE -> PARTIAL (TP1) -> MOMENTUM_RSI -> TIME -> TRAIL
 *
 * PARTIAL: اگر profitR >= 1.0 و هنوز partial نشده، نیمی از معامله بسته می‌شود.
 * اگر قبل از +1R اسپایک شود، کل معامله بسته می‌شود (VOL_SPIKE اولویت بالاتر).
 */

data class ExitContext(
    val side: String,
    val entry: Double,
    val initialStop: Double,
    val currentStop: Double,
    val target: Double?,
    val closes: List<Double>,
    val livePrice: Double,
    val partialClose: Boolean = false   // 🚀 Commit 11: آیا نیمی قبلاً بسته شده؟
)

data class ExitDecision(
    val action: String,                 // HOLD | TRAIL | PARTIAL | CLOSE
    val newStop: Double?,
    val exitPrice: Double?,
    val reason: String?                 // STOP | TARGET | VOL_SPIKE | TP1 | MOMENTUM_RSI | TIME | TRAIL | TIGHTEN
)

object ExitEngine {

    private const val MIN_BARS = 20
    private const val MAX_BARS = 24
    private const val VOL_SPIKE_ATR = 2.5
    private const val RSI_LOSS = 50.0
    private const val RSI_WAS = 55.0

    fun decide(ctx: ExitContext): ExitDecision {
        val isLong = ctx.side == "PUMP"
        val risk = abs(ctx.entry - ctx.initialStop)
        if (risk <= 0.0 || ctx.entry <= 0.0) return ExitDecision("HOLD", null, null, null)

        // ۱) استاپ سخت
        val stopLevel = if (isLong) max(ctx.initialStop, ctx.currentStop)
        else if (ctx.currentStop > 0.0) min(ctx.initialStop, ctx.currentStop)
        else ctx.initialStop
        if (isLong && ctx.livePrice <= stopLevel) return ExitDecision("CLOSE", stopLevel, stopLevel, "STOP")
        if (!isLong && ctx.livePrice >= stopLevel) return ExitDecision("CLOSE", stopLevel, stopLevel, "STOP")

        // ۲) تارگت
        val tg = ctx.target
        if (tg != null && tg > 0.0) {
            if (isLong && ctx.livePrice >= tg) return ExitDecision("CLOSE", null, tg, "TARGET")
            if (!isLong && ctx.livePrice <= tg) return ExitDecision("CLOSE", null, tg, "TARGET")
        }

        val atr = atrEstimate(ctx.closes)
        val profitR = if (isLong) (ctx.livePrice - ctx.entry) / risk
        else (ctx.entry - ctx.livePrice) / risk

        // ۳) اسپایک نوسان (اولویت بالاتر از PARTIAL: کل ترید بسته می‌شود)
        val lastClose = ctx.closes.lastOrNull() ?: ctx.livePrice
        val move = if (isLong) ctx.livePrice - lastClose else lastClose - ctx.livePrice
        if (atr > 0.0 && -move > VOL_SPIKE_ATR * atr) {
            return ExitDecision("CLOSE", null, ctx.livePrice, "VOL_SPIKE")
        }

        // ۴) 🚀 Commit 11: PARTIAL در +1R (اگر هنوز partial نشده)
        if (profitR >= 1.0 && !ctx.partialClose) {
            return ExitDecision("PARTIAL", null, ctx.livePrice, "TP1")
        }

        if (ctx.closes.size < MIN_BARS) return ExitDecision("HOLD", null, null, null)

        // ۵) آشکارسازهای تغییر جهت
        val e9 = ema(ctx.closes, 9)
        val rsi = rsi(ctx.closes, 14)
        val againstTrend = if (isLong) ctx.closes.last() < e9.last() else ctx.closes.last() > e9.last()
        val rsiLost = if (isLong) rsiFellBelow(rsi, RSI_LOSS, RSI_WAS) else rsiRoseAbove(rsi, RSI_LOSS, RSI_WAS)

        if (againstTrend && rsiLost) {
            return ExitDecision("CLOSE", null, ctx.livePrice, "MOMENTUM_RSI")
        }

        // ۶) توقف زمانی
        if (ctx.closes.size >= MAX_BARS && profitR < 1.0) {
            return ExitDecision("CLOSE", null, ctx.livePrice, "TIME")
        }

        // ۷) نردبان تریل
        var stop = stopLevel
        if (profitR >= 1.0) {
            stop = if (isLong) max(stop, ctx.entry) else min(stop, ctx.entry)
        }
        val k = when {
            profitR >= 3.0 -> 1.5
            profitR >= 2.0 -> 2.0
            profitR >= 1.0 -> 2.5
            againstTrend || rsiLost -> 2.5
            else -> null
        }
        if (k != null && atr > 0.0) {
            val extreme = if (isLong) max(ctx.closes.maxOrNull() ?: ctx.entry, ctx.livePrice)
            else min(ctx.closes.minOrNull() ?: ctx.entry, ctx.livePrice)
            val chandelier = if (isLong) extreme - k * atr else extreme + k * atr
            stop = if (isLong) max(stop, chandelier) else min(stop, chandelier)
        }

        val reason = if (againstTrend || rsiLost) "TIGHTEN" else "TRAIL"
        return ExitDecision("TRAIL", stop, null, reason)
    }

    internal fun atrEstimate(closes: List<Double>): Double {
        if (closes.size < 2) return 0.0
        val n = min(14, closes.size - 1)
        var sum = 0.0
        for (i in closes.size - n until closes.size) sum += abs(closes[i] - closes[i - 1])
        return sum / n
    }

    internal fun ema(v: List<Double>, p: Int): List<Double> {
        if (v.isEmpty()) return emptyList()
        val k = 2.0 / (p + 1)
        val out = ArrayList<Double>(v.size)
        var sum = 0.0
        for (i in v.indices) {
            if (i < p) {
                sum += v[i]
                out.add(sum / (i + 1))
            } else {
                out.add(v[i] * k + out[i - 1] * (1 - k))
            }
        }
        return out
    }

    internal fun rsi(v: List<Double>, p: Int = 14): List<Double> {
        if (v.size <= p) return List(v.size) { 50.0 }
        val out = ArrayList<Double>(v.size)
        for (i in 0 until p) out.add(50.0)
        var g = 0.0
        var l = 0.0
        for (i in 1..p) {
            val d = v[i] - v[i - 1]
            if (d > 0) g += d else l -= d
        }
        var ag = g / p
        var al = l / p
        out.add(if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al))
        for (i in p + 1 until v.size) {
            val d = v[i] - v[i - 1]
            ag = (ag * (p - 1) + maxOf(d, 0.0)) / p
            al = (al * (p - 1) + maxOf(-d, 0.0)) / p
            out.add(if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al))
        }
        return out
    }

    internal fun rsiFellBelow(rsi: List<Double>, loss: Double, was: Double): Boolean {
        if (rsi.size < 2) return false
        if (rsi.last() >= loss) return false
        return (rsi.takeLast(6).maxOrNull() ?: 0.0) >= was
    }

    internal fun rsiRoseAbove(rsi: List<Double>, loss: Double, was: Double): Boolean {
        if (rsi.size < 2) return false
        if (rsi.last() <= loss) return false
        return (rsi.takeLast(6).minOrNull() ?: 100.0) <= loss
    }
}
