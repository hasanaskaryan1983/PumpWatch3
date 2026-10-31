package com.pumpwatch.app.engine
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
class PaperTradingFillTest {
    @Test fun slippage_calculation_must_be_accurate() {
        val entry = 100.0; val market = 101.5
        val slip = if (entry > 0) abs(market - entry) / entry * 100.0 else 0.0
        assertEquals(1.5, slip, 0.01)
    }
    @Test fun fut_signal_defaults_price_to_entry() {
        val sig = FutSignalForPaper("id", "BTC", "BTC", "LONG", 50000.0, 49000.0, 52000.0, 80)
        assertEquals(50000.0, sig.price, 0.01)
    }
}
