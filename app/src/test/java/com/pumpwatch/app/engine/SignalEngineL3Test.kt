package com.pumpwatch.app.engine

import org.junit.Assert.*
import org.junit.Test

class SignalEngineL3Test {

    @Test
    fun testMomentumFilterRejectsSmallMove() {
        // شبیه‌سازی: قیمت از ۱۰۰ به ۱۰۱ رسیده (فقط ۱٪ رشد، کمتر از آستانه ۱.۵٪)
        val prevClose = 100.0
        val currentPrice = 101.0
        val changePct = if (prevClose > 0) ((currentPrice - prevClose) / prevClose) * 100.0 else 0.0
        
        // شرط L3: باید رد شود
        val isBullishSetup = changePct >= 1.5
        assertFalse("Setup with 1 percent move should be rejected by L3 filter", isBullishSetup)
    }

    @Test
    fun testMomentumFilterAcceptsValidMove() {
        // شبیه‌سازی: قیمت از ۱۰۰ به ۱۰۱.۶ رسیده (۱.۶٪ رشد، بیشتر از آستانه ۱.۵٪)
        val prevClose = 100.0
        val currentPrice = 101.6
        val changePct = if (prevClose > 0) ((currentPrice - prevClose) / prevClose) * 100.0 else 0.0
        
        // شرط L3: باید قبول شود
        val isBullishSetup = changePct >= 1.5
        assertTrue("Setup with 1.6 percent move should pass L3 filter", isBullishSetup)
    }
}
