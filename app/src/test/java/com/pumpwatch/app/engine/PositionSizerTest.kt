package com.pumpwatch.app.engine

import org.junit.Assert.*
import org.junit.Test

class PositionSizerTest {

    @Test
    fun `below MIN_TRADES returns fallback sizing with no kelly`() {
        val result = PositionSizer.fromPnlPercents(
            listOf(5.0, -3.0, 2.0, -1.0, 4.0),
            capital = 1000.0
        )
        assertFalse("Should not have enough data", result.hasEnoughData)
        assertEquals("Kelly should be 0", 0.0, result.kellyFraction, 0.0001)
        // fallback = 5% of 1000 = 50$ (within MIN_SIZE_USD..MAX_CAPITAL_PCT range)
        assertTrue("Size should be near 50$", result.sizeUsd in 45.0..55.0)
        assertTrue("Label should mention insufficient data", result.label.contains("ناکافی"))
    }

    @Test
    fun `empty PnL list returns fallback`() {
        val result = PositionSizer.fromPnlPercents(emptyList(), 1000.0)
        assertFalse(result.hasEnoughData)
    }

    @Test
    fun `zero capital returns zero size`() {
        val result = PositionSizer.fromPnlPercents(listOf(1.0, 2.0), 0.0)
        assertEquals(0.0, result.sizeUsd, 0.0001)
    }

    @Test
    fun `negative capital returns zero size`() {
        val result = PositionSizer.fromPnlPercents(List(25) { 5.0 }, -100.0)
        assertEquals(0.0, result.sizeUsd, 0.0001)
    }

    @Test
    fun `high winrate high RR respects MAX_CAPITAL_PCT`() {
        // 18 wins +30%, 2 losses -10% → extremely good strategy
        val pnls = mutableListOf<Double>()
        repeat(18) { pnls.add(30.0) }
        repeat(2) { pnls.add(-10.0) }
        val result = PositionSizer.fromPnlPercents(pnls, 10000.0)
        assertTrue(result.hasEnoughData)
        assertTrue("Kelly should be positive", result.kellyFraction > 0.0)
        assertTrue("Kelly should be capped at MAX_KELLY",
            result.kellyFraction <= PositionSizer.MAX_KELLY + 0.0001)
        assertTrue("Used fraction capped at MAX_CAPITAL_PCT",
            result.usedFraction <= PositionSizer.MAX_CAPITAL_PCT + 0.0001)
    }

    @Test
    fun `losing strategy gives near-zero Kelly`() {
        // 2 wins +5%, 18 losses -10% → losing
        val pnls = mutableListOf<Double>()
        repeat(2) { pnls.add(5.0) }
        repeat(18) { pnls.add(-10.0) }
        val result = PositionSizer.fromPnlPercents(pnls, 1000.0)
        assertTrue(result.hasEnoughData)
        assertTrue("Kelly should be very small", result.kellyFraction < 0.01)
        // Half Kelly ≈ 0 → size coerced to MIN_SIZE_USD
        assertEquals("Size should be minimum",
            PositionSizer.MIN_SIZE_USD, result.sizeUsd, 0.01)
    }

    @Test
    fun `all wins no losses uses half of max Kelly`() {
        val pnls = List(20) { 10.0 }
        val result = PositionSizer.fromPnlPercents(pnls, 1000.0)
        assertTrue(result.hasEnoughData)
        // Without losses, avgLoss=0 → falls back to MAX_KELLY * 0.5
        assertEquals(PositionSizer.MAX_KELLY * 0.5, result.kellyFraction, 0.0001)
    }

    @Test
    fun `size never exceeds capital and respects MIN_SIZE_USD`() {
        val result = PositionSizer.fromPnlPercents(List(20) { 10.0 }, capital = 100.0)
        assertTrue("Size <= capital", result.sizeUsd <= 100.0)
        assertTrue("Size >= MIN_SIZE_USD", result.sizeUsd >= PositionSizer.MIN_SIZE_USD)
    }

    @Test
    fun `label includes kelly W R info when enough data`() {
        val pnls = mutableListOf<Double>()
        repeat(15) { pnls.add(5.0) }
        repeat(10) { pnls.add(-3.0) }
        val result = PositionSizer.fromPnlPercents(pnls, 1000.0)
        assertTrue("Label should contain Kelly", result.label.contains("Kelly"))
        assertTrue("Label should contain W=", result.label.contains("W="))
        assertTrue("Label should contain R=", result.label.contains("R="))
    }

    @Test
    fun `exactly MIN_TRADES counts as enough data`() {
        val pnls = List(PositionSizer.MIN_TRADES) { 5.0 }
        val result = PositionSizer.fromPnlPercents(pnls, 1000.0)
        assertTrue("Exactly MIN_TRADES should be enough", result.hasEnoughData)
    }
}
