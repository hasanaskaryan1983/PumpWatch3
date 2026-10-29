package com.pumpwatch.app.data

/**
 * 🚀 Sprint 15 (فاز ۱ / Commit 11): Take-profit پله‌ای
 * 🚀 Sprint 16 (F2): PnL فیوچرز مارجین‌محور
 *
 * فیلدهای جدید F2:
 *  - leverage: اهرم برای فیوچرز (پیش‌فرض 1.0 برای اسپات)
 *  - fundingPaid: مبلغ فاندینگ پرداختی/دریافتی به USD
 *
 * backward compatible: همهٔ فیلدهای جدید default دارند.
 */
data class Trade(
    val id: String,
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
    val currentStop: Double,
    val target1: Double,
    val target2: Double,
    val exitReason: String?,     // "STOP" | "TARGET" | "VOL_SPIKE" | "MOMENTUM_RSI" | "TIME" | "MANUAL"
    val status: String,          // "OPEN" | "CLOSED"
    // 🚀 Sprint 13 (F6a-ext)
    val source: String = "manual",
    val note: String = "",
    val sizeUsd: Double = 100.0,
    // 🚀 Sprint 14 (Commit 7C): provenance
    val venue: String = "unknown",
    val contract: String? = null,
    val slippagePct: Double = 0.1,
    val feePct: Double = 0.1,
    val fillTime: Long? = null,
    // 🚀 Sprint 16 (F2): PnL فیوچرز مارجین‌محور
    val leverage: Double = 1.0,           // اهرم (پیش‌فرض 1.0 برای اسپات)
    val fundingPaid: Double = 0.0,        // فاندینگ پرداختی/دریافتی به USD
    // 🚀 Sprint 15 (Commit 11): partial close در +1R
    val partialClose: Boolean = false,
    val partialClosePrice: Double? = null,
    val partialCloseTime: Long? = null,
    val partialCloseReason: String? = null,
    val ledgerVersion: Int = 3
) {
    // ---------- PnL لحظه‌ای ----------
    fun unrealizedPnl(): Double {
        if (status != "OPEN") return 0.0
        if (entryPrice <= 0.0) return 0.0  // 🛡️ گارد NaN
        
        val diff = currentPrice - entryPrice
        val basePct = if (side == "PUMP") diff / entryPrice * 100 else -diff / entryPrice * 100
        val leveragedPct = basePct * leverage
        return leveragedPct - feePct
    }

    /**
     * 🚀 Commit 11: PnL بخش ۵۰٪ اول (partial close)
     * 🚀 F2: با اهرم و فاندینگ
     */
    fun realizedPnlPartial(): Double {
        if (!partialClose || partialClosePrice == null) return 0.0
        if (entryPrice <= 0.0) return 0.0  // 🛡️ گارد NaN
        
        val diff = partialClosePrice - entryPrice
        val basePct = if (side == "PUMP") diff / entryPrice * 100 else -diff / entryPrice * 100
        val leveragedPct = basePct * leverage
        val partialPnl = 0.5 * (leveragedPct - (feePct * 2) - slippagePct)
        // کسر نیمی از فاندینگ (چون فقط نیمی از پوزیشن بسته شده)
        return partialPnl - (fundingPaid * 0.5 * 100.0 / sizeUsd)
    }

    /**
     * 🚀 Commit 11: PnL بخش ۵۰٪ دوم (فقط اگر ترید کامل بسته شده باشد)
     * 🚀 F2: با اهرم و فاندینگ
     */
    fun realizedPnlFinal(): Double {
        if (status != "CLOSED" || exitPrice == null) return 0.0
        if (entryPrice <= 0.0) return 0.0  // 🛡️ گارد NaN
        
        val diff = exitPrice - entryPrice
        val basePct = if (side == "PUMP") diff / entryPrice * 100 else -diff / entryPrice * 100
        val leveragedPct = basePct * leverage
        val finalPnl = 0.5 * (leveragedPct - (feePct * 2) - slippagePct)
        // کسر نیمی از فاندینگ
        return finalPnl - (fundingPaid * 0.5 * 100.0 / sizeUsd)
    }

    /**
     * 🚀 Commit 11: مجموع PnL هر دو بخش
     */
    fun totalRealizedPnl(): Double = realizedPnlPartial() + realizedPnlFinal()

    // Backward-compatible: realizedPnl قدیمی (روی exitPrice)
    fun realizedPnl(): Double {
        if (status != "CLOSED" || exitPrice == null) return 0.0
        if (entryPrice <= 0.0) return 0.0  // 🛡️ گارد NaN
        
        val diff = exitPrice - entryPrice
        val basePct = if (side == "PUMP") diff / entryPrice * 100 else -diff / entryPrice * 100
        val leveragedPct = basePct * leverage
        val pnl = leveragedPct - (feePct * 2) - slippagePct
        // کسر کل فاندینگ
        return pnl - (fundingPaid * 100.0 / sizeUsd)
    }

    fun realizedPnlUsd(): Double = sizeUsd * realizedPnl() / 100.0

    /** 🚀 Commit 11: PnL دلاری کل هر دو بخش */
    fun totalRealizedPnlUsd(): Double = sizeUsd * totalRealizedPnl() / 100.0

    fun unrealizedPnlUsd(): Double {
        val pct = unrealizedPnl()
        return sizeUsd * pct / 100.0
    }

    fun rMultiple(): Double? {
        if (status != "CLOSED" || exitPrice == null) return null
        val risk = kotlin.math.abs(entryPrice - initialStop)
        if (risk <= 0.0) return null
        val dir = if (side == "PUMP") 1.0 else -1.0
        return ((exitPrice - entryPrice) / risk) * dir
    }
}
