package com.pumpwatch.app.engine

import com.pumpwatch.app.ui.PaperState

/**
 * 🚀 Commit 107 + 120: قوانین مشترک Paper اسپات.
 *
 * - lock: قفل مشترک بین UI و Worker تا هیچ‌کدام ledger را overwrite نکنند (T2)
 * - recomputeCash: cash همیشه از روی تریدها باز محاسبه می‌شود (نه اعتماد به مقدار ذخیره‌شده)
 * - مدل هزینهٔ واحد: fee 0.1% + slippage 0.05% هر طرف → 0.3% رفت‌وبرگشت (T1)
 */
object PaperRules {

    /** قفل مشترک Read-Modify-Write برای UI و Worker */
    val lock = Any()

    const val FEE_PCT = 0.1
    const val SLIPPAGE_PCT = 0.05
    const val DEFAULT_CAPITAL = 1000.0

    /** هزینهٔ رفت‌وبرگشت (هر دو طرف) به درصد */
    fun roundTripCostPct(): Double = 2.0 * (FEE_PCT + SLIPPAGE_PCT)

    /**
     * باز محاسبهٔ cash از روی تریدها:
     * cash = سرمایهٔ پایه − سرمایهٔ درگیر در تریدهای باز + برگشتی تریدهای بسته
     */
    fun recomputeCash(state: PaperState): Double {
        val openStake = state.trades
            .filter { it.status == "OPEN" }
            .sumOf { it.sizeUsd }
        val closedBack = state.trades
            .filter { it.status == "CLOSED" }
            .sumOf { it.sizeUsd * (1.0 + it.pnl / 100.0) }
        return DEFAULT_CAPITAL - openStake + closedBack
    }
}
