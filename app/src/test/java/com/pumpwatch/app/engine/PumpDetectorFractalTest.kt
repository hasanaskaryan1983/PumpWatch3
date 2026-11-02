package com.pumpwatch.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KFunction2

class PumpDetectorFractalTest {

    // بازتاب (Reflection) برای دسترسی به توابع private جهت تست واحد
    private val detector = PumpDetector()
    private val detectHigh: KFunction2<List<Double>, Int, Boolean> = 
        PumpDetector::class.java.getDeclaredMethod("detectFractalHigh", List::class.java, Int::class.java, Int::class.java).let {
            it.isAccessible = true
            { highs: List<Double>, index: Int -> it.invoke(detector, highs, index, 2) as Boolean }
        }
        
    private val detectLow: KFunction2<List<Double>, Int, Boolean> = 
        PumpDetector::class.java.getDeclaredMethod("detectFractalLow", List::class.java, Int::class.java, Int::class.java).let {
            it.isAccessible = true
            { lows: List<Double>, index: Int -> it.invoke(detector, lows, index, 2) as Boolean }
        }

    @Test
    fun testF5_ValidFractalHighIsDetected() {
        // شبیه‌سازی: ۵ کندل. کندل وسط (ایندکس ۲) بالاترین است (فراکتال صعودی)
        // قیمت‌ها: 10, 20, 30, 20, 10
        val highs = listOf(10.0, 20.0, 30.0, 20.0, 10.0)
        val size = highs.size
        val bars = 2
        val correctIndex = size - 1 - bars // 5 - 1 - 2 = 2
        
        // تست اندیس صحیح: باید true باشد
        assertTrue("فراکتال صعودی در اندیس صحیح باید تشخیص داده شود", detectHigh(highs, correctIndex))
        
        // تست اندیس غلط (آخرین کندل): باید false باشد (اثبات باگ قدیمی)
        assertFalse("بررسی آخرین کندل (size - 1) باید false برگرداند چون تأیید نشده است", detectHigh(highs, size - 1))
    }

    @Test
    fun testF5_ValidFractalLowIsDetected() {
        // شبیه‌سازی: ۵ کندل. کندل وسط (ایندکس ۲) پایین‌ترین است (فراکتال نزولی)
        // قیمت‌ها: 30, 20, 10, 20, 30
        val lows = listOf(30.0, 20.0, 10.0, 20.0, 30.0)
        val size = lows.size
        val bars = 2
        val correctIndex = size - 1 - bars // 5 - 1 - 2 = 2
        
        assertTrue("فراکتال نزولی در اندیس صحیح باید تشخیص داده شود", detectLow(lows, correctIndex))
        assertFalse("بررسی آخرین کندل (size - 1) باید false برگرداند", detectLow(lows, size - 1))
    }
}
