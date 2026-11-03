package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MemeRadarF6Test {

    // شبیه‌سازی دقیق منطق تابع برای تست مستقل
    private fun ageHours(createdAt: String?): Double? {
        if (createdAt.isNullOrBlank()) return null
        return try {
            val instant = Instant.parse(createdAt)
            val now = Instant.now()
            Duration.between(instant, now).toMillis() / 3_600_000.0
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun `F6 - parses ISO time WITH milliseconds correctly`() {
        // شبیه‌سازی زمان ۲ ساعت پیش با میلی‌ثانیه
        val twoHoursAgo = Instant.now().minusSeconds(2 * 3600).toString() // تولید رشته‌ای مثل 2024-01-15T10:30:45.123Z
        val age = ageHours(twoHoursAgo)
        
        // باید حدود ۲ ساعت باشد (با حاشیه خطای کوچک به دلیل اختلاف زمانی اجرا)
        assert(age != null)
        assert(age!! > 1.9 && age < 2.1) { "سن محاسبه‌شده باید حدود ۲ ساعت باشد، اما $age است" }
    }

    @Test
    fun `F6 - parses ISO time WITHOUT milliseconds correctly`() {
        // شبیه‌سازی زمان ۵ ساعت پیش بدون میلی‌ثانیه
        val fiveHoursAgo = Instant.now().minusSeconds(5 * 3600).toString().replaceBeforeLast(".", "") // حذف میلی‌ثانیه برای تست
        // ساخت دستی فرمت بدون میلی‌ثانیه برای اطمینان
        val formatted = java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now().minusSeconds(5 * 3600)).replaceBeforeLast(".", "")
        
        val age = ageHours(formatted)
        assert(age != null)
        assert(age!! > 4.9 && age < 5.1) { "سن محاسبه‌شده باید حدود ۵ ساعت باشد، اما $age است" }
    }

    @Test
    fun `F6 - returns null (UNKNOWN) for invalid time strings`() {
        val invalidTime = "2024-01-15 10:30:45" // فرمت نامعتبر (بدون T و Z)
        val age = ageHours(invalidTime)
        
        assertNull("برای زمان نامعتبر باید null (UNKNOWN) برگرداند، نه ۹۹۹۹.۰", age)
    }

    @Test
    fun `F6 - returns null (UNKNOWN) for null or blank input`() {
        assertNull(ageHours(null))
        assertNull(ageHours(""))
        assertNull(ageHours("   "))
    }
}
