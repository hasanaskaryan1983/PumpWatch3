package com.pumpwatch.app.engine

import com.pumpwatch.app.data.AggTradeNormalized
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class WhaleFlowSampleMaxTest {

    private fun trades(n: Int, notional: Double): List<AggTradeNormalized> =
        (1..n).map { AggTradeNormalized(100.0, notional / 100.0, it.toLong(), false) }

    @Test
    fun sampleMaxReportedEvenWhenNoWhale() {
        val result = WhaleFlowEngine.computeFromTrades("BTCUSDT", "BYBIT", trades(150, 5_000.0))
        assertTrue(result != null)
        assertEquals(0, result!!.whaleTrades)
        assertEquals(0.0, result.largestTrade, 0.01)
        assertEquals(5_000.0, result.largestSampleTrade, 0.01)
        assertEquals(WhaleFlowEngine.PRESSURE_NO_WHALE, result.pressure)
    }

    @Test
    fun allZeroNotionalIsFailClosed() {
        val zero = (1..10).map { AggTradeNormalized(0.0, 5.0, it.toLong(), false) }
        assertNull(WhaleFlowEngine.computeFromTrades("BTCUSDT", "BYBIT", zero))
    }

    @Test
    fun sampleMaxAtLeastWhaleMax() {
        val list = trades(150, 5_000.0) + AggTradeNormalized(100.0, 3_000.0, 999L, false)
        val result = WhaleFlowEngine.computeFromTrades("BTCUSDT", "BYBIT", list)
        assertTrue(result != null)
        assertTrue(result!!.largestSampleTrade >= result.largestTrade)
        assertTrue(result.largestSampleTrade > 0.0)
    }
}
