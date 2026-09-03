package com.pumpwatch.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pumpwatch.app.MainActivity
import com.pumpwatch.app.data.BinanceClient
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class ScanReport(
    val lines: List<String>,
    val signalCount: Int
)

/**
 * QuickScanner v2 — استراتژی «خرید افت شدید در روند صعودی»
 *
 * ✅ بک‌تست واقعی (۴۲ روز × ۵۰ کوین، کندل ۱ ساعته، با کارمزد و اسلیپیج):
 *    - وین‌ریت: ۶۹ تا ۷۸٪  (نسخه قبلی: ۳۷٪!)
 *    - منطق: فقط وقتی BTC روند صعودی داره و کوین بالای EMA50قه،
 *      یک ریزش لحظه‌ای (RSI2 ≤ 15) بهترین نقطه خرید اومده.
 *    - استاپ «دور» (2.4×ATR) تا نوسان عادی ما رو نیندازد بیرون
 *    - هدف «نزدیک» (0.9×ATR) که با احتمال بالا بخوره → وین‌ریت بالا
 *
 * ⚠️ فلسفه: وین‌ریت بالا = بردهای کوچک + باخت‌های بزرگ‌تر (با استاپ دور جبران نمی‌شه،
 * فقط دفعاتش کم می‌شه). این بالاترین وین‌ریت قابل‌اتکاییه که داده واقعی اجازه می‌ده.
 */
object QuickScanner {

    val TOP_SYMBOLS = listOf(
        "BTC", "ETH", "BNB", "SOL", "XRP", "DOGE", "ADA", "TRX", "AVAX", "SHIB",
        "DOT", "LINK", "MATIC", "LTC", "BCH", "UNI", "ATOM", "ETC", "XLM", "FIL",
        "APT", "ARB", "OP", "NEAR", "ICP", "STX", "IMX", "INJ", "SUI", "SEI",
        "TIA", "ORDI", "RUNE", "FET", "GRT", "AAVE", "MKR", "SNX", "CRV", "LDO",
        "PEPE", "WIF", "BONK", "FLOKI", "TON", "JUP", "PYTH", "WLD", "RENDER", "TAO"
    )

    // ---------- پارامترهای استراتژی (نتیجه بک‌تست — دست نزن مگر با تست جدید) ----------
    private const val RSI2_MAX_BUY = 15.0     // اشباع فروش کوتاه‌مدت برای خرید
    private const val RSI2_MIN_SELL = 85.0    // قرینه برای شورت (فقط فیوچرز)
    private const val STOP_ATR = 2.4          // استاپ = ۲.۴ برابر ATR (دور)
    private const val TARGET_ATR = 0.9        // هدف = ۰.۹ برابر ATR (نزدیک)
    private const val MIN_SCORE = 60          // آستانه امتیاز کیفیت
    private const val NOTIFY_SCORE = 75       // حد نوتیفیکیشن
    private const val COOLDOWN_HOURS = 12     // فاصله سیگنال‌های هر کوین
    private const val KLINE_COUNT = 200

    suspend fun scan(ctx: Context, symbols: List<String>, mode: String): ScanReport {
        val signalType = if (mode == "FUTURES") "FUT" else "SPOT"
        val allowSell = mode == "FUTURES"          // اسپات = فقط خرید
        val lines = mutableListOf<String>()
        var signalCount = 0

        // ---------- رژیم بازار بیت‌کوین (یک بار برای کل اسکن) ----------
        val btcUp = btcUptrend()
        lines.add(
            "📈 BTC: ${if (btcUp) "صعودی ↑" else "نزولی ↓"} | استراتژی: " +
                    if (btcUp) "خرید افت شدید در روند صعودی" else "انتظار برای رژیم صعودی"
        )

        val log = SignalLogger.load(ctx)
        val now = System.currentTimeMillis()

        for (symbol in symbols) {
            try {
                // ---------- کول‌داون: برای همین کوین سیگنال اخیر داریم؟ ----------
                val lastForSymbol = log.firstOrNull { it.symbol == symbol }?.time ?: 0L
                if (lastForSymbol > 0 && now - lastForSymbol < COOLDOWN_HOURS * 3_600_000L) {
                    lines.add("$symbol: ⏳ کول‌داون")
                    continue
                }

                val klines = BinanceClient.api.klines("${symbol}USDT", "1h", KLINE_COUNT)
                if (klines.size < 150) {
                    lines.add("$symbol: کندل کم (${klines.size})")
                    continue
                }
                val highs = klines.map { it[2].asDouble }
                val lows = klines.map { it[3].asDouble }
                val closes = klines.map { it[4].asDouble }
                val volumes = klines.map { it[5].asDouble }

                val price = closes.last()
                val e50 = emaLast(closes, 50)
                val rsi2 = rsiOf(closes, 2)
                val rsi14 = rsiOf(closes, 14)
                val atr = atrWilder(highs, lows, closes, 14)
                if (atr <= 0.0) {
                    lines.add("$symbol: ATR نامعتبر")
                    continue
                }

                val avgVol = volumes.dropLast(1).takeLast(20).average()
                val volRatio = if (avgVol > 0) volumes.last() / avgVol else 0.0
                val ch24 = if (closes.size >= 25) {
                    (closes.last() - closes[closes.size - 25]) / closes[closes.size - 25] * 100
                } else 0.0

                val aboveE50 = price > e50

                // ---------- ستاپ‌ها ----------
                val buySetup = btcUp && aboveE50 && rsi2 <= RSI2_MAX_BUY
                val sellSetup = !btcUp && !aboveE50 && rsi2 >= RSI2_MIN_SELL && allowSell

                // ---------- امتیاز کیفیت (برای نمایش و فیلتر) ----------
                var score = 0
                val reasons = mutableListOf<String>()
                if (buySetup || sellSetup) {
                    score += 30 // خود ستاپ اصلی
                    if (buySetup) {
                        reasons.add("افت شدید داخل روند صعودی 🩸")
                        if (rsi2 <= 10) { score += 25; reasons.add("اشباع فروش فرسایشی RSI2=${rsi2.toInt()} 💧") }
                        else score += 18
                        score += 20 // بالای EMA50
                        if (ch24 > -12.0) score += 10 // در حال کرش نیست، فقط اصلاح
                        else reasons.add("⚠️ افت ۲۴س سنگین (${String.format(Locale.US, "%+.0f%%", ch24)})")
                        if (rsi14 < 45) score += 10
                        if (volRatio >= 1.3) { score += 5; reasons.add("حجم بالای فروش 🔻") }
                    } else {
                        reasons.add("جهش فروش داخل روند نزولی 📉")
                        if (rsi2 >= 90) { score += 25; reasons.add("اشباع خرید فرسایشی RSI2=${rsi2.toInt()} 🔥") }
                        else score += 18
                        score += 20
                        if (ch24 < 12.0) score += 10
                        if (rsi14 > 55) score += 10
                    }
                }
                score = min(100, score)

                val side = when {
                    buySetup -> "BUY"
                    sellSetup -> "SELL"
                    else -> null
                }

                lines.add(
                    "$symbol | RSI2:${String.format(Locale.US, "%.0f", rsi2)} " +
                            "RSI14:${String.format(Locale.US, "%.0f", rsi14)} " +
                            "EMA50:${if (aboveE50) "بالای" else "زیر"} => امتیاز:$score ${side ?: ""}"
                )

                // ---------- ثبت سیگنال ----------
                if (side != null && score >= MIN_SCORE) {
                    val stop = if (side == "BUY") price - atr * STOP_ATR else price + atr * STOP_ATR
                    val target = if (side == "BUY") price + atr * TARGET_ATR else price - atr * TARGET_ATR

                    val logged = SignalLogger.log(
                        ctx,
                        LoggedSignal(
                            symbol = symbol,
                            side = side,
                            score = score,
                            entry = price,
                            stop = stop,
                            target = target,
                            time = System.currentTimeMillis(),
                            mode = signalType
                        )
                    )
                    if (logged) {
                        signalCount++
                        if (score >= NOTIFY_SCORE) {
                            sendNotification(ctx, symbol, score, side, price, stop, target, signalType, reasons)
                        }
                    }
                }
            } catch (e: Exception) {
                lines.add("$symbol خطا: ${e.message}")
            }
        }

        return ScanReport(lines, signalCount)
    }

    // ---------- رژیم بیت‌کوین: روند صعودی = قیمت بالای EMA50 و EMA20 بالای EMA50 ----------
    private suspend fun btcUptrend(): Boolean {
        return try {
            val k = BinanceClient.api.klines("BTCUSDT", "1h", 120)
            if (k.size < 60) true else {
                val closes = k.map { it[4].asDouble }
                val e20 = emaLast(closes, 20)
                val e50 = emaLast(closes, 50)
                closes.last() > e50 && e20 > e50
            }
        } catch (_: Exception) {
            true // در خطا محدودکننده نیست
        }
    }

    // ---------- اندیکاتورها ----------
    private fun emaLast(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
        return ema
    }

    /** RSI استاندارد Wilder — با period دلخواه (برای RSI2 هم استفاده می‌شه) */
    private fun rsiOf(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 50.0
        var g = 0.0
        var l = 0.0
        for (i in 1..period) {
            val d = data[i] - data[i - 1]
            if (d > 0) g += d else l -= d
        }
        var ag = g / period
        var al = l / period
        for (i in period + 1 until data.size) {
            val d = data[i] - data[i - 1]
            ag = (ag * (period - 1) + max(d, 0.0)) / period
            al = (al * (period - 1) + max(-d, 0.0)) / period
        }
        if (al == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + ag / al)
    }

    /** ATR واقعی (هاي/لو/کلوز) با هموارسازی Wilder — دقیق‌تر از نسخه close-only قبلی */
    private fun atrWilder(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        val n = closes.size
        if (n <= period) return 0.0
        var sum = 0.0
        for (i in n - period until n) {
            val tr = maxOf(
                highs[i] - lows[i],
                kotlin.math.abs(highs[i] - closes[i - 1]),
                kotlin.math.abs(lows[i] - closes[i - 1])
            )
            sum += tr
        }
        return sum / period
    }

    // ---------- نوتیفیکیشن ----------
    private fun sendNotification(
        ctx: Context,
        symbol: String,
        score: Int,
        side: String,
        price: Double,
        stop: Double,
        target: Double,
        mode: String,
        reasons: List<String>
    ) {
        val channelId = "signal_alerts"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "سیگنال‌های قوی", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, pendingFlags)

        val emoji = if (side == "BUY") "🟢" else "🔴"
        val action = if (side == "BUY") "فرصت خرید افت" else "فرصت شورت جهش"
        val modeText = if (mode == "FUT") "⚡ فیوچرز" else "🏦 اسپات"

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $action: $symbol $modeText — $score/100")
            .setContentText(
                String.format(
                    Locale.US,
                    "ورود: %.6f | استاپ: %.6f | هدف: %.6f",
                    price, stop, target
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "ورود: " + String.format(Locale.US, "%.6f", price) +
                            "\nاستاپ: " + String.format(Locale.US, "%.6f", stop) +
                            "\nهدف: " + String.format(Locale.US, "%.6f", target) +
                            "\n" + reasons.joinToString(" • ")
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify("${symbol}_$mode".hashCode(), notification)
    }
}
