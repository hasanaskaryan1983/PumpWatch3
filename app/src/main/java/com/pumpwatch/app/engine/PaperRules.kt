package com.pumpwatch.app.engine

import com.pumpwatch.app.ui.PaperState

/**
 * 🚀 Commit 107: منبع واحد هزینه و موجودی برای Paper اسپات.
 *
 * درس T2: موجودی نقد (cash) هرگز به‌صورت مستقل ذخیره/تغییر نمی‌شود؛
 * همیشه از لیست تریدها بازمحاسبه می‌شود تا نویسندهٔ هم‌زمان (UI و Worker)
 * نتوانند مقدار یکدیگر را overwrite کنند.
 */
object PaperRules {

    /** کارمزد هر طرف معامله (٪) */
    const val FEE_PCT = 0.10

    /** لغزش قیمت هر طرف معامله (٪) */
    const val SLIP_PCT = 0.05

    /** هزینهٔ رفت‌وبرگشت کامل (٪) = ۲×کارمزد + لغزش */
    const val ROUND_COST_PCT = 2 * FEE_PCT + SLIP_PCT   // = 0.25

    /** سرمایهٔ اولیهٔ واحد برای کل اپ */
    const val START_CAPITAL = 1000.0

    /** قفل مشترک Read-Modify-Write بین UI و Worker */
    val lock = Any()

    fun grossPnlPct(entry: Double, exit: Double): Double =
        if (entry > 0) (exit - entry) / entry * 100 else 0.0

    /** 🚀 Commit 107 (T1): PnL خالص پس از کسر هزینهٔ رفت‌وبرگشت */
    fun netPnlPct(entry: Double, exit: Double): Double =
        grossPnlPct(entry, exit) - ROUND_COST_PCT

    /**
     * 🚀 Commit 107 (T2): cash یک تابع خالص از تریدهاست:
     * هر OPEN اندازه‌اش را کم می‌کند؛ هر CLOSED سود/زیان خالصش را اضافه می‌کند.
     */
    fun recomputeCash(s: PaperState): Double {
        val openUsd = s.trades.filter { it.status == "OPEN" }.sumOf { it.sizeUsd }
        val closedPnlUsd = s.trades.filter { it.status == "CLOSED" }
            .sumOf { it.sizeUsd * it.pnl / 100.0 }
        return START_CAPITAL - openUsd + closedPnlUsd
    }
}
