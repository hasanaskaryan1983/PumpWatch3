package com.pumpwatch.app.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.Trade

object TradeStore {

    private const val PREFS = "pumpdump_trades"
    private const val KEY_TRADES = "trades"
    private const val KEY_ENABLED = "paper_enabled"
    private const val MAX_HISTORY = 200

    private val gson = Gson()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    // ---------- تنظیمات ----------

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ---------- ذخیره‌سازی ----------

    fun save(ctx: Context, trades: List<Trade>) {
        prefs(ctx).edit().putString(KEY_TRADES, gson.toJson(trades)).apply()
    }

    fun load(ctx: Context): List<Trade> {
        val json = prefs(ctx).getString(KEY_TRADES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Trade>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------- اضافه/بروزرسانی ----------

    fun upsert(ctx: Context, trade: Trade) {
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
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().remove(KEY_TRADES).apply()
    }

    // ---------- آمار ----------

    fun stats(ctx: Context): TradeStats {
        val all = load(ctx)
        val open = all.filter { it.status == "OPEN" }
        val closed = all.filter { it.status == "CLOSED" }
        val wins = closed.count { it.realizedPnl() > 0 }
        val totalPnl = closed.sumOf { it.realizedPnl() } + open.sumOf { it.unrealizedPnl() }
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
}

data class TradeStats(
    val openCount: Int,
    val closedCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRate: Double,
    val totalPnl: Double
)
