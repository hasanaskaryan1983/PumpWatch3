package com.pumpwatch.app.engine

import com.pumpwatch.app.data.WhaleClient

/**
 * نتیجهٔ جریان واقعی نهنگ‌ها از Binance aggTrades
 * source همیشه "BINANCE_AGG" است؛ اگر null برگردد یعنی داده موجود نیست
 * و UI باید برچسب "تخمین از DEX" نشان دهد.
 */
data class WhaleFlowResult(
    val symbol: String,
    val source: String,
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
 */
object WhaleFlowEngine {

    const val SOURCE_BINANCE = "BINANCE_AGG"
    private const val DEFAULT_WHALE_THRESHOLD = 100_000.0

    suspend fun analyze(
        symbol: String,
        whaleThresholdUsd: Double = DEFAULT_WHALE_THRESHOLD,
        limit: Int = 1000
    ): WhaleFlowResult? {
        return try {
            val trades = WhaleClient.api.aggTrades(symbol, limit)
            if (trades.isEmpty()) return null

            var buy = 0.0
            var sell = 0.0
            var largest = 0.0
            var whaleCount = 0
            var total = 0

            for (t in trades) {
                val p = t.price?.toDoubleOrNull() ?: continue
                val q = t.qty?.toDoubleOrNull() ?: continue
                val notional = p * q
                total++
                if (notional >= whaleThresholdUsd) {
                    whaleCount++
                    if (notional > largest) largest = notional
                    if (t.buyerIsMaker == true) sell += notional else buy += notional
                }
            }

            val ratio = if (buy + sell > 0) buy / (buy + sell) else 0.5
            val pressure = when {
                ratio >= 0.6 -> "ACCUMULATION"
                ratio <= 0.4 -> "DISTRIBUTION"
                else -> "BALANCED"
            }

            WhaleFlowResult(
                symbol = symbol,
                source = SOURCE_BINANCE,
                windowTrades = total,
                whaleTrades = whaleCount,
                whaleBuyNotional = buy,
                whaleSellNotional = sell,
                largestTrade = largest,
                buyRatio = ratio,
                pressure = pressure
            )
        } catch (_: Exception) {
            null
        }
    }
}
