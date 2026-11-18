package com.pumpwatch.app.engine

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class PumpDetectorAccumulationDumpTest {
    private val detector = PumpDetector()

    @Test
    fun testDetectAccumulationAndDumpPositive() {
        // تاریخچه: ۲۰ کندل با نوسان کم (بین ۹۹.۵ تا ۱۰۱.۰ -> کمتر از ۳٪) و حجم کم (۱۰.۰)
        val history = (1..20).map { i ->
            Candle(time = i.toLong(), open = 100.0, high = 101.0, low = 99.5, close = 100.5, volume = 10.0)
        }
        // کندل آخر: قرمز، حجم انفجاری (۳۰.۰ > ۲.۵ * ۱۰.۰)، شکست قیمت (۹۷.۰ < ۱۰۰.۵ * ۰.۹۸)
        val dumpCandle = Candle(time = 21L, open = 100.5, high = 101.0, low = 96.0, close = 97.0, volume = 30.0)
        
        val candles = history + listOf(dumpCandle)
        assertTrue("Should detect accumulation and dump", detector.detectAccumulationAndDump(candles))
    }

    @Test
    fun testDetectAccumulationAndDumpFalseIfHighVolatility() {
        // تاریخچه: نوسان زیاد (۹۰.۰ تا ۱۱۰.۰ -> بیشتر از ۳٪)، پس تجمیع در کف نیست
        val history = (1..20).map { i ->
            Candle(time = i.toLong(), open = 100.0, high = 110.0, low = 90.0, close = 100.0, volume = 10.0)
        }
        val dumpCandle = Candle(time = 21L, open = 100.0, high = 101.0, low = 90.0, close = 90.0, volume = 30.0)
        
        val candles = history + listOf(dumpCandle)
        assertFalse("Should not detect if history volatility is high", detector.detectAccumulationAndDump(candles))
    }

    @Test
    fun testDetectAccumulationAndDumpFalseIfLastCandleIsGreen() {
        val history = (1..20).map { i ->
            Candle(time = i.toLong(), open = 100.0, high = 101.0, low = 99.5, close = 100.5, volume = 10.0)
        }
        // کندل آخر: سبز (صعودی)، پس دامپ نیست
        val greenCandle = Candle(time = 21L, open = 100.0, high = 105.0, low = 100.0, close = 104.0, volume = 30.0)
        
        val candles = history + listOf(greenCandle)
        assertFalse("Should not detect if last candle is green", detector.detectAccumulationAndDump(candles))
    }
}
