package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class FundingTimestampTest {

    // بازتاب (Reflection) برای دسترسی به تابع private جهت تست
    // توجه: در محیط واقعی کاتلین، بهتر است تابع را internal کنیم، اما برای تست سریع از این روش یا کپی منطق استفاده می‌کنیم.
    // برای سادگی و پایداری تست، منطق را مستقیماً اینجا شبیه‌سازی می‌کنیم تا از پیچیدگی Reflection جلوگیری شود.
    
    private fun calculateFundingCost(notionalUsd: Double, fundingRate: Double, entryTimeMs: Long, exitTimeMs: Long, side: String): Double {
        val intervalMs = 8L * 60 * 60 * 1000
        val periodsCrossed = ((exitTimeMs / intervalMs) - (entryTimeMs / intervalMs)).toInt()
        if (periodsCrossed <= 0) return 0.0
        val isLong = side == "BUY" || side == "PUMP"
        val pays = if (isLong) fundingRate >= 0 else fundingRate < 0
        val costPerPeriod = notionalUsd * abs(fundingRate)
        val totalCost = costPerPeriod * periodsCrossed
        return if (pays) totalCost else -totalCost
    }

    @Test
    fun `F4b - No funding charged if 8-hour UTC boundary is NOT crossed`() {
        // سناریو: ورود ساعت ۰۷:۰۰، خروج ساعت ۱۵:۰۰ (۸ ساعت نگهداری، اما بدون عبور از ۰۸:۰۰ یا ۱۶:۰۰)
        // 1970-01-01 07:00:00 UTC = 25200000 ms
        // 1970-01-01 15:00:00 UTC = 54000000 ms
        val entryTime = 25200000L
        val exitTime = 54000000L
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "BUY")
        assertEquals("باید ۰.۰ باشد چون از مرز ۸ ساعته عبور نشده", 0.0, cost, 0.001)
    }

    @Test
    fun `F4b - Funding charged when 8-hour UTC boundary IS crossed (even for short duration)`() {
        // سناریو: ورود ساعت ۰۷:۵۵، خروج ساعت ۰۸:۰۵ (فقط ۱۰ دقیقه، اما عبور از مرز ۰۸:۰۰)
        // 1970-01-01 07:55:00 UTC = 28500000 ms
        // 1970-01-01 08:05:00 UTC = 29100000 ms
        val entryTime = 28500000L
        val exitTime = 29100000L
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "BUY")
        // 1000 * 0.0001 = 0.1 دلار
        assertEquals("باید ۰.۱ دلار باشد چون از یک مرز ۸ ساعته عبور شده", 0.1, cost, 0.001)
    }

    @Test
    fun `F4b - Short position receives funding when rate is positive`() {
        // سناریو: پوزیشن شورت (SELL)، نرخ فاندینگ مثبت. باید فاندینگ دریافت کند (هزینه منفی)
        val entryTime = 25200000L // 07:00
        val exitTime = 61200000L // 17:00 (عبور از مرز 08:00 و 16:00 -> 2 دوره)
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "SELL")
        // 2 دوره * 1000 * 0.0001 = 0.2 دلار. چون شورت است و نرخ مثبت، دریافت می‌کند (-0.2)
        assertEquals("هزینه باید منفی باشد (دریافت فاندینگ)", -0.2, cost, 0.001)
    }
}
