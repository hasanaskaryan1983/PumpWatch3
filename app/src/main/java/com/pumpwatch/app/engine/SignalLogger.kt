package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.BinanceClient

data class LoggedSignal(
    val symbol: String,
    val side: String,
    val score: Int,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val time: Long,
    val mode: String = "SPOT",
    var status: String = "OPEN",
    var exitPrice: Double? = null
)

object SignalLogger {
    private const val KEY = "pumpdump_signal_log_v1"
    private const val EXPIRY_MS = 24 * 3_600_000L      // ۲۴ ساعت — هماهنگ با متن UI
    private const val MAX_EVALUATE_FETCH = 40          // سقف دریافت کندل در هر بار ارزیابی

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

    /**
     * ارزیابی صحیح با کندل ساعتی واقعی:
     * - WIN فقط اگر قیمت «بعد از» زمان سیگنال به هدف رسیده باشه
     * - LOSS فقط اگر استاپ خورده باشه (در یک کندل، استاپ اولویت داره = محافظه‌کارانه)
     * - exitPrice دقیقاً سطح هدف/استاپ ثبت می‌شه → PnL واقعی
     * - بعد از ۲۴ ساعت بدون نتیجه → EXP
     * (نسخه قبلی فقط «آخرین قیمت لحظه‌ای» رو چک می‌کرد و برد/باخت‌ها رو جابجا نشون می‌داد)
     */
    fun evaluate(ctx: Context, list: MutableList<LoggedSignal>): MutableList<LoggedSignal> {
        val now = System.currentTimeMillis()
        var fetched = 0

        for (s in list) {
            if (s.status != "OPEN") continue
            if (now - s.time < 45 * 60_000L) continue   // تازه ثبت شده — هنوز فرصت نداشته
            if (fetched >= MAX_EVALUATE_FETCH) break     // جلوگیری از طوفان درخواست

            fetched++
            try {
                val hours = ((now - s.time) / 3_600_000L + 2).toInt().coerceIn(2, 48)
                val klines = BinanceClient.api.klines("${s.symbol}USDT", "1h", hours)
                if (klines.isEmpty()) continue

                for (c in klines) {
                    val candleTime = c[0].asLong()
                    if (candleTime <= s.time) continue          // فقط کندل‌های «بعد» از سیگنال
                    val high = c[2].asDouble()
                    val low = c[3].asDouble()
                    val isBuy = s.side == "BUY"

                    val hitStop = if (isBuy) low <= s.stop else high >= s.stop
                    val hitTarget = if (isBuy) high >= s.target else low <= s.target

                    when {
                        hitStop -> { s.status = "LOSS"; s.exitPrice = s.stop }      // استاپ اولویت داره
                        hitTarget -> { s.status = "WIN"; s.exitPrice = s.target }
                    }
                    if (s.status != "OPEN") break
                }

                if (s.status == "OPEN" && now - s.time > EXPIRY_MS) {
                    s.status = "EXP"
                    s.exitPrice = klines.last()[4].asDouble()
                }
            } catch (_: Exception) {
                // خطای شبکه → سیگنال باز می‌مونه و دفعه بعد دوباره چک می‌شه
            }
        }
        return list
    }
}
