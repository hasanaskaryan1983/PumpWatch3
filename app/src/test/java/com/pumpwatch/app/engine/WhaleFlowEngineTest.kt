package com.pumpwatch.app.engine

import com.pumpwatch.app.data.AggTradeNormalized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🚀 Commit 83: تست pure برای WhaleFlowEngine.computeFromTrades.
 *
 * این تابع حالا pure است (بدون suspend) و می‌توان در JVM unit test صدا زد.
 *
 * 🚀 Commit 83-fix: threshold در تست‌های accumulation/distribution
 *   به 50_000 کاهش یافت تا هر دو trade به‌عنوان «نهنگ» محاسبه شوند.
 *   قبلاً با threshold = 100_000، یکی از trade‌ها زیر آستانه بود و
 *   whaleTrades = 1 می‌شد → buyRatio محاسبه‌شده با انتظار mismatch بود.
 */
class WhaleFlowEngineTest {

    @Test
    fun `empty trades returns null`() {
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = emptyList(),
            whaleThresholdUsd = 100_000.0
        )
        assertNull(result)
    }

    @Test
    fun `window start and end calculated from min max timestamps`() {
        val trades = listOf(
            AggTradeNormalized(price = 50000.0, qty = 0.5, time = 1000L, buyerIsMaker = false),
            AggTradeNormalized(price = 50100.0, qty = 0.3, time = 5000L, buyerIsMaker = false),
            AggTradeNormalized(price = 50200.0, qty = 3.0, time = 3000L, buyerIsMaker = false)
        )
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = trades,
            whaleThresholdUsd = 100_000.0
        )
        assertNotNull(result)
        assertEquals(1000L, result!!.windowStartMs)
        assertEquals(5000L, result.windowEndMs)
        assertEquals(3, result.windowTrades)
    }

    @Test
    fun `no whale trades results in NO_WHALE_ACTIVITY`() {
        val trades = listOf(
            AggTradeNormalized(price = 50000.0, qty = 0.5, time = 1000L, buyerIsMaker = false), // 25K
            AggTradeNormalized(price = 50000.0, qty = 0.3, time = 2000L, buyerIsMaker = true)   // 15K
        )
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = trades,
            whaleThresholdUsd = 100_000.0
        )
        assertNotNull(result)
        assertEquals(0, result!!.whaleTrades)
        assertEquals(WhaleFlowEngine.PRESSURE_NO_WHALE, result.pressure)
        assertEquals(1000L, result.windowStartMs)
        assertEquals(2000L, result.windowEndMs)
    }

    @Test
    fun `accumulation when buy ratio 60 percent or more`() {
        // 🚀 Commit 83-fix: threshold = 50_000 تا هر دو trade به‌عنوان «نهنگ» محاسبه شوند
        val trades = listOf(
            AggTradeNormalized(price = 50000.0, qty = 3.0, time = 1000L, buyerIsMaker = false), // buy 150K
            AggTradeNormalized(price = 50000.0, qty = 1.0, time = 2000L, buyerIsMaker = true)   // sell 50K
        )
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = trades,
            whaleThresholdUsd = 50_000.0   // ← کاهش آستانه
        )
        assertNotNull(result)
        assertEquals(2, result!!.whaleTrades)
        assertEquals(150_000.0, result.whaleBuyNotional, 0.01)
        assertEquals(50_000.0, result.whaleSellNotional, 0.01)
        assertEquals(WhaleFlowEngine.PRESSURE_ACCUMULATION, result.pressure)
        assertEquals(0.75, result.buyRatio, 0.001)
    }

    @Test
    fun `distribution when buy ratio 40 percent or less`() {
        // 🚀 Commit 83-fix: threshold = 50_000 تا هر دو trade به‌عنوان «نهنگ» محاسبه شوند
        val trades = listOf(
            AggTradeNormalized(price = 50000.0, qty = 1.0, time = 1000L, buyerIsMaker = false), // buy 50K
            AggTradeNormalized(price = 50000.0, qty = 3.0, time = 2000L, buyerIsMaker = true)   // sell 150K
        )
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = trades,
            whaleThresholdUsd = 50_000.0   // ← کاهش آستانه
        )
        assertNotNull(result)
        assertEquals(2, result!!.whaleTrades)
        assertEquals(WhaleFlowEngine.PRESSURE_DISTRIBUTION, result.pressure)
        assertEquals(0.25, result.buyRatio, 0.001)
    }

    @Test
    fun `balanced when ratio between 40 and 60 percent`() {
        val trades = listOf(
            AggTradeNormalized(price = 50000.0, qty = 2.5, time = 1000L, buyerIsMaker = false), // buy 125K
            AggTradeNormalized(price = 50000.0, qty = 2.0, time = 2000L, buyerIsMaker = true)   // sell 100K
        )
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = trades,
            whaleThresholdUsd = 100_000.0
        )
        assertNotNull(result)
        assertEquals(WhaleFlowEngine.PRESSURE_BALANCED, result!!.pressure)
        assertEquals(125.0 / 225.0, result.buyRatio, 0.001)
    }

    @Test
    fun `largest trade tracked correctly`() {
        val trades = listOf(
            AggTradeNormalized(price = 50000.0, qty = 3.0, time = 1000L, buyerIsMaker = false), // 150K
            AggTradeNormalized(price = 50000.0, qty = 5.0, time = 2000L, buyerIsMaker = false), // 250K (بزرگ‌ترین)
            AggTradeNormalized(price = 50000.0, qty = 4.0, time = 3000L, buyerIsMaker = false)  // 200K
        )
        val result = WhaleFlowEngine.computeFromTrades(
            symbol = "BTCUSDT",
            source = "BINANCE",
            trades = trades,
            whaleThresholdUsd = 100_000.0
        )
        assertNotNull(result)
        assertEquals(250_000.0, result!!.largestTrade, 0.01)
    }

    @Test
    fun `formatWindowLabel handles unknown window`() {
        assertEquals("5 ترید (پنجره نامشخص)", formatWindowLabel(0L, 0L, 5))
        assertEquals("10 ترید (پنجره نامشخص)", formatWindowLabel(1000L, 500L, 10)) // end < start
    }

    @Test
    fun `formatWindowLabel under 60 minutes shows minutes`() {
        val start = 1727500000000L
        val end = start + 30 * 60 * 1000L // 30 minutes
        val label = formatWindowLabel(start, end, 1000)
        assert(label.contains("30 دقیقه")) { "expected minutes, got: $label" }
        assert(label.contains("1000 ترید")) { "expected trade count, got: $label" }
    }

    @Test
    fun `formatWindowLabel over 60 minutes shows hours`() {
        val start = 1727500000000L
        val end = start + 3 * 60 * 60 * 1000L // 3 hours
        val label = formatWindowLabel(start, end, 1000)
        assert(label.contains("3 ساعت")) { "expected hours, got: $label" }
    }

    @Test
    fun `formatWindowLabel over 24 hours shows days`() {
        val start = 1727500000000L
        val end = start + 5L * 24 * 60 * 60 * 1000L // 5 days
        val label = formatWindowLabel(start, end, 1000)
        assert(label.contains("5 روز")) { "expected days, got: $label" }
    }
}
