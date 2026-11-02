package com.pumpwatch.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpDetectorFractalTest {

    @Test
    fun testF5_ValidFractalLowIsDetected() {
        // ساخت داده‌های ساختگی با اندازه ۲۰ (بیشتر از ۱۴ برای ADX/Stoch و ۲ برای bars)
        // برای اینکه isFractalLow در اندیس صحیح (size - 1 - 2 = 17) true شود،
        // مقدار lows[17] باید از ۴ همسایه خود (۱۵، ۱۶، ۱۸، ۱۹) کمتر باشد.
        val size = 20
        val lows = MutableList(size) { 100.0 }.apply { this[17] = 50.0 }
        val highs = List(size) { 100.0 }
        val closes = List(size) { 100.0 }

        val result = PumpDetector.analyzeSixtySecond(highs, lows, closes)

        // اثبات: با اصلاح اندیس، فراکتال نزولی باید تشخیص داده شود
        assertTrue("فراکتال نزولی باید در اندیس صحیح تشخیص داده شود", result.isFractalLow)
        assertFalse("فراکتال صعودی نباید در این داده‌ها تشخیص داده شود", result.isFractalHigh)
    }

    @Test
    fun testF5_ValidFractalHighIsDetected() {
        // ساخت داده‌های ساختگی با اندازه ۲۰
        // برای اینکه isFractalHigh در اندیس صحیح (۱۷) true شود،
        // مقدار highs[17] باید از ۴ همسایه خود بیشتر باشد.
        val size = 20
        val highs = MutableList(size) { 100.0 }.apply { this[17] = 150.0 }
        val lows = List(size) { 100.0 }
        val closes = List(size) { 100.0 }

        val result = PumpDetector.analyzeSixtySecond(highs, lows, closes)

        // اثبات: با اصلاح اندیس، فراکتال صعودی باید تشخیص داده شود
        assertTrue("فراکتال صعودی باید در اندیس صحیح تشخیص داده شود", result.isFractalHigh)
        assertFalse("فراکتال نزولی نباید در این داده‌ها تشخیص داده شود", result.isFractalLow)
    }
}
