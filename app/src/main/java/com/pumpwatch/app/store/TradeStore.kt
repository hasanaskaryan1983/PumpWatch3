package com.pumpwatch.app.store

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.SecureStorage
import com.pumpwatch.app.data.Trade

/**
 * 🚀 Commit 69 (فاز ۱ — پایداری داده): TradeStore ایمن.
 *
 * چهار باگ بسته شد (CONSTITUTION بندهای ۸، ۹، ۲):
 *
 * ۱) Race condition (بند ۹): upsert/remove/save حالا داخل `synchronized(lock)`
 *    اجرا می‌شوند تا Worker و UI هم‌زمان داده را overwrite نکنند.
 *
 * ۲) load error ≠ save empty (بند ۸): اگر decrypt/parse شکست بخورد،
 *    `loadEncryptedOrNull` مقدار null برمی‌گرداند (نه لیست خالی).
 *    هر نوشتنی که بخواهد لیست خالی را روی store خراب بنویسد، REJECT می‌شود.
 *    → تاریخچهٔ کاربر هرگز بی‌صدا پاک نمی‌شود.
 *
 * ۳) مسیر wipe چهارم داخل migrateIfNeeded: قبلاً اگر loadEncrypted در مرحلهٔ
 *    V2→V3 شکست می‌خورد، لیست خالی save می‌شد. حالا migration روی null abort می‌کند.
 *
 * ۴) maxDrawdown (بند ۲): سرمایه پایه دیگر ۱۰ هاردکد نیست؛
 *    از prefs خوانده می‌شود (setBaseCapital/getBaseCapital، پیش‌فرض ۱۰۰۰).
 */
object TradeStore {

    private const val PREFS = "pumpdump_trades"
    private const val KEY_TRADES = "trades"
    private const val KEY_TRADES_ENCRYPTED = "trades_v2_encrypted"
    private const val KEY_ENABLED = "paper_enabled"
    private const val KEY_MIGRATED = "migrated_to_v2"
    private const val KEY_MIGRATED_V3 = "migrated_to_v3"
    private const val KEY_BASE_CAPITAL = "paper_base_capital"
    private const val MAX_HISTORY = 200

    /** سرمایهٔ پایهٔ پیش‌فرض Paper Trading برای محاسبهٔ Drawdown */
    const val DEFAULT_BASE_CAPITAL = 1000.0

    private val gson = Gson()

    /** قفل برای عملیات Read-Modify-Write (بند ۹) */
    private val lock = Any()

    /**
     * پرچم «آخرین خواندن شکست خورد».
     * تا وقتی true است، هیچ save خالی‌ای پذیرفته نمی‌شود (بند ۸).
     */
    @Volatile
    private var lastLoadFailed = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // -------- سرمایهٔ پایه (بند ۲: عدد دروغ ممنوع) --------

    fun getBaseCapital(ctx: Context): Double =
        prefs(ctx).getString(KEY_BASE_CAPITAL, null)?.toDoubleOrNull() ?: DEFAULT_BASE_CAPITAL

    fun setBaseCapital(ctx: Context, capital: Double) {
        prefs(ctx).edit().putString(KEY_BASE_CAPITAL, capital.toString()).apply()
    }

    /**
     * 🚀 Sprint 15 (Commit 11): sanitizer مهاجرت نسخه ۱/۲ به ۳
     * درس Gson: فیلدهای غایب null می‌شوند، پس Boolean غایب → null → false
     * Commit 69: cast امن `as? String` (جلوگیری ClassCastException احتمالی)
     */
    internal fun upgradeLegacy(t: Trade): Trade = t.copy(
        source = (t.source as? String) ?: "manual",
        note = (t.note as? String) ?: "",
        sizeUsd = if (t.sizeUsd > 0.0) t.sizeUsd else 100.0,
        venue = (t.venue as? String) ?: "unknown",
        contract = t.contract,
        slippagePct = if (t.slippagePct > 0.0) t.slippagePct else 0.0,
        feePct = if (t.feePct > 0.0) t.feePct else 0.1,
        fillTime = t.fillTime,
        partialClose = t.partialClose,               // null => false
        partialClosePrice = t.partialClosePrice,
        partialCloseTime = t.partialCloseTime,
        partialCloseReason = t.partialCloseReason as? String,
        ledgerVersion = 3
    )

    private fun migrateIfNeeded(ctx: Context) {
        // V1 → V2 (legacy)
        if (!prefs(ctx).getBoolean(KEY_MIGRATED, false)) {
            val oldTrades = loadLegacy(ctx)
            if (oldTrades.isNotEmpty()) {
                val upgraded = oldTrades.map { upgradeLegacy(it) }
                saveEncrypted(ctx, upgraded)
            }
            prefs(ctx).edit().putBoolean(KEY_MIGRATED, true).apply()
        }
        // V2 → V3 (partial close fields)
        // 🚀 Commit 69: اگر load شکست بخورد (null)، هرگز save خالی نمی‌کنیم (بند ۸)
        if (!prefs(ctx).getBoolean(KEY_MIGRATED_V3, false)) {
            val cur = loadEncryptedOrNull(ctx)
            if (cur != null && cur.isNotEmpty()) {
                val upgraded = cur.map { upgradeLegacy(it) }
                saveEncrypted(ctx, upgraded)
            }
            prefs(ctx).edit().putBoolean(KEY_MIGRATED_V3, true).apply()
        }
    }

    private fun loadLegacy(ctx: Context): List<Trade> {
        val json = prefs(ctx).getString(KEY_TRADES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Trade>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("TradeStore", "loadLegacy parse failed — returning empty (legacy only)", e)
            emptyList()
        }
    }

    private fun saveEncrypted(ctx: Context, trades: List<Trade>) {
        val json = gson.toJson(trades)
        SecureStorage.putString(ctx, KEY_TRADES_ENCRYPTED, json)
    }

    /**
     * 🚀 Commit 69 (بند ۸): نسخهٔ صادقِ خواندن.
     * - اگر داده‌ای وجود ندارد → emptyList (این «شکست» نیست)
     * - اگر decrypt/parse شکست بخورد → **null** (یعنی داده هست ولی خوانده نمی‌شود)
     */
    private fun loadEncryptedOrNull(ctx: Context): List<Trade>? {
        val json = SecureStorage.getString(ctx, KEY_TRADES_ENCRYPTED) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Trade>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("TradeStore", "loadEncrypted FAILED — data exists but unreadable; NOT returning empty", e)
            null
        }
    }

    fun save(ctx: Context, trades: List<Trade>) {
        synchronized(lock) {
            migrateIfNeeded(ctx)
            // 🚀 Commit 69 (بند ۸): اگر آخرین خواندن شکست خورده و لیست خالی است، REJECT
            if (lastLoadFailed && trades.isEmpty()) {
                Log.e("TradeStore", "SAVE REJECTED: refusing to overwrite unreadable store with empty list")
                return
            }
            saveEncrypted(ctx, trades)
            if (!trades.isEmpty()) lastLoadFailed = false
        }
    }

    fun load(ctx: Context): List<Trade> {
        synchronized(lock) {
            migrateIfNeeded(ctx)
            val result = loadEncryptedOrNull(ctx)
            lastLoadFailed = (result == null)
            return result ?: emptyList()
        }
    }

    fun upsert(ctx: Context, trade: Trade) {
        synchronized(lock) {
            migrateIfNeeded(ctx)
            val current = loadEncryptedOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("TradeStore", "UPSERT ABORTED: store unreadable — refusing to overwrite")
                return
            }
            lastLoadFailed = false
            val list = current.toMutableList()
            val idx = list.indexOfFirst { it.id == trade.id }
            if (idx >= 0) list[idx] = trade else list.add(0, trade)
            while (list.count { it.status == "CLOSED" } > MAX_HISTORY) {
                val closedIdx = list.indexOfLast { it.status == "CLOSED" }
                if (closedIdx >= 0) list.removeAt(closedIdx)
            }
            saveEncrypted(ctx, list)
        }
    }

    fun remove(ctx: Context, id: String) {
        synchronized(lock) {
            migrateIfNeeded(ctx)
            val current = loadEncryptedOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("TradeStore", "REMOVE ABORTED: store unreadable — refusing to overwrite")
                return
            }
            lastLoadFailed = false
            saveEncrypted(ctx, current.filterNot { it.id == id })
        }
    }

    fun clearAll(ctx: Context) {
        synchronized(lock) {
            SecureStorage.remove(ctx, KEY_TRADES_ENCRYPTED)
            prefs(ctx).edit()
                .remove(KEY_TRADES)
                .remove(KEY_MIGRATED)
                .remove(KEY_MIGRATED_V3)
                .apply()
            lastLoadFailed = false
        }
    }

    fun stats(ctx: Context): TradeStats {
        val all = load(ctx)
        val open = all.filter { it.status == "OPEN" }
        val closed = all.filter { it.status == "CLOSED" }
        val wins = closed.count { it.totalRealizedPnl() > 0 }   // 🚀 Commit 11
        val totalPnl = closed.sumOf { it.totalRealizedPnl() } + open.sumOf { it.unrealizedPnl() }
        val winRate = if (closed.isEmpty()) 0.0 else wins * 100.0 / closed.size
        return TradeStats(
            openCount = open.size,
            closedCount = closed.size,
            winCount = wins,
            lossCount = closed.size - wins,
            winRate = winRate,
            totalPnl = totalPnl
        )
    }

    fun advancedStats(ctx: Context): AdvancedTradeStats {
        val all = load(ctx)
        val closed = all.filter { it.status == "CLOSED" }
        val wins = closed.filter { it.totalRealizedPnl() > 0 }
        val losses = closed.filter { it.totalRealizedPnl() <= 0 }
        val pnlUsd = closed.sumOf { it.totalRealizedPnlUsd() }   // 🚀 Commit 11
        val rMultiples = closed.mapNotNull { it.rMultiple() }
        val avgR = if (rMultiples.isEmpty()) 0.0 else rMultiples.average()
        val bestR = rMultiples.maxOrNull() ?: 0.0
        val worstR = rMultiples.minOrNull() ?: 0.0

        val bySource = all.groupBy { it.source }.mapValues { (_, list) ->
            val c = list.filter { it.status == "CLOSED" }
            SourceBreakdown(
                count = list.size,
                closedCount = c.size,
                winCount = c.count { it.totalRealizedPnl() > 0 },
                pnlUsd = c.sumOf { it.totalRealizedPnlUsd() }
            )
        }

        // 🚀 Commit 69 (بند ۲): سرمایهٔ واقعی از prefs، نه ۱۰۰ هاردکد
        val maxDD = maxDrawdown(closed, getBaseCapital(ctx))

        return AdvancedTradeStats(
            totalClosed = closed.size,
            wins = wins.size,
            losses = losses.size,
            winRate = if (closed.isEmpty()) 0.0 else wins.size * 100.0 / closed.size,
            pnlUsd = pnlUsd,
            avgR = avgR,
            bestR = bestR,
            worstR = worstR,
            maxDrawdownPct = maxDD,
            bySource = bySource
        )
    }

    private fun maxDrawdown(closedTrades: List<Trade>, baseCapital: Double): Double =
        maxDrawdownFromPnls(closedTrades.map { it.totalRealizedPnl() }, baseCapital)

    /**
     * 🚀 Commit 69: منطق خالص Drawdown (قابل تست بدون Android).
     * Drawdown = بیشینهٔ افت نسبی از قلهٔ تاریخی equity curve.
     * سرمایهٔ پایه ورودی است — هرگز هاردکد نمی‌شود (بند ۲).
     */
    internal fun maxDrawdownFromPnls(pnls: List<Double>, baseCapital: Double): Double {
        if (pnls.isEmpty()) return 0.0
        val base = if (baseCapital > 0.0) baseCapital else DEFAULT_BASE_CAPITAL
        var equity = base
        var peak = base
        var maxDD = 0.0
        for (pnl in pnls) {
            equity += pnl
            if (equity > peak) peak = equity
            if (peak > 0.0) {
                val dd = (peak - equity) / peak * 100.0
                if (dd > maxDD) maxDD = dd
            }
        }
        return maxDD
    }
}

data class TradeStats(
    val openCount: Int,
    val closedCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRate: Double,
    val totalPnl: Double
)

data class AdvancedTradeStats(
    val totalClosed: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val pnlUsd: Double,
    val avgR: Double,
    val bestR: Double,
    val worstR: Double,
    val maxDrawdownPct: Double,
    val bySource: Map<String, SourceBreakdown>
)

data class SourceBreakdown(
    val count: Int,
    val closedCount: Int,
    val winCount: Int,
    val pnlUsd: Double
)
