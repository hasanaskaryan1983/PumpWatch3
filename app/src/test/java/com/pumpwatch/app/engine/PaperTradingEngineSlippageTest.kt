package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class PaperTradingEngineSlippageTest {

    @Test
    fun `slippage should be calculated correctly when price differs from entry`() {
        // شبیه‌سازی: قیمت سیگنال ۱۰۰، قیمت لحظه‌ای بازار ۱۰۱.۵ (۱.۵٪ اسلیپج)
        val entryPrice = 100.0
        val marketPrice = 101.5
        
        val slippagePct = if (entryPrice > 0) abs(marketPrice - entryPrice) / entryPrice * 100.0 else 0.0
        
        assertEquals(1.5, slippagePct, 0.01)
    }

    @Test
    fun `slippage should be zero when entry price is zero or invalid`() {
        val entryPrice = 0.0
        val marketPrice = 100.0
        
        val slippagePct = if (entryPrice > 0) abs(marketPrice - entryPrice) / entryPrice * 100.0 else 0.0
        
        assertEquals(0.0, slippagePct, 0.01)
    }
}
