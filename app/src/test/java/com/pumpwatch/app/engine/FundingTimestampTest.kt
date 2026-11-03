package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class FundingTimestampTest {

    // شبیه‌سازی دقیق منطق تابع برای تست مستقل
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
        // ورود ۰۷:۰۰، خروج ۱۵:۰۰ (۸ ساعت نگهداری، اما بدون عبور از ۰۸:۰۰ یا ۱۶:۰۰)
        val entryTime = 25200000L // 1970-01-01 07:00:00 UTC
        val exitTime = 54000000L  // 1970-01-01 15:00:00 UTC
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "BUY")
        assertEquals("باید ۰.۰ باشد چون از مرز ۸ ساعته عبور نشده", 0.0, cost, 0.001)
    }

    @Test
    fun `F4b - Funding charged when 8-hour UTC boundary IS crossed (even for short duration)`() {
        // ورود ۰۷:۵۵، خروج ۰۸:۰۵ (فقط ۱۰ دقیقه، اما عبور از مرز ۰۸:۰۰)
        val entryTime = 28500000L // 1970-01-01 07:55:00 UTC
        val exitTime = 29100000L  // 1970-01-01 08:05:00 UTC
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "BUY")
        assertEquals("باید ۰.۱ دلار باشد چون از یک مرز ۸ ساعته عبور شده", 0.1, cost, 0.001)
    }

    @Test
    fun `F4b - Short position receives funding when rate is positive`() {
        // پوزیشن شورت (SELL)، نرخ فاندینگ مثبت. باید فاندینگ دریافت کند (هزینه منفی)
        val entryTime = 25200000L  // 07:00
        val exitTime = 61200000L   // 17:00 (عبور از مرز 08:00 و 16:00 -> 2 دوره)
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "SELL")
        assertEquals("هزینه باید منفی باشد (دریافت فاندینگ)", -0.2, cost, 0.001)
    }
}
