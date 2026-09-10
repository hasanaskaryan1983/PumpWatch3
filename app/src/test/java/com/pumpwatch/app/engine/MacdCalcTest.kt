package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MacdCalcTest {

    @Test
    fun `emaAligned returns nulls before period minus 1`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val e = MacdCalc.emaAligned(values, 3)
        assertEquals(5, e.size)
        assertNull("index 0 must be null", e[0])
        assertNull("index 1 must be null", e[1])
        assertNotNull("index 2 must be valid (SMA seed)", e[2])
        assertEquals(2.0, e[2]!!, 1e-9)
    }

    @Test
    fun `emaAligned handles short input`() {
        val short = listOf(1.0, 2.0)
        val e = MacdCalc.emaAligned(short, 5)
        assertTrue(e.all { it == null })
    }

    @Test
    fun `macdLine null before slow minus 1 and positive on uptrend`() {
        val closes = (1..60).map { it.toDouble() }
        val m = MacdCalc.macdLine(closes)
        assertEquals(60, m.size)
        for (i in 0 until 25) assertNull("MACD before index 25 must be null", m[i])
        val m40 = m[40]!!
        val m50 = m[50]!!
        assertTrue("MACD must be positive in uptrend", m40 > 0)
        assertTrue("MACD must stabilize", abs(m40 - m50) < 1e-6)
    }

    @Test
    fun `signalLine null when not enough data`() {
        val short = (1..30).map { it.toDouble() }
        val s = MacdCalc.signalLine(short)
        assertTrue("signal should be all null when data is short", s.all { it == null })
    }

    @Test
    fun `macdUp true in sustained uptrend (MACD above signal)`() {
        val flat = MutableList(60) { 100.0 }
        val up = MutableList(20) { 100.0 + it * 2.0 }
        assertTrue("uptrend must be bullish regime", MacdCalc.macdUp(flat + up))
    }

    @Test
    fun `macdUp false in sustained downtrend`() {
        val flat = MutableList(60) { 100.0 }
        val down = MutableList(20) { 100.0 - it * 2.0 }
        assertFalse("downtrend must not be bullish regime", MacdCalc.macdUp(flat + down))
    }

    @Test
    fun `macdUp false on flat series`() {
        val flat = MutableList(80) { 100.0 }
        assertFalse("flat series has MACD == signal", MacdCalc.macdUp(flat))
    }

    @Test
    fun `macdUp false on insufficient data`() {
        val small = listOf(1.0, 2.0, 3.0)
        assertFalse(MacdCalc.macdUp(small))
    }

    @Test
    fun `macdCrossUp detects fresh cross exactly on jump bar`() {
        val flat = MutableList(60) { 100.0 }
        val series = flat + listOf(102.0) // کراس دقیقاً در آخرین کندل
        assertTrue("fresh bullish cross must be detected", MacdCalc.macdCrossUp(series))
    }

    @Test
    fun `macdCrossUp false when cross happened earlier (sustained uptrend)`() {
        val flat = MutableList(60) { 100.0 }
        val up = MutableList(20) { 100.0 + it * 2.0 }
        assertFalse("old cross is not a fresh cross", MacdCalc.macdCrossUp(flat + up))
    }
}
