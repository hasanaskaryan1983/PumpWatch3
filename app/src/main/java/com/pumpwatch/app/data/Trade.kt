package com.pumpwatch.app.data

// ---------- مدل معامله شبیه‌سازی ----------

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
    val status: String           // "OPEN" | "CLOSED"
) {
    // محاسبه PnL لحظه‌ای
    fun unrealizedPnl(): Double {
        if (status != "OPEN") return 0.0
        val diff = currentPrice - entryPrice
        val pct = if (side == "PUMP") diff / entryPrice * 100
                  else -diff / entryPrice * 100
        return pct - 0.1 // کارمزد 0.1%
    }

    // محاسبه PnL نهایی (معامله بسته‌شده)
    fun realizedPnl(): Double {
        if (status != "CLOSED" || exitPrice == null) return 0.0
        val diff = exitPrice - entryPrice
        val pct = if (side == "PUMP") diff / entryPrice * 100
                  else -diff / entryPrice * 100
        return pct - 0.2 // کارمزد ورود + خروج
    }
}
