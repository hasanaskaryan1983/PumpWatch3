package com.pumpwatch.app.engine

import com.pumpwatch.app.data.FuturesCandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesScannerHDojiTest {

    private fun createCandle(openTime: Long, open: Double, close: Double): FuturesCandle {
        return FuturesCandle(
            openTime = openTime,
            open = open,
            high = maxOf(open, close),
            low = minOf(open, close),
            close = close,
            quoteVolume = 1000.0
        )
    }

    @Test
    fun `H-DOJI - open candle is ignored`() {
        val now = System.currentTimeMillis()
        val intervalMs = 5L * 60 * 1000
        
        // 3 کندل بسته شده (صعودی)
        val c1 = createCandle(now - 4 * intervalMs, 100.0, 101.0)
        val c2 = createCandle(now - 3 * intervalMs, 101.0, 102.0)
        val c3 = createCandle(now - 2 * intervalMs, 102.0, 103.0)
        
        // 1 کندل باز (نزولی، باید نادیده گرفته شود)
        val cOpen = createCandle(now - intervalMs / 2, 103.0, 90.0) 
        
        val candles = listOf(c1, c2, c3, cOpen)
        
        // باید true برگرداند چون کندل باز نزولی نادیده گرفته می‌شود
        assertTrue(FuturesScannerEngine.triggerOf(candles, "LONG"))
    }

    @Test
    fun `H-DOJI - doji candle does not trigger directional signal`() {
        val now = System.currentTimeMillis()
        val intervalMs = 5L * 60 * 1000
        
        val c1 = createCandle(now - 4 * intervalMs, 100.0, 101.0)
        val c2 = createCandle(now - 3 * intervalMs, 101.0, 102.0)
        // کندل دوجی (close == open)
        val c3 = createCandle(now - 2 * intervalMs, 102.0, 102.0) 
        
        val candles = listOf(c1, c2, c3)
        
        // باید false برگرداند چون دوجی یک کندل جهت‌دار نیست
        assertFalse(FuturesScannerEngine.triggerOf(candles, "LONG"))
        assertFalse(FuturesScannerEngine.triggerOf(candles, "SHORT"))
    }
    
    @Test
    fun `H-DOJI - minimal body requirement filters out tiny bodies`() {
        val now = System.currentTimeMillis()
        val intervalMs = 5L * 60 * 1000
        
        val c1 = createCandle(now - 4 * intervalMs, 100.0, 101.0)
        val c2 = createCandle(now - 3 * intervalMs, 101.0, 102.0)
        // بدنه بسیار کوچک (۰.۰۱٪ < ۰.۰۵٪)
        val c3 = createCandle(now - 2 * intervalMs, 102.0, 102.01) 
        
        val candles = listOf(c1, c2, c3)
        
        assertFalse(FuturesScannerEngine.triggerOf(candles, "LONG"))
    }
}
