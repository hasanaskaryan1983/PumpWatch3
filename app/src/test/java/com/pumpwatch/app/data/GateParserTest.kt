package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GateParserTest {

    // fixture واقعی‌نما از Gate spot candlesticks:
    // [timestamp(sec), volume, close, high, low, open, quote_volume]
    private val row = listOf("1700000000", "12.5", "105.0", "108.0", "102.0", "103.0", "1300.0")

    @Test
    fun `candle maps open index 5 and close index 2 for Gate`() {
        val c = MultiExchange.candle(row, 0, 5, 3, 4, 2, 1, false)
        assertNotNull(c)
        assertEquals("open must be index 5", 103.0, c!!.open, 1e-9)
        assertEquals("close must be index 2", 105.0, c.close, 1e-9)
        assertEquals("high must be index 3", 108.0, c.high, 1e-9)
        assertEquals("low must be index 4", 102.0, c.low, 1e-9)
        assertEquals("volume must be index 1", 12.5, c.volume, 1e-9)
        assertEquals("timestamp must be converted to ms", 1700000000000L, c.time)
    }

    @Test
    fun `candle returns null when close index is invalid`() {
        val badRow = listOf("1700000000", "12.5", "not_a_number", "108.0", "102.0", "103.0", "1300.0")
        val c = MultiExchange.candle(badRow, 0, 5, 3, 4, 2, 1, false)
        assertNull("close must be parseable", c)
    }

    @Test
    fun `candle returns null when timestamp index is invalid`() {
        val badRow = listOf("bad_ts", "12.5", "105.0", "108.0", "102.0", "103.0", "1300.0")
        val c = MultiExchange.candle(badRow, 0, 5, 3, 4, 2, 1, false)
        assertNull("timestamp must be parseable", c)
    }
}
