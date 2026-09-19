package com.pumpwatch.app.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.SecureStorage
import com.pumpwatch.app.data.Trade

object TradeStore {

    private const val PREFS = "pumpdump_trades"
    private const val KEY_TRADES = "trades"
    private const val KEY_TRADES_ENCRYPTED = "trades_v2_encrypted"
    private const val KEY_ENABLED = "paper_enabled"
    private const val KEY_MIGRATED = "migrated_to_v2"
    private const val KEY_MIGRATED_V3 = "migrated_to_v3"
    private const val MAX_HISTORY = 200

    private val gson = Gson()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 🚀 Sprint 15 (Commit 11): sanitizer مهاجرت نسخه ۱/۲ به ۳
     * درس Gson: فیلدهای غایب null می‌شوند، پس Boolean غایب → null → false
     */
    internal fun upgradeLegacy(t: Trade): Trade = t.copy(
        source = (t.source as String?) ?: "manual",
        note = (t.note as String?) ?: "",
        sizeUsd = if (t.sizeUsd > 0.0) t.sizeUsd else 100.0,
        venue = (t.venue as String?) ?: "unknown",
        contract = t.contract,
        slippagePct = if (t.slippagePct > 0.0) t.slippagePct else 0.0,
        feePct = if (t.feePct > 0.0) t.feePct else 0.1,
        fillTime = t.fillTime,
        partialClose = t.partialClose,               // null => false
        partialClosePrice = t.partialClosePrice,
        partialCloseTime = t.partialCloseTime,
        partialCloseReason = t.partialCloseReason as String?,
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
        if (!prefs(ctx).getBoolean(KEY_MIGRATED_V3, false)) {
            val cur = loadEncrypted(ctx)
            if (cur.isNotEmpty()) {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveEncrypted(ctx: Context, trades: List<Trade>) {
        val json = gson.toJson(trades)
        SecureStorage.putString(ctx, KEY_TRADES_ENCRYPTED, json)
    }

    private fun loadEncrypted(ctx: Context): List<Trade> {
        val json = SecureStorage.getString(ctx, KEY_TRADES_ENCRYPTED) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Trade>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, trades: List<Trade>) {
        migrateIfNeeded(ctx)
        saveEncrypted(ctx, trades)
    }

    fun load(ctx: Context): List<Trade> {
        migrateIfNeeded(ctx)
        return loadEncrypted(ctx)
    }

    fun upsert(ctx: Context, trade: Trade) {
        migrateIfNeeded(ctx)
        val list = load(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == trade.id }
        if (idx >= 0) list[idx] = trade else list.add(0, trade)
        while (list.count { it.status == "CLOSED" } > MAX_HISTORY) {
            val closedIdx = list.indexOfLast { it.status == "CLOSED" }
            if (closedIdx >= 0) list.removeAt(closedIdx)
        }
        save(ctx, list)
    }

    fun remove(ctx: Context, id: String) {
        migrateIfNeeded(ctx)
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    fun clearAll(ctx: Context) {
        SecureStorage.remove(ctx, KEY_TRADES_ENCRYPTED)
        prefs(ctx).edit()
            .remove(KEY_TRADES)
            .remove(KEY_MIGRATED)
            .remove(KEY_MIGRATED_V3)
            .apply()
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

        val maxDD = maxDrawdown(closed)

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

    private fun maxDrawdown(closedTrades: List<Trade>): Double {
        if (closedTrades.isEmpty()) return 0.0
        val sorted = closedTrades.sortedBy { it.exitTime ?: 0L }
        var equity = 100.0
        var peak = 100.0
        var maxDD = 0.0
        for (t in sorted) {
            equity += t.totalRealizedPnl()   // 🚀 Commit 11
            if (equity > peak) peak = equity
            val dd = (peak - equity) / peak * 100.0
            if (dd > maxDD) maxDD = dd
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
