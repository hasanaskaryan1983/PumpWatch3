package com.pumpwatch.app.engine

import com.pumpwatch.app.data.FuturesCandle
import kotlin.math.abs

/**
 * 🚀 Sprint 12 (F3): موتور اسکنر فیوچرز — pure و قابل تست
 *
 * استراتژی (همان که در placeholder توضیح داده شده بود):
 * - Regime: 4H (ساختار کلان) + 1H (هم‌راستایی)
 * - Setup: 15M (پولبک به EMA20 یا Breakout-Retest)
 * - Trigger: 5M (کندل هم‌جهت)
 * - استاپ: ۲ × ATR(15M) • تارگت: ۳ × ATR
 *
 * قوانین صداقت:
 * - Regime RANGE → هیچ سیگنالی نیست (نه لانگ نه شورت)
 * - Setup NONE → رد می‌شود
 * - Trigger نیامده → سیگنال «در انتظار» می‌ماند، نه «فعال»
 */
object FuturesScannerEngine {

    enum class Regime { BULL, BEAR, RANGE }
    enum class Setup { PULLBACK, BREAKOUT_RETEST, NONE }

    data class ScanResult(
        val symbol: String,
        val base: String,
        val direction: String,          // "LONG" | "SHORT"
        val score: Int,
        val regime4h: Regime,
        val regime1h: Regime,
        val setup15m: Setup,
        val trigger5m: Boolean,
        val entry: Double,
        val stop: Double,
        val target: Double,
        val atr15m: Double,
        val fundingPct: Double?,
        val reasons: List<String>
    )

    /** EMA ساده — seed با اولین مقدار (تقریب استاندارد برای دادهٔ محدود) */
    fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values.first()
        out.add(prev)
        for (i in 1 until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out.add(prev)
        }
        return out
    }

    /** Regime یک تایم‌فریم: رابطهٔ EMA50/EMA200 + قیمت */
    fun regimeOf(candles: List<FuturesCandle>): Regime {
        val closes = candles.map { it.close }
        if (closes.size < 60) return Regime.RANGE
        val e50 = ema(closes, 50).last()
        val e200 = ema(closes, minOf(200, closes.size)).last()
        val last = closes.last()
        return when {
            e50 > e200 && last > e50 -> Regime.BULL
            e50 < e200 && last < e50 -> Regime.BEAR
            else -> Regime.RANGE
        }
    }

    /** Setup روی 15M بر اساس جهت regime */
    fun setupOf(candles: List<FuturesCandle>, regime: Regime): Setup {
        val closes = candles.map { it.close }
        if (closes.size < 40) return Setup.NONE
        val e20 = ema(closes, 20)
        val last = closes.last()
        val prev = closes[closes.size - 2]
        val e20Last = e20.last()
        val e20Prev = e20[e20.size - 2]

        val window = closes.takeLast(33).dropLast(3)
        if (window.isEmpty()) return Setup.NONE
        val hi = window.max()
        val lo = window.min()

        return when (regime) {
            Regime.BULL -> when {
                prev <= e20Prev * 1.002 && last > e20Last -> Setup.PULLBACK
                last > hi && prev <= hi * 1.005 -> Setup.BREAKOUT_RETEST
                else -> Setup.NONE
            }
            Regime.BEAR -> when {
                prev >= e20Prev * 0.998 && last < e20Last -> Setup.PULLBACK
                last < lo && prev >= lo * 0.995 -> Setup.BREAKOUT_RETEST
                else -> Setup.NONE
            }
            Regime.RANGE -> Setup.NONE
        }
    }

    /** Trigger روی 5M: سه کندل هم‌جهت با جهت سیگنال */
    fun triggerOf(candles5m: List<FuturesCandle>, direction: String): Boolean {
        if (candles5m.size < 3) return false
        val last3 = candles5m.takeLast(3)
        return if (direction == "LONG") last3.all { it.close >= it.open }
        else last3.all { it.close <= it.open }
    }

    /** ATR ساده (میانگین TR های اخیر) */
    fun atr(candles: List<FuturesCandle>, period: Int = 14): Double {
        if (candles.size < period + 1) return 0.0
        val trs = candles.zipWithNext { a, b ->
            maxOf(
                b.high - b.low,
                abs(b.high - a.close),
                abs(b.low - a.close)
            )
        }
        return trs.takeLast(period).average()
    }

    /**
     * تحلیل کامل یک کاندید (فاز A — بدون trigger)
     * null یعنی: regime خنثی یا setup تشکیل نشده → صادقانه هیچ سیگنالی نیست
     */
    fun analyze(
        symbol: String,
        base: String,
        c4h: List<FuturesCandle>,
        c1h: List<FuturesCandle>,
        c15m: List<FuturesCandle>,
        fundingPct: Double?
    ): ScanResult? {
        val r4 = regimeOf(c4h)
        if (r4 == Regime.RANGE) return null
        val r1 = regimeOf(c1h)

        val direction = if (r4 == Regime.BULL) "LONG" else "SHORT"
        val setup = setupOf(c15m, r4)
        if (setup == Setup.NONE) return null

        var score = 40
        val reasons = mutableListOf<String>()
        reasons.add(if (r4 == Regime.BULL) "Regime 4H: صعودی 🟢" else "Regime 4H: نزولی 🔴")
        if (r1 == r4) { score += 15; reasons.add("Regime 1H: هم‌راستا ✅") }
        else reasons.add("Regime 1H: خنثی/مخالف ⚠️")

        when (setup) {
            Setup.PULLBACK -> { score += 25; reasons.add("Setup 15M: پولبک به EMA20 ") }
            Setup.BREAKOUT_RETEST -> { score += 20; reasons.add("Setup 15M: شکست-بازآزمایی 🎯") }
            Setup.NONE -> { }
        }

        val entry = c15m.lastOrNull()?.close ?: return null
        val a = atr(c15m)
        if (a <= 0.0) return null
        val stop = if (direction == "LONG") entry - 2 * a else entry + 2 * a
        val target = if (direction == "LONG") entry + 3 * a else entry - 3 * a

        return ScanResult(
            symbol = symbol,
            base = base,
            direction = direction,
            score = score.coerceAtMost(100),
            regime4h = r4,
            regime1h = r1,
            setup15m = setup,
            trigger5m = false,
            entry = entry,
            stop = stop,
            target = target,
            atr15m = a,
            fundingPct = fundingPct,
            reasons = reasons
        )
    }

    /** فاز B: اعمال trigger 5M روی نتیجهٔ فاز A */
    fun applyTrigger(r: ScanResult, c5m: List<FuturesCandle>): ScanResult {
        val trig = triggerOf(c5m, r.direction)
        val reasons = r.reasons.toMutableList()
        var score = r.score
        if (trig) { score += 20; reasons.add("Trigger 5M: کندل هم‌جهت ✅") }
        else reasons.add("Trigger 5M: در انتظار ⏳")
        return r.copy(
            trigger5m = trig,
            score = score.coerceAtMost(100),
            reasons = reasons
        )
    }
}
