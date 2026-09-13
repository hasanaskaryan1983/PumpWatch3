package com.pumpwatch.app.engine

import com.pumpwatch.app.data.AggTradeNormalized
import com.pumpwatch.app.data.WhaleProviders

/**
 * نتیجهٔ جریان واقعی نهنگ‌ها از زنجیرهٔ چندصرافی
 * source = نام منبعی که واقعاً پاسخ داده (BINANCE / BYBIT / OKX / GATE / GECKO_DEX)
 * fetchedAt = زمان دریافت داده (epoch millis) برای شفافیت تازگی
 * اگر null برگردد یعنی هیچ منبعی داده نداشت و UI باید برچسب «تخمین از DEX» نشان دهد.
 */
data class WhaleFlowResult(
    val symbol: String,
    val source: String,
    val fetchedAt: Long,
    val windowTrades: Int,
    val whaleTrades: Int,
    val whaleBuyNotional: Double,
    val whaleSellNotional: Double,
    val largestTrade: Double,
    val buyRatio: Double,
    val pressure: String // "ACCUMULATION" | "DISTRIBUTION" | "BALANCED"
)

/**
 * WhaleFlowEngine — تنها منبع حقیقت برای "نهنگ واقعی"
 * تعریف نهنگ: معاملهٔ تکی با ارزش >= آستانه (پیش‌فرض ۱۰۰ هزار دلار)
 * جهت معامله: buyerIsMaker == true یعنی فروش تهاجمی (SELL)، وگرنه BUY
 *
 * P0-2: زنجیرهٔ fallback چندصرافی با کف آن‌چین:
 * Binance → Bybit → OKX → Gate → GeckoTerminal DEX
 * اولین منبعی که ترید غیرخالی برگرداند برنده است و نامش در `source` ثبت می‌شود.
 * قطع Binance دیگر کل قابلیت را خالی نمی‌کند.
 */
object WhaleFlowEngine {

    /** ثابت قدیمی برای سازگاری؛ UI جدید هم "BINANCE_AGG" و هم "BINANCE" را می‌شناسد */
    const val SOURCE_BINANCE = "BINANCE_AGG"
    private const val DEFAULT_WHALE_THRESHOLD = 100_000.0

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

    private fun computeFromTrades(
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

        for (t in trades) {
            val notional = t.notional
            total++
            if (notional >= whaleThresholdUsd) {
                whaleCount++
                if (notional > largest) largest = notional
                if (t.buyerIsMaker) sell += notional else buy += notional
            }
        }

        val ratio = if (buy + sell > 0) buy / (buy + sell) else 0.5
        val pressure = when {
            ratio >= 0.6 -> "ACCUMULATION"
            ratio <= 0.4 -> "DISTRIBUTION"
            else -> "BALANCED"
        }

        return WhaleFlowResult(
            symbol = symbol,
            source = source,
            fetchedAt = System.currentTimeMillis(),
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
