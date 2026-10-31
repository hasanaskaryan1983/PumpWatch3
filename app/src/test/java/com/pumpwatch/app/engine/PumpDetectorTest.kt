package com.pumpwatch.app.engine
import org.junit.Assert.*
import org.junit.Test

class PumpDetectorTest {
    
    @Test 
    fun detectEarlyPump_normalData() {
        val c = genNormalCandles(30)
        assertFalse("Should not detect pump in normal data", PumpDetector.detectEarlyPump(c))
    }

    @Test 
    fun detectEarlyPump_spikeData() {
        val c = genPumpCandles(30)
        assertTrue("Should detect early pump with volume and price spike", PumpDetector.detectEarlyPump(c))
    }

    @Test 
    fun isAtTop_nearHigh() {
        val c = genPumpCandles(60)
        assertTrue("Should detect price near top", PumpDetector.isAtTop(c))
    }

    @Test 
    fun analyzePumpPotential_highScore() {
        val c = genPumpCandles(60)
        val score = PumpDetector.analyzePumpPotential(c)
        assertTrue("Pump potential score should be >= 40, got $score", score >= 40)
    }

    private fun genNormalCandles(n: Int): List<Candle> {
        val l = mutableListOf<Candle>()
        var p = 100.0
        for (i in 0 until n) {
            l.add(Candle(i.toLong(), p, p + 1.0, p - 1.0, p, 100.0))
        }
        return l
    }

    private fun genPumpCandles(n: Int): List<Candle> {
        val l = mutableListOf<Candle>()
        var p = 100.0
        for (i in 0 until n) {
            val isSpike = i >= n - 3
            // جهش حجم به ۵ برابر در ۳ کندل آخر
            val v = if (isSpike) 500.0 else 100.0 
            // جهش قیمت ۱.۵٪ در هر کندل برای ۳ کندل آخر (مجموعاً > ۴.۵٪)
            p = if (isSpike) p * 1.015 else p 
            l.add(Candle(i.toLong(), p, p * 1.01, p * 0.99, p, v))
        }
        return l
    }
}
