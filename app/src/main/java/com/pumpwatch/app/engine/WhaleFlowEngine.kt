package com.pumpwatch.app.engine

import com.pumpwatch.app.data.AggTradeNormalized
import com.pumpwatch.app.data.WhaleProviders

/**
 * نتیجهٔ جریان واقعی نهنگ‌ها از زنجیرهٔ چندصرافی.
 *
 * 🚀 Commit 83 (فاز ۳ — W2 + W9): فیلدهای جدید برای صداقت زمانی و حالت نهنگ صفر.
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
    val pressure: String
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
 */
object WhaleFlowEngine {

    const val SOURCE_BINANCE = "BINANCE_AGG"
    private const val DEFAULT_WHALE_THRESHOLD = 100_000.0

    /** ثابت‌های pressure برای اجتناب از typo و سازگاری با UI */
    const val PRESSURE_ACCUMULATION = "ACCUMULATION"
    const val PRESSURE_DISTRIBUTION = "DISTRIBUTION"
    const val PRESSURE_BALANCED = "BALANCED"
    const val PRESSURE_NO_WHALE = "NO_WHALE_ACTIVITY"

    suspend fun analyze(
        symbol: String,
        whaleThresholdUsd: Double = DEFAULT_WHALE_THRESHOLD,
        limit: Int = 1000
    ): WhaleFlowResult? {
        for (provider in WhaleProviders.all) {
            val trades: List<AggTradeNormalized> = try {
                provider.fetchNormalized(symbol, limit)
            } catch (_: Exception) {
                emptyList()
            }
            if (trades.isEmpty()) continue
            val result = computeFromTrades(symbol, provider.name, trades, whaleThresholdUsd)
            if (result != null) return result
        }
        return null
    }

    /**
     * 🚀 Commit 83: تست‌پذیر به‌عنوان یک تابع pure (بدون suspend).
     * ورودی: لیست تریدها. خروجی: WhaleFlowResult.
     */
    fun computeFromTrades(
        symbol: String,
        source: String,
        trades: List<AggTradeNormalized>,
        whaleThresholdUsd: Double
    ): WhaleFlowResult? {
        if (trades.isEmpty()) return null

        var buy = 0.0
        var sell = 0.0
        var largest = 0.0
        var whaleCount = 0
        var total = 0

        // 🚀 Commit 83: min/max timestamp برای پنجرهٔ واقعی
        var minTs = Long.MAX_VALUE
        var maxTs = Long.MIN_VALUE

        for (t in trades) {
            val notional = t.notional
            total++
            if (t.time < minTs) minTs = t.time
            if (t.time > maxTs) maxTs = t.time

            if (notional >= whaleThresholdUsd) {
                whaleCount++
                if (notional > largest) largest = notional
                if (t.buyerIsMaker) sell += notional else buy += notional
            }
        }

        // 🚀 Commit 83: NO_WHALE_ACTIVITY وقتی هیچ ترید نهنگی نیست
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
            pressure = pressure
        )
    }
}
