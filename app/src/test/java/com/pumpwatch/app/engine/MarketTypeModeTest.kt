package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketTypeModeTest {

    @Test
    fun mode_FUT_maps_to_FUTURES_market_type() {
        assertEquals("FUTURES", BatchScanner.marketTypeOf("FUT"))
        assertEquals("SPOT", BatchScanner.marketTypeOf("SPOT"))
        assertEquals("SPOT", BatchScanner.marketTypeOf("ANY"))
    }
}
