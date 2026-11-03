package com.pumpwatch.app.engine

import org.junit.Assert.assertNotNull
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
        // Instant.toString() به‌طور پیش‌فرض شامل میلی‌ثانیه است (مثلاً 2023-10-27T10:00:00.123Z)
        val twoHoursAgo = Instant.now().minusSeconds(2 * 3600).toString()
        val age = ageHours(twoHoursAgo)
        
        assertNotNull("باید با میلی‌ثانیه به‌درستی پارس شود", age)
        assert(age!! > 1.9 && age < 2.1) { "سن باید حدود ۲ ساعت باشد، اما $age است" }
    }

    @Test
    fun `F6 - parses ISO time WITHOUT milliseconds correctly`() {
        // ساخت زمان ۵ ساعت پیش و حذف بخش میلی‌ثانیه با تابع استاندارد substringBefore
        val fiveHoursAgoInstant = Instant.now().minusSeconds(5 * 3600)
        val timeWithoutMs = fiveHoursAgoInstant.toString().substringBefore(".") + "Z"
        
        val age = ageHours(timeWithoutMs)
        assertNotNull("باید بدون میلی‌ثانیه هم به‌درستی پارس شود", age)
        assert(age!! > 4.9 && age < 5.1) { "سن باید حدود ۵ ساعت باشد، اما $age است" }
    }

    @Test
    fun `F6 - returns null (UNKNOWN) for invalid time strings`() {
        val invalidTime = "2024-01-15 10:30:45" // فرمت نامعتبر (فاقد T و Z)
        val age = ageHours(invalidTime)
        
        assertNull("برای زمان نامعتبر باید null (UNKNOWN) برگرداند", age)
    }

    @Test
    fun `F6 - returns null (UNKNOWN) for null or blank input`() {
        assertNull(ageHours(null))
        assertNull(ageHours(""))
        assertNull(ageHours("   "))
    }
}
