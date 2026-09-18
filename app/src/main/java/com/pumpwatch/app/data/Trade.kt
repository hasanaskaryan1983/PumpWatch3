package com.pumpwatch.app.data

// ---------- مدل معامله شبیه‌سازی ----------

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7C): مدل نسخه‌دار با فیلدهای provenance
 *
 * نسخه ۲ (Sprint 14):
 * - venue: منبع داده (Binance/Bybit/OKX/Gate/CoinGecko)
 * - contract: آدرس قرارداد (برای DEX tokens)
 * - slippagePct: اسلیپیج واقعی در زمان fill
 * - feePct: کارمزد واقعی صرافی
 * - fillTime: زمان واقعی fill (نه closeTime کندل سیگنال)
 * - ledgerVersion: نسخهٔ ledger برای migration
 *
 * همهٔ فیلدهای جدید default دارند → backward compatible
 */
data class Trade(
    val id: String,              // UUID
    val coinId: String,
    val symbol: String,
    val name: String,
    val side: String,            // "PUMP" | "DUMP"
    val mode: String,            // "SPOT" | "FUT"
    val entryPrice: Double,
    val currentPrice: Double,
    val entryTime: Long,
    val exitTime: Long?,
    val exitPrice: Double?,
    val initialStop: Double,
    val currentStop: Double,     // استاپ شناور
    val target1: Double,
    val target2: Double,
    val exitReason: String?,     // "STOP" | "TARGET" | "REVERSAL" | "MANUAL"
    val status: String,          // "OPEN" | "CLOSED"
    // 🚀 Sprint 13 (F6a-ext): فیلدهای جدید با default (backward compatible)
    val source: String = "manual",   // "scanner" | "manual" | "backtest"
    val note: String = "",
    val sizeUsd: Double = 100.0,
    // 🚀 Sprint 14 (مرحله ۳ / Commit 7C): provenance و نسخه‌بندی
    val venue: String = "unknown",           // منبع داده
    val contract: String? = null,            // آدرس قرارداد (DEX)
    val slippagePct: Double = 0.1,           // اسلیپیج واقعی
    val feePct: Double = 0.1,                // کارمزد واقعی
    val fillTime: Long? = null,              // زمان واقعی fill (next-bar)
    val ledgerVersion: Int = 2               // نسخهٔ ledger (برای migration)
) {
    // محاسبه PnL لحظه‌ای
    fun unrealizedPnl(): Double {
        if (status != "OPEN") return 0.0
        val diff = currentPrice - entryPrice
        val pct = if (side == "PUMP") diff / entryPrice * 100
                  else -diff / entryPrice * 100
        return pct - feePct  // 🚀 Sprint 14: استفاده از feePct واقعی
    }

    // محاسبه PnL نهایی (معامله بسته‌شده)
    fun realizedPnl(): Double {
        if (status != "CLOSED" || exitPrice == null) return 0.0
        val diff = exitPrice - entryPrice
        val pct = if (side == "PUMP") diff / entryPrice * 100
                  else -diff / entryPrice * 100
        return pct - (feePct * 2) - slippagePct  // 🚀 Sprint 14: fee + slippage واقعی
    }

    // 🚀 Sprint 13 (F6a-ext): PnL دلاری (برای ژورنال)
    fun realizedPnlUsd(): Double {
        val pct = realizedPnl()
        return sizeUsd * pct / 100.0
    }

    fun unrealizedPnlUsd(): Double {
        val pct = unrealizedPnl()
        return sizeUsd * pct / 100.0
    }

    // 🚀 Sprint 13 (F6a-ext): R-multiple برای ژورنال حرفه‌ای
    fun rMultiple(): Double? {
        if (status != "CLOSED" || exitPrice == null) return null
        val risk = kotlin.math.abs(entryPrice - initialStop)
        if (risk <= 0.0) return null
        val dir = if (side == "PUMP") 1.0 else -1.0
        return ((exitPrice - entryPrice) / risk) * dir
    }
}
