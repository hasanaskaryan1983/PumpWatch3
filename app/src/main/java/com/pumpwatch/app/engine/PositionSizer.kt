package com.pumpwatch.app.engine

import java.util.Locale
import kotlin.math.abs

/**
 * 🚀 Commit 120: سایز پوزیشن بر اساس Kelly Criterion.
 *
 * چرا Kelly؟
 * - تنها فرمولی است که بهینهٔ رشد بلندمدت سرمایه را می‌دهد
 * - اگر edge نداشته باشی (kelly <= 0)، خودش می‌گوید سایز کوچک
 *
 * چرا نیم‌Kelly؟
 * - Kelly کامل در عمل بیش‌ازحد تهاجمی است (خطای تخمین W و R)
 * - نیم‌Kelly تقریباً همان رشد را با نصف نوسان می‌دهد
 *
 * گاردهای ایمنی:
 * - حداقل ۲۰ ترید بسته لازم است؛ وگرنه سایز پیش‌فرض ۵٪
 * - سقف full Kelly = ۲۵٪ • سقف مصرف = ۱۰٪ سرمایه per trade
 * - کف سایز = ۱۰$ • هرگز بیشتر از کل سرمایه
 * - اگر هنوز ضرری ثبت نشده، به «بدون ضرر» اعتماد نمی‌کنیم (نیمهٔ سقف)
 */
object PositionSizer {

    const val MIN_TRADES = 20
    const val MAX_KELLY = 0.25
    const val MAX_CAPITAL_PCT = 0.10
    const val MIN_SIZE_USD = 10.0
    const val DEFAULT_FALLBACK_PCT = 0.05

    data class SizingResult(
        val sizeUsd: Double,
        val kellyFraction: Double,
        val usedFraction: Double,
        val hasEnoughData: Boolean,
        val label: String
    )

    /**
     * @param pnlPercents لیست PnL درصدی تریدهای بسته (مثلاً +3.2 یا -1.5)
     * @param capital سرمایهٔ فعلی (equity)
     */
    fun fromPnlPercents(
        pnlPercents: List<Double>,
        capital: Double,
        fallbackPct: Double = DEFAULT_FALLBACK_PCT
    ): SizingResult {
        if (capital <= 0.0) {
            return SizingResult(0.0, 0.0, 0.0, false, "سرمایه نامعتبر")
        }

        val closed = pnlPercents.size
        if (closed < MIN_TRADES) {
            val fb = (capital * fallbackPct)
                .coerceAtLeast(MIN_SIZE_USD)
                .coerceAtMost(capital * MAX_CAPITAL_PCT)
            return SizingResult(
                sizeUsd = fb,
                kellyFraction = 0.0,
                usedFraction = fb / capital,
                hasEnoughData = false,
                label = "📊 داده ناکافی ($closed از $MIN_TRADES ترید) — سایز پیش‌فرض ${pct(fallbackPct)} سرمایه"
            )
        }

        val wins = pnlPercents.filter { it > 0.0 }
        val losses = pnlPercents.filter { it <= 0.0 }
        val w = wins.size.toDouble() / closed
        val avgWin = if (wins.isEmpty()) 0.0 else wins.average()
        val avgLoss = if (losses.isEmpty()) 0.0 else abs(losses.average())

        val kelly = if (avgLoss <= 0.0 || avgWin <= 0.0) {
            // هنوز ضرر ثبت نشده → نمی‌توان R را تخمین زد؛ محتاطانه نصف سقف
            MAX_KELLY * 0.5
        } else {
            val r = avgWin / avgLoss
            (w - (1.0 - w) / r).coerceIn(0.0, MAX_KELLY)
        }

        val half = kelly / 2.0
        val used = half.coerceAtMost(MAX_CAPITAL_PCT)
        val size = (capital * used)
            .coerceAtLeast(MIN_SIZE_USD)
            .coerceAtMost(capital)

        return SizingResult(
            sizeUsd = size,
            kellyFraction = kelly,
            usedFraction = used,
            hasEnoughData = true,
            label = "📊 Kelly=${pct(kelly)} → مصرف=${pct(used)} (نیم‌Kelly، سقف ${pct(MAX_CAPITAL_PCT)}) بر اساس $closed ترید • W=${pct(w)} • R=${fmtR(avgWin, avgLoss)}"
        )
    }

    private fun fmtR(avgWin: Double, avgLoss: Double): String =
        if (avgLoss <= 0.0) "—" else String.format(Locale.US, "%.2f", avgWin / avgLoss)

    private fun pct(x: Double): String = String.format(Locale.US, "%.1f%%", x * 100.0)
}
