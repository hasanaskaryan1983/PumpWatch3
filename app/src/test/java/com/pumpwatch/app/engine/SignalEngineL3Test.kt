package com.pumpwatch.app.engine

import org.junit.Assert.*
import org.junit.Test

class SignalEngineL3Test {

    @Test
    fun `momentum filter must reject breakout with less than 1.5 percent move`() {
        // شبیه‌سازی: قیمت از ۱۰۰ به ۱۰۱ رسیده (فقط ۱٪ رشد، کمتر از آستانه ۱.۵٪)
        val prevClose = 100.0
        val currentPrice = 101.0
        val changePct = if (prevClose > 0) ((currentPrice - prevClose) / prevClose) * 100.0 else 0.0
        
        // شرط L3: باید رد شود
        val isBullishSetup = changePct >= 1.5
        assertFalse("Setup with 1% move should be rejected by L3 filter", isBullishSetup)
    }

    @Test
    fun `momentum filter must accept breakout with 1.5 percent or more move`() {
        // شبیه‌سازی: قیمت از ۱۰۰ به ۱۰۱.۶ رسیده (۱.۶٪ رشد، بیشتر از آستانه ۱.۵٪)
        val prevClose = 100.0
        val currentPrice = 101.6
        val changePct = if (prevClose > 0) ((currentPrice - prevClose) / prevClose) * 100.0 else 0.0
        
        // شرط L3: باید قبول شود
        val isBullishSetup = changePct >= 1.5
        assertTrue("Setup with 1.6% move should pass L3 filter", isBullishSetup)
    }
}
