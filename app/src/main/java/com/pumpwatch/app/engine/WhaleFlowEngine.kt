package com.pumpwatch.app.engine

import com.pumpwatch.app.data.AggTradeNormalized
import com.pumpwatch.app.data.WhaleProviders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * نتیجهٔ جریان واقعی نهنگ‌ها از زنجیرهٔ چندصرافی.
 *
 * 🚀 Commit 83 (W2 + W9): فیلدهای جدید برای صداقت زمانی و حالت نهنگ صفر.
 * 🚀 Commit 87 (W8): فیلد `adaptiveThresholdUsed` برای شفافیت آستانهٔ استفاده‌شده.
 *
 * @property symbol نماد (مثلاً BTCUSDT)
 * @property source نام منبعی که واقعاً پاسخ داد (BINANCE / BYBIT / OKX / GATE / GECKO_DEX)
 * @property fetchedAt زمان دریافت داده (epoch ms) — برای شفافیت تازگی
 * @property windowStartMs قدیمی‌ترین ترید در پنجره (epoch ms)
 * @property windowEndMs جدیدترین ترید در پنجره (epoch ms)
 * @property windowTrades تعداد کل تریدهای درون پنجره
 * @property whaleTrades تعداد تریدهای نهنگی (≥ آستانه)
 * @property whaleBuyNotional مجموع حجم خرید نهنگ‌ها (دلار)
 * @property whaleSellNotional مجموع حجم فروش نهنگ‌ها (دلار)
 * @property largestTrade بزرگ‌ترین معاملهٔ نهنگی (دلار)
 * @property buyRatio نسبت خرید نهنگ به کل (۰ تا ۱)
 * @property pressure حالت جریان:
 *   - "ACCUMULATION" (ratio ≥ 0.6)
 *   - "DISTRIBUTION" (ratio ≤ 0.4)
 *   - "BALANCED" (0.4 < ratio < 0.6)
 *   - "NO_WHALE_ACTIVITY" (whaleTrades == 0)
 * @property adaptiveThresholdUsed آستانهٔ واقعی استفاده‌شده (دلار) — برای شفافیت
 *
 * اگر null برگردد یعنی هیچ منبعی داده نداشت.
 */
data class WhaleFlowResult(
    val symbol: String,
    val source: String,
    val fetchedAt: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val windowTrades: Int,
    val whaleTrades: Int,
    val whaleBuyNotional: Double,
    val whaleSellNotional: Double,
    val largestTrade: Double,
    val buyRatio: Double,
    val pressure: String,
    val adaptiveThresholdUsed: Double  // 🚀 Commit 87 (W8)
)

/**
 * WhaleFlowEngine — تنها منبع حقیقت برای "نهنگ واقعی"
 *
 * تعریف نهنگ: معاملهٔ تکی با ارزش >= آستانه (پیش‌فرض ۱۰۰ هزار دلار).
 * جهت معامله: buyerIsMaker == true یعنی فروش تهاجمی (SELL)، وگرنه BUY.
 *
 * زنجیرهٔ fallback چندصرافی با کف آن‌چین:
 * Binance → Bybit → OKX → Gate → GeckoTerminal DEX
 *
 * 🚀 Commit 83:
 *   - محاسبهٔ windowStartMs/windowEndMs از min/max timestamp تریدها
 *   - حالت NO_WHALE_ACTIVITY جدا از BALANCED
 *
 * 🚀 Commit 87 (W8):
 *   - آستانهٔ نسبی بر اساس حجم ۲۴ ساعته یا صدک ۹۹ تریدها
 *   - فیلد adaptiveThresholdUsed در خروجی برای شفافیت
 */
object WhaleFlowEngine {

    const val SOURCE_BINANCE = "BINANCE_AGG"
    private const val DEFAULT_WHALE_THRESHOLD = 100_000.0

    // 🚀 Commit 87 (W8): Floor و Ceiling برای آستانهٔ نسبی
    private const val MIN_THRESHOLD = 10_000.0
    private const val MAX_THRESHOLD = 500_000.0
    private const val VOLUME_RATIO = 0.001  // ۰.۱٪ از حجم ۲۴ ساعته
    private const val MIN_TRADES_FOR_PERCENTILE = 100

    /** ثابت‌های pressure برای اجتناب از typo و سازگاری با UI */
    const val PRESSURE_ACCUMULATION = "ACCUMULATION"
    const val PRESSURE_DISTRIBUTION = "DISTRIBUTION"
    const val PRESSURE_BALANCED = "BALANCED"
    const val PRESSURE_NO_WHALE = "NO_WHALE_ACTIVITY"

    /**
     * 🚀 Commit 87 (W8): پارامتر `volume24h` اضافه شد برای آستانهٔ نسبی.
     *
     * @param symbol نماد ارز (مثلاً BTCUSDT)
     * @param volume24h حجم ۲۴ ساعته (دلار) — اگر null باشد، از صدک ۹۹ تریدها استفاده می‌شود
     * @param whaleThresholdUsd آستانهٔ دستی — اگر null باشد، نسبی محاسبه می‌شود
     * @param limit تعداد تریدهای درخواستی
     */
    suspend fun analyze(
        symbol: String,
        volume24h: Double? = null,
        whaleThresholdUsd: Double? = null,
        limit: Int = 1000
    ): WhaleFlowResult? {
        for (provider in WhaleProviders.all) {
            val trades: List<AggTradeNormalized> = try {
                provider.fetchNormalized(symbol, limit)
            } catch (_: Exception) {
                emptyList()
            }
            if (trades.isEmpty()) continue
            val result = computeFromTrades(symbol, provider.name, trades, whaleThresholdUsd, volume24h)
            if (result != null) return result
        }
        return null
    }

    /**
     * 🚀 Commit 83: تست‌پذیر به‌عنوان یک تابع pure (بدون suspend).
     * 🚀 Commit 87 (W8): پارامتر `volume24h` اضافه شد.
     *
     * ورودی: لیست تریدها + volume24h اختیاری. خروجی: WhaleFlowResult.
     */
    fun computeFromTrades(
        symbol: String,
        source: String,
        trades: List<AggTradeNormalized>,
        whaleThresholdUsd: Double? = null,
        volume24h: Double? = null
    ): WhaleFlowResult? {
        if (trades.isEmpty()) return null

        // 🚀 Commit 87 (W8): محاسبهٔ آستانهٔ نسبی
        val threshold = computeAdaptiveThreshold(trades, volume24h, whaleThresholdUsd)

        var buy = 0.0
        var sell = 0.0
        var largest = 0.0
        var whaleCount = 0
        var total = 0

        // محاسبهٔ min/max timestamp برای پنجرهٔ واقعی
        var minTs = Long.MAX_VALUE
        var maxTs = Long.MIN_VALUE

        for (t in trades) {
            val notional = t.notional
            total++
            if (t.time < minTs) minTs = t.time
            if (t.time > maxTs) maxTs = t.time

            if (notional >= threshold) {
                whaleCount++
                if (notional > largest) largest = notional
                if (t.buyerIsMaker) sell += notional else buy += notional
            }
        }

        // حالت NO_WHALE_ACTIVITY وقتی هیچ ترید نهنگی نیست
        val pressure = if (whaleCount == 0) {
            PRESSURE_NO_WHALE
        } else {
            val ratio = buy / (buy + sell)
            when {
                ratio >= 0.6 -> PRESSURE_ACCUMULATION
                ratio <= 0.4 -> PRESSURE_DISTRIBUTION
                else -> PRESSURE_BALANCED
            }
        }

        val ratio = if (buy + sell > 0) buy / (buy + sell) else 0.5

        return WhaleFlowResult(
            symbol = symbol,
            source = source,
            fetchedAt = System.currentTimeMillis(),
            windowStartMs = if (minTs == Long.MAX_VALUE) 0L else minTs,
            windowEndMs = if (maxTs == Long.MIN_VALUE) 0L else maxTs,
            windowTrades = total,
            whaleTrades = whaleCount,
            whaleBuyNotional = buy,
            whaleSellNotional = sell,
            largestTrade = largest,
            buyRatio = ratio,
            pressure = pressure,
            adaptiveThresholdUsed = threshold  // 🚀 Commit 87 (W8)
        )
    }

    /**
     * 🚀 Commit 87 (W8): محاسبهٔ آستانهٔ نسبی بر اساس حجم ۲۴ ساعته یا صدک ۹۹.
     *
     * رویکرد ترکیبی (Hybrid):
     * 1. اگر `userThreshold` مشخص شده باشد، از آن استفاده کن (override دستی)
     * 2. اگر `volume24h` موجود است، threshold = volume24h * 0.001 (با floor/ceiling)
     * 3. اگر نیست ولی داده کافی داریم (≥ 100 ترید)، صدک ۹۹ اندازهٔ تریدها
     * 4. Fallback: 100K
     *
     * @param trades لیست تریدها
     * @param volume24h حجم ۲۴ ساعته (دلار) — اختیاری
     * @param userThreshold آستانهٔ دستی — اختیاری
     * @return آستانهٔ محاسبه‌شده (دلار)
     */
    internal fun computeAdaptiveThreshold(
        trades: List<AggTradeNormalized>,
        volume24h: Double?,
        userThreshold: Double?
    ): Double {
        // ۱. Override دستی (اگر کاربر مشخص کرده)
        if (userThreshold != null && userThreshold > 0) {
            return userThreshold
        }

        // ۲. Volume-based: ۰.۱٪ از حجم ۲۴ ساعته
        if (volume24h != null && volume24h > 0) {
            val volumeBased = volume24h * VOLUME_RATIO
            return volumeBased.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        }

        // ۳. Percentile-based: صدک ۹۹ اندازهٔ تریدها
        if (trades.size >= MIN_TRADES_FOR_PERCENTILE) {
            return percentile99(trades)
        }

        // ۴. Fallback: 100K
        return DEFAULT_WHALE_THRESHOLD
    }

    /**
     * محاسبهٔ صدک ۹۹ اندازهٔ تریدها.
     *
     * صدک ۹۹ یعنی ۹۹٪ تریدها کوچکتر از این مقدارند.
     * این مقدار به‌عنوان "آستانهٔ نهنگ" در نظر گرفته می‌شود.
     */
    private fun percentile99(trades: List<AggTradeNormalized>): Double {
        val notionals = trades.map { it.notional }.sorted()
        if (notionals.isEmpty()) return DEFAULT_WHALE_THRESHOLD
        val index = (notionals.size * 0.99).toInt().coerceAtMost(notionals.size - 1)
        return notionals[index].coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }
}

/**
 * 🚀 Commit 83 (W2): برچسب زمانی واقعی از روی min/max timestamp تریدها.
 *
 * این تابع top-level در package `engine` است تا از package‌های دیگر
 * (UI و تست) در دسترس باشد.
 *
 * قبلاً همیشه "۱ ساعته" نمایش داده می‌شد که دروغ بود (برای BTC چند دقیقه،
 * برای آلت‌کوین‌های کم‌حجم چند روز). حالا:
 *   - اگر پنجره < 60 دقیقه: "از HH:mm تا HH:mm (N ترید، X دقیقه)"
 *   - اگر پنجره >= 60 دقیقه: "از HH:mm تا HH:mm (N ترید، X ساعت)"
 *   - اگر پنجره >= 24 ساعت: "از MM/dd تا MM/dd (N ترید، X روز)"
 *   - اگر unknown: "N ترید (پنجره نامشخص)"
 */
fun formatWindowLabel(startMs: Long, endMs: Long, trades: Int): String {
    if (startMs <= 0L || endMs <= 0L || endMs <= startMs) {
        return "$trades ترید (پنجره نامشخص)"
    }
    val durationMin = ((endMs - startMs) / 60_000L).coerceAtLeast(1)
    val sdfMin = SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val sdfHour = SimpleDateFormat("MM/dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val sdfDay = SimpleDateFormat("MM/dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return when {
        durationMin < 60 -> {
            "از ${sdfMin.format(Date(startMs))} تا ${sdfMin.format(Date(endMs))} ($trades ترید، $durationMin دقیقه)"
        }
        durationMin < 24 * 60 -> {
            val hours = durationMin / 60
            "از ${sdfHour.format(Date(startMs))} تا ${sdfHour.format(Date(endMs))} ($trades ترید، ~$hours ساعت)"
        }
        else -> {
            val days = durationMin / (24 * 60)
            "از ${sdfDay.format(Date(startMs))} تا ${sdfDay.format(Date(endMs))} ($trades ترید، ~$days روز)"
        }
    }
}
