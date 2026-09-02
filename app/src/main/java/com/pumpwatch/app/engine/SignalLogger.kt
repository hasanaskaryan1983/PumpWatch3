package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class LoggedSignal(
    val symbol: String,
    val side: String,
    val score: Int,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val time: Long,
    var status: String = "OPEN",
    var exitPrice: Double? = null
)

object SignalLogger {
    private const val KEY = "pumpdump_signal_log_v1"
    private val gson = Gson()

    fun load(ctx: Context): MutableList<LoggedSignal> {
        val json = ctx.getSharedPreferences("pumpwatch_prefs", 0)
            .getString(KEY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<LoggedSignal>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(ctx: Context, list: List<LoggedSignal>) {
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putString(KEY, gson.toJson(list)).apply()
    }

    fun log(ctx: Context, sig: LoggedSignal): Boolean {
        val list = load(ctx)
        if (list.any { it.symbol == sig.symbol && it.status == "OPEN" }) return false
        list.add(0, sig)
        save(ctx, list.take(100))
        return true
    }

    fun evaluate(
        list: MutableList<LoggedSignal>,
        prices: Map<String, Double>
    ): MutableList<LoggedSignal> {
        val now = System.currentTimeMillis()
        for (s in list) {
            if (s.status != "OPEN") continue
            val p = prices[s.symbol] ?: continue
            s.exitPrice = p
            val win = if (s.side == "BUY") p >= s.target else p <= s.target
            val loss = if (s.side == "BUY") p <= s.stop else p >= s.stop
            when {
                win -> s.status = "WIN"
                loss -> s.status = "LOSS"
                now - s.time > 72 * 3_600_000L -> s.status = "EXP"
            }
        }
        return list
    }
}
