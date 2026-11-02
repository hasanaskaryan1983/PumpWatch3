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
    /**
     * 🚀 F10: بازمحاسبهٔ دقیق وجه نقد در دسترس (Available Buying Power)
     * فرمول: سرمایه اولیه - حجم قفل‌شده در معاملات باز + (اصل سرمایه + سود خالص پس از کسر هزینه) معاملات بسته
     */
    fun recomputeCash(state: PaperState): Double {
        val initial = state.initialCapital
        
        // وجهی که در معاملات باز قفل شده است
        val openStake = state.trades
            .filter { it.status == "OPEN" }
            .sumOf { it.sizeUsd }
            
        // بازگشت سرمایه از معاملات بسته (شامل اصل پول + سود/زیان خالص پس از کسر کارمزد و اسلیپج)
        val closedNetReturn = state.trades
            .filter { it.status == "CLOSED" }
            .sumOf { trade ->
                val grossPnlUsd = trade.sizeUsd * (trade.pnl / 100.0)
                val costUsd = trade.sizeUsd * (roundTripCostPct() / 100.0)
                val netPnlUsd = grossPnlUsd - costUsd
                trade.sizeUsd + netPnlUsd // بازگشت اصل پول به اضافه سود خالص
            }
            
        return initial - openStake + closedNetReturn
    }
}
