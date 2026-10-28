package com.pumpwatch.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTypeSeparationTest {

    @Test
    fun isFut_only_accepts_FUT_or_FUTURES() {
        assertTrue(MultiExchange.isFut("FUT"))
        assertTrue(MultiExchange.isFut("FUTURES"))
        assertFalse(MultiExchange.isFut("SPOT"))
        assertFalse(MultiExchange.isFut(""))
    }
}
