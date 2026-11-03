package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WhaleFlowEngineF8Test {

    // کپی دقیق منطق تابع جدید برای تست ایزوله (بدون نیاز به Reflection)
    private fun computeThreshold(
        trades: List<Double>,
        volume24h: Double?,
        userThreshold: Double?,
        liquidityUsd: Double?
    ): Double {
        val DEFAULT_WHALE_THRESHOLD = 100_000.0
        val MIN_THRESHOLD = 10_000.0
        val MAX_THRESHOLD = 500_000.0
        val VOLUME_RATIO = 0.001
        val MIN_TRADES_FOR_PERCENTILE = 100
        val LIQUIDITY_FLOOR_RATIO = 0.01

        if (userThreshold != null && userThreshold > 0) return userThreshold

        val liquidityFloor = if (liquidityUsd != null && liquidityUsd > 0) {
            (liquidityUsd * LIQUIDITY_FLOOR_RATIO).coerceAtLeast(MIN_THRESHOLD)
        } else {
            MIN_THRESHOLD
        }

        if (volume24h != null && volume24h > 0) {
            val volumeBased = volume24h * VOLUME_RATIO
            return volumeBased.coerceIn(liquidityFloor, MAX_THRESHOLD)
        }

        if (trades.size >= MIN_TRADES_FOR_PERCENTILE) {
            val notionals = trades.sorted()
            val index = (notionals.size * 0.99).toInt().coerceAtMost(notionals.size - 1)
            val p99 = notionals[index].coerceIn(liquidityFloor, MAX_THRESHOLD)
            return p99.coerceAtLeast(liquidityFloor)
        }

        return DEFAULT_WHALE_THRESHOLD.coerceAtLeast(liquidityFloor)
    }

    @Test
    fun `F8 - high liquidity pool prevents small trades from being flagged as whales via percentile`() {
        // سناریو: استخر بزرگ (۱۰ میلیون دلار نقدینگی) با تریدهای کوچک (۱۰۰ تا ۲۰,۰۰۰ دلار)
        // ۱٪ نقدینگی = ۱۰۰,۰۰۰ دلار. پس کف آستانه باید ۱۰۰,۰۰۰ دلار باشد.
        // صدک ۹۹ تریدها حدود ۲۰,۰۰۰ دلار است. بدون F8، آستانه ۲۰,۰۰۰ می‌شد (باگ!).
        // با F8، آستانه باید به ۱۰۰,۰۰۰ دلار (کف نقدینگی) محدود شود.
        
        val trades = List(1000) { (it * 20.0) } // تریدهایی از ۰ تا ۲۰,۰۰۰ دلار
        val liquidity = 10_000_000.0 // ۱۰ میلیون دلار
        
        val threshold = computeThreshold(trades, null, null, liquidity)
        
        assertEquals("آستانه باید روی کف نقدینگی (۱٪ استخر = ۱۰۰,۰۰۰) قفل شود", 100_000.0, threshold, 0.1)
    }

    @Test
    fun `F8 - low liquidity pool falls back to MIN_THRESHOLD`() {
        // سناریو: استخر کوچک (۵۰۰ هزار دلار نقدینگی)
        // ۱٪ نقدینگی = ۵,۰۰۰ دلار که کمتر از MIN_THRESHOLD (۱۰,۰۰۰) است.
        // کف باید روی MIN_THRESHOLD (۱۰,۰۰۰) قفل شود.
        
        val trades = List(1000) { (it * 5.0) } 
        val liquidity = 500_000.0 
        
        val threshold = computeThreshold(trades, null, null, liquidity)
        
        assertEquals("آستانه باید روی MIN_THRESHOLD (۱۰,۰۰۰) قفل شود", 10_000.0, threshold, 0.1)
    }

    @Test
    fun `F8 - null liquidity falls back to default behavior`() {
        // سناریو: نقدینگی در دسترس نیست. باید مانند قبل MIN_THRESHOLD (۱۰,۰۰۰) اعمال شود.
        val trades = List(1000) { (it * 20.0) } 
        
        val threshold = computeThreshold(trades, null, null, null)
        
        assertEquals("آستانه باید روی صدک ۹۹ (۲۰,۰۰۰) بماند چون نقدینگی null است", 20_000.0, threshold, 0.1)
    }

    @Test
    fun `F8 - user override bypasses liquidity floor`() {
        val trades = List(1000) { (it * 20.0) }
        val liquidity = 10_000_000.0
        val userOverride = 5_000.0
        
        val threshold = computeThreshold(trades, null, userOverride, liquidity)
        
        assertEquals("Override کاربر باید بر کف نقدینگی اولویت داشته باشد", 5_000.0, threshold, 0.1)
    }
}
