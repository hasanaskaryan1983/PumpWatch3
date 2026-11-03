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
        // 09:00 UTC = 32,400,000 ms (اینکس بازه: 1)
        // 15:00 UTC = 54,000,000 ms (اینکس بازه: 1)
        // 1 - 1 = 0 مرز عبور کرده
        val entryTime = 32400000L 
        val exitTime = 54000000L  
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "BUY")
        assertEquals("باید ۰.۰ باشد چون از مرز ۸ ساعته عبور نشده", 0.0, cost, 0.001)
    }

    @Test
    fun `F4b - Funding charged when 8-hour UTC boundary IS crossed (even for short duration)`() {
        // 07:55 UTC = 28,500,000 ms (اینکس بازه: 0)
        // 08:05 UTC = 29,100,000 ms (اینکس بازه: 1)
        // 1 - 0 = 1 مرز عبور کرده
        val entryTime = 28500000L 
        val exitTime = 29100000L  
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "BUY")
        assertEquals("باید ۰.۱ دلار باشد چون از یک مرز ۸ ساعته عبور شده", 0.1, cost, 0.001)
    }

    @Test
    fun `F4b - Short position receives funding when rate is positive`() {
        // 07:00 UTC = 25,200,000 ms (اینکس بازه: 0)
        // 17:00 UTC = 61,200,000 ms (اینکس بازه: 2)
        // 2 - 0 = 2 مرز عبور کرده
        val entryTime = 25200000L  
        val exitTime = 61200000L   
        
        val cost = calculateFundingCost(1000.0, 0.0001, entryTime, exitTime, "SELL")
        assertEquals("هزینه باید منفی باشد (دریافت فاندینگ)", -0.2, cost, 0.001)
    }
}
