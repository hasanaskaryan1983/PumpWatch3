package com.pumpwatch.app.data

import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 122: تست parser کندل برای هر سه صرافی.
 *
 * هدف: تأیید اینکه فرمت‌های متفاوت Bybit/OKX/Gate به یک ساختار یکسان
 * (BinanceCandle) تبدیل می‌شوند — بدون نیاز به شبکه یا MockWebServer.
 *
 * 🚀 Commit 122 fix: چون امضا `List<String>` است (نه `List<String?>`)،
 * از string خالی/نامعتبر استفاده می‌کنیم نه null.
 */
class BinanceApiCandleParserTest {

    // ========== Bybit (timeMs = true) ==========

    @Test
    fun `Bybit candle parses all fields correctly`() {
        // ts=1700000000000, open=40000, high=40500, low=39500, close=40200, vol=1234.5
        val raw = listOf("1700000000000", "40000", "40500", "39500", "40200", "1234.5", "extra")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNotNull(c)
        c!!
        assertEquals(1_700_000_000_000L, c.time)
        assertEquals(40000.0, c.open, 0.0001)
        assertEquals(40500.0, c.high, 0.0001)
        assertEquals(39500.0, c.low, 0.0001)
        assertEquals(40200.0, c.close, 0.0001)
        assertEquals(1234.5, c.volume, 0.0001)
    }

    @Test
    fun `Bybit candle with extra fields works`() {
        // OKX sends 9 fields: [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
        val raw = listOf("1700000000000", "100", "110", "90", "105", "500", "50000", "500000", "1")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNotNull(c)
        assertEquals(105.0, c!!.close, 0.0001)
        assertEquals(500.0, c.volume, 0.0001)
    }

    // ========== Gate (timeMs = false → seconds, needs × 1000) ==========

    @Test
    fun `Gate candle converts seconds to milliseconds`() {
        // Gate: [timestamp(s), volume, close, high, low, open, quote_volume]
        // indices: 0=ts, 1=vol, 2=close, 3=high, 4=low, 5=open
        val raw = listOf("1700000000", "1234.5", "40200", "40500", "39500", "40000", "50000000")
        val c = MultiExchange.candle(raw, t = 0, o = 5, h = 3, l = 4, c = 2, v = 1, timeMs = false)
        assertNotNull(c)
        c!!
        // time should be multiplied by 1000
        assertEquals(1_700_000_000_000L, c.time)
        assertEquals(40000.0, c.open, 0.0001)
        assertEquals(40500.0, c.high, 0.0001)
        assertEquals(39500.0, c.low, 0.0001)
        assertEquals(40200.0, c.close, 0.0001)
        assertEquals(1234.5, c.volume, 0.0001)
    }

    // ========== خطاها و edge cases ==========

    @Test
    fun `candle returns null when close is invalid string`() {
        // 🚀 Commit 122 fix: use empty/invalid string instead of null (List<String> signature)
        val raw = listOf("1700000000000", "40000", "40500", "39500", "", "1234.5")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNull("Candle with invalid close should be rejected", c)
    }

    @Test
    fun `candle returns null when time is invalid string`() {
        // 🚀 Commit 122 fix: use empty string instead of null
        val raw = listOf("", "40000", "40500", "39500", "40200", "1234.5")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNull("Candle with invalid time should be rejected", c)
    }

    @Test
    fun `candle returns null when close is not a number`() {
        val raw = listOf("1700000000000", "40000", "40500", "39500", "invalid", "1234.5")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNull("Candle with non-numeric close should be rejected", c)
    }

    @Test
    fun `candle returns null when time is not a number`() {
        val raw = listOf("not-a-timestamp", "40000", "40500", "39500", "40200", "1234.5")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNull("Candle with non-numeric time should be rejected", c)
    }

    @Test
    fun `candle returns null for empty list`() {
        val c = MultiExchange.candle(emptyList(), t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNull("Empty input should return null", c)
    }

    @Test
    fun `candle returns null for too-short list`() {
        // Only 3 fields, missing close (index 4) and volume (index 5)
        val raw = listOf("1700000000000", "40000", "40500")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNull("Too-short input should return null", c)
    }

    @Test
    fun `candle handles invalid non-critical fields gracefully`() {
        // 🚀 Commit 122 fix: use empty strings instead of null (List<String> signature)
        // open/high/low/volume are empty/invalid but close is present → candle should work with 0.0 defaults
        val raw = listOf("1700000000000", "", "", "", "40200", "")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNotNull("Candle should work if close and time are valid", c)
        c!!
        assertEquals(0.0, c.open, 0.0001)
        assertEquals(0.0, c.high, 0.0001)
        assertEquals(0.0, c.low, 0.0001)
        assertEquals(40200.0, c.close, 0.0001)
        assertEquals(0.0, c.volume, 0.0001)
    }

    @Test
    fun `candle handles decimal strings correctly`() {
        val raw = listOf("1700000000000", "40000.123456", "40500.99", "39500.01", "40200.5", "1234.567")
        val c = MultiExchange.candle(raw, t = 0, o = 1, h = 2, l = 3, c = 4, v = 5, timeMs = true)
        assertNotNull(c)
        c!!
        assertEquals(40000.123456, c.open, 0.0000001)
        assertEquals(1234.567, c.volume, 0.0001)
    }
}
