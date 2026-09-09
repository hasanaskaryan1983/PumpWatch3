package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- کلاس سیگنال (با Trailing Stop و هدف شناور) ----------
data class LoggedSignal(
    val symbol: String,
    val side: String, // "BUY" or "SELL"
    val entry: Double,
    val stop: Double,
    val target: Double,
    val status: String, // "OPEN", "WIN", "LOSS", "EXP"
    val exitPrice: Double? = null,
    val mode: String = "SPOT", // "SPOT" or "FUT"
    val score: Int = 0,
    val time: Long = 0L,
    val currentPrice: Double? = null,
    val highestPrice: Double = 0.0,   // برای BUY = سقف • برای SELL = کف
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

// ---------- موتور لاگ + آپدیت زنده ----------
object SignalLogger {
    private const val KEY = "signal_logs"
    private val GSON = Gson()

    fun load(ctx: Context): List<LoggedSignal> = try {
        val json = ctx.getSharedPreferences("pumpwatch_prefs", 0).getString(KEY, "") ?: ""
        if (json.isEmpty()) emptyList()
        else GSON.fromJson(json, object : TypeToken<MutableList<LoggedSignal>>() {}.type) ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun save(ctx: Context, logs: List<LoggedSignal>) {
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putString(KEY, GSON.toJson(logs)).apply()
    }

    fun add(ctx: Context, s: LoggedSignal) {
        val list = load(ctx).toMutableList()
        list.add(0, s)
        save(ctx, list.take(200))
    }

    // قیمت لحظه‌ای: اول Binance → بعد CoinGecko → بعد DEX
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

    // بررسی انقضا (۲۴ ساعت بدون نتیجه)
    suspend fun evaluate(ctx: Context, logs: List<LoggedSignal>): List<LoggedSignal> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        logs.map { s ->
            if (s.status == "OPEN" && s.time > 0 && now - s.time > 24 * 3600_000L) s.copy(status = "EXP")
            else s
        }
    }

    // 🔄 آپدیت زنده: قیمت بگیر → تریلینگ استاپ و هدف شناور رو اعمال کن → اگه استاپ خورد ببند
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
}
