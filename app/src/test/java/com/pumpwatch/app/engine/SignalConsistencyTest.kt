package com.pumpwatch.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalConsistencyTest {

    @Test
    fun pump_signal_must_not_contain_bearish_reasons() {
        val reasons = mutableListOf("روند بلندمدت صعودی", "شکست مقاومت", "روند نزولی کوتاه‌مدت")
        SignalEngine.enforceSignalConsistency("PUMP", reasons)
        
        assertTrue(reasons.contains("روند بلندمدت صعودی"))
        assertFalse("دلایل نزولی باید حذف شوند", reasons.any { it.contains("نزولی") || it.contains("DOWN") })
    }

    @Test
    fun dump_signal_must_not_contain_bullish_reasons() {
        val reasons = mutableListOf("شکست حمایت", "روند بلندمدت صعودی", "واگرایی منفی")
        SignalEngine.enforceSignalConsistency("DUMP", reasons)
        
        assertTrue(reasons.contains("شکست حمایت"))
        assertFalse("دلایل صعودی باید حذف شوند", reasons.any { it.contains("صعودی") || it.contains("UP") })
    }

    @Test
    fun none_signal_keeps_all_reasons() {
        val reasons = mutableListOf("روند صعودی", "روند نزولی")
        SignalEngine.enforceSignalConsistency("NONE", reasons)
        
        assertEquals(2, reasons.size)
    }
}
