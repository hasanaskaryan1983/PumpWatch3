package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.store.StorageGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoggedSignal(
    val symbol: String,
    val side: String,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val status: String = "OPEN",
    val exitPrice: Double? = null,
    val mode: String = "SPOT",
    val score: Int = 0,
    val time: Long = 0L,
    val currentPrice: Double? = null,
    val highestPrice: Double = 0.0,
    val trailingStop: Double = 0.0,
    val currentTarget: Double = 0.0
) {
    fun updateLivePrice(price: Double): LoggedSignal {
        val best = when {
            highestPrice <= 0.0 -> price
            side == "BUY" -> maxOf(highestPrice, price)
            else -> minOf(highestPrice, price)
        }
        val trail = if (side == "BUY") best * 0.92 else best * 1.08
        val newStop = when {
            trailingStop <= 0.0 -> trail
            side == "BUY" -> maxOf(trailingStop, trail)
            else -> minOf(trailingStop, trail)
        }
        val curT = if (currentTarget > 0) currentTarget else target
        val newTarget = if (side == "BUY") {
            if (price >= curT) curT * 1.15 else curT
        } else {
            if (price <= curT) curT * 0.85 else curT
        }
        return copy(
            currentPrice = price,
            highestPrice = best,
            trailingStop = newStop,
            currentTarget = newTarget
        )
    }
}

data class SignalAccuracyStats(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val expired: Int,
    val open: Int,
    val winRate: Double,
    val totalDecided: Int
)

object SignalLogger {
    private const val PREFS = "pumpwatch_prefs"
    private const val KEY = "signal_logs"
    private val GSON = Gson()

    // 🚀 P1-2: خواندن با مهاجرت + قرنطینه به‌جای حذف بی‌صدا
    fun load(ctx: Context): List<LoggedSignal> {
        val p = ctx.getSharedPreferences(PREFS, 0)
        val raw = p.getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        val migrated = StorageGuard.migrateRaw(ctx, PREFS, KEY, raw)
        return try {
            GSON.fromJson(migrated, object : TypeToken<MutableList<LoggedSignal>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            StorageGuard.quarantine(ctx, PREFS, KEY, migrated)
            p.edit().remove(KEY).apply()
            emptyList()
        }
    }

    fun save(ctx: Context, logs: List<LoggedSignal>) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY, GSON.toJson(logs)).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .remove(KEY)
            .apply()
    }

    fun add(ctx: Context, s: LoggedSignal) {
        val list = load(ctx).toMutableList()
        list.add(0, s)
        save(ctx, list.take(200))
    }

    fun log(ctx: Context, s: LoggedSignal, nowMs: Long = System.currentTimeMillis()): Boolean {
        val list = load(ctx).toMutableList()
        if (isDuplicate(list, s, nowMs)) return false
        list.add(0, s)
        save(ctx, list.take(200))
        return true
    }

    fun isDuplicate(existing: List<LoggedSignal>, s: LoggedSignal, now: Long): Boolean =
        existing.any { it.symbol == s.symbol && it.side == s.side && it.status == "OPEN" } ||
                (s.time > 0 && existing.any { it.symbol == s.symbol && it.side == s.side && it.time == s.time }) ||
                existing.any { it.symbol == s.symbol && it.side == s.side && it.time > 0 && now - it.time < 3_600_000L }

    suspend fun livePrice(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            val kl = BinanceClient.api.klines("${symbol.uppercase()}USDT", "1h", 2)
            if (kl.isNotEmpty()) return@withContext kl.last()[4].asDouble
        } catch (_: Exception) { }
        try {
            val coins = ApiClient.getTop1000Coins()
            coins.firstOrNull { it.symbol.equals(symbol, true) }?.current_price?.let { return@withContext it }
        } catch (_: Exception) { }
        try {
            GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
                ?.attributes?.priceUsd?.toDoubleOrNull()?.let { return@withContext it }
        } catch (_: Exception) { }
        null
    }

    suspend fun evaluate(ctx: Context, logs: List<LoggedSignal>, nowMs: Long = System.currentTimeMillis()): List<LoggedSignal> = withContext(Dispatchers.IO) {
        logs.map { s ->
            if (s.status == "OPEN" && s.time > 0 && nowMs - s.time > 24 * 3600_000L) s.copy(status = "EXP")
            else s
        }
    }

    suspend fun updateOpenSignals(ctx: Context, logs: List<LoggedSignal>): List<LoggedSignal> = withContext(Dispatchers.IO) {
        val out = logs.toMutableList()
        var changed = false
        for (i in out.indices) {
            val s = out[i]
            if (s.status != "OPEN") continue
            val px = livePrice(s.symbol) ?: continue
            val u = s.updateLivePrice(px)
            val hitStop = if (s.side == "BUY") px <= u.trailingStop else px >= u.trailingStop
            if (hitStop) {
                val profit = if (s.side == "BUY") px > s.entry else px < s.entry
                out[i] = u.copy(status = if (profit) "WIN" else "LOSS", exitPrice = px)
            } else {
                out[i] = u
            }
            changed = true
        }
        if (changed) save(ctx, out)
        out
    }

    fun accuracyStats(logs: List<LoggedSignal>): SignalAccuracyStats {
        val open = logs.count { it.status == "OPEN" }
        val wins = logs.count { it.status == "WIN" }
        val losses = logs.count { it.status == "LOSS" }
        val expired = logs.count { it.status == "EXP" }
        val decided = wins + losses
        val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
        return SignalAccuracyStats(
            total = logs.size,
            wins = wins,
            losses = losses,
            expired = expired,
            open = open,
            winRate = winRate,
            totalDecided = decided
        )
    }

    fun accuracyByMode(logs: List<LoggedSignal>): Map<String, SignalAccuracyStats> =
        logs.groupBy { it.mode }.mapValues { (_, list) -> accuracyStats(list) }

    fun accuracyBySide(logs: List<LoggedSignal>): Map<String, SignalAccuracyStats> =
        logs.groupBy { it.side }.mapValues { (_, list) -> accuracyStats(list) }
}
