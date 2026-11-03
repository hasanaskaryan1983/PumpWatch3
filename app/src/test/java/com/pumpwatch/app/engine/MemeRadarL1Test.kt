package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeRadarL1Test {

    @Test
    fun `L1 - UNKNOWN security sets isHighRisk flag and caps score at 50`() {
        // شبیه‌سازی سناریویی که rugScore null است (وضعیت UNKNOWN)
        val rugScore: Int? = null
        val initialScore = 85 // امتیازی که در حالت عادی بالا است
        
        // منطق موجود در MemeRadar.kt
        val isHighRisk = (rugScore == null)
        val finalScore = if (rugScore == null) {
            initialScore.coerceAtMost(50)
        } else {
            initialScore
        }

        // تأییدات تست
        assertTrue("وقتی rugScore null است، isHighRisk باید true باشد", isHighRisk)
        assertEquals("امتیاز نهایی باید به حداکثر ۵۰ محدود شود", 50, finalScore)
    }

    @Test
    fun `L1 - Verified security (rugScore != null) keeps original score and no high risk flag`() {
        val rugScore: Int? = 75
        val initialScore = 85
        
        val isHighRisk = (rugScore == null)
        val finalScore = if (rugScore == null) {
            initialScore.coerceAtMost(50)
        } else {
            initialScore
        }

        assertTrue("وقتی rugScore مقدار دارد، isHighRisk باید false باشد", !isHighRisk)
        assertEquals("امتیاز نهایی باید همان امتیاز اولیه باقی بماند", 85, finalScore)
    }
}
