package com.pumpwatch.app.engine

import com.pumpwatch.app.data.CoinMarket
import kotlin.math.abs
import kotlin.math.max

/**
 * 🚀 P3-1 + P3-3: موتور مشترک ارزیابی سیگنال‌های هوشمند.
 *
 * استفاده‌کنندگان:
 *   ۱. SmartAlertsScreen (UI)
 *   ۲. MonitorWorker (پس‌زمینه)
 *
 * یک منبع واحد از حقیقت برای منطق امتیازدهی، تطابق استراتژی و فرمول سفارشی.
 */
object SmartAlertEngine {

    data class AlertEval(
        val coin: CoinMarket,
        val side: String,
        val score: Int,
        val early: Boolean,
        val reasons: List<String>,
        val matchedStrategies: List<String> = emptyList()
    )

    /**
     * ارزیابی یک سکه در برابر الگوهای شتاب/حجم + استراتژی‌های فعال.
     */
    fun evalCoin(c: CoinMarket, strategies: List<CustomStrategy>): AlertEval? {
        val c1 = c.change1h ?: 0.0
        val c24 = c.price_change_percentage_24h ?: 0.0
        val c7 = c.change7d ?: 0.0
        val cap = c.market_cap
        val turnover = if (cap > 0) c.total_volume / cap else 0.0
        val high = c.high24h ?: 0.0
        val low = c.low24h ?: 0.0
        val rangePos = if (high > low) (c.current_price - low) / (high - low) else 0.5

        var pump = 0
        val pr = mutableListOf<String>()
        if (c1 >= 1.0) { pump += 25; pr.add("شتاب ۱ ساعته") }
        if (c1 >= 3.0) pump += 15
        if (c24 in 2.0..35.0) { pump += 20; pr.add("حرکت مثبت ۲۴ ساعته") }
        if (turnover >= 0.15) { pump += 20; pr.add("نسبت حجم به ارزش بازار بالا") }
        if (rangePos >= 0.85) { pump += 20; pr.add("نزدیک به سقف ۲۴ ساعته") }
        if (c7 > 10) pump += 5

        var dump = 0
        val dr = mutableListOf<String>()
        if (c1 <= -1.0) { dump += 25; dr.add("کاهش ۱ ساعته") }
        if (c1 <= -3.0) dump += 15
        if (c24 in -35.0..-2.0) { dump += 20; dr.add("حرکت منفی ۲۴ ساعته") }
        if (turnover >= 0.15) { dump += 20; dr.add("نسبت حجم به ارزش بازار بالا") }
        if (rangePos <= 0.15) { dump += 20; dr.add("نزدیک به کف ۲۴ ساعته") }
        if (c7 < -10) dump += 5

        val side = if (pump >= dump) "PUMP" else "DUMP"
        val score = max(pump, dump).coerceAtMost(100)
        if (score < 35) return null
        val early = abs(c1) >= 1.5 && abs(c24) < 10

        val matched = strategies.filter { it.isEnabled }.filter { s ->
            val scoreMatch = score >= s.minScore
            val changeMatch = if (s.minChange1h < 0) c1 <= s.minChange1h else c1 >= s.minChange1h
            val volMatch = turnover >= s.minVolumeRatio
            scoreMatch && changeMatch && volMatch
        }.map { it.name }

        return AlertEval(c, side, score, early, if (pump >= dump) pr else dr, matched)
    }

    /**
     * آیا این سیگنال ارزش اعلان کردن دارد؟
     */
    fun isAlertWorthy(eval: AlertEval): Boolean {
        return eval.score >= 70 || eval.matchedStrategies.isNotEmpty()
    }

    /**
     * 🚀 P3-3: آیا سیگنال از فیلترهای فرمول سفارشی کاربر عبور می‌کند؟
     *
     * این تابع هم در UI و هم در Worker استفاده می‌شود تا رفتار یکسان باشد.
     */
    fun passesFormula(c: CoinMarket, eval: AlertEval, f: SignalFormula): Boolean {
        if (eval.score < f.minScore) return false

        val c1 = c.change1h ?: 0.0
        if (abs(c1) < f.minChange1h) return false

        val turnover = if (c.market_cap > 0) c.total_volume / c.market_cap else 0.0
        if (turnover < f.minVolumeRatio) return false

        val high = c.high24h ?: 0.0
        val low = c.low24h ?: 0.0
        val rangePos = if (high > low) (c.current_price - low) / (high - low) else 0.5
        when (f.rangeMode) {
            RangeMode.NEAR_HIGH -> if (rangePos < 0.7) return false
            RangeMode.NEAR_LOW -> if (rangePos > 0.3) return false
            RangeMode.ANY -> { }
        }

        when (f.side) {
            FormulaSide.PUMP -> if (eval.side != "PUMP") return false
            FormulaSide.DUMP -> if (eval.side != "DUMP") return false
            FormulaSide.BOTH -> { }
        }

        return true
    }
}
