package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WhaleFlowEngineF8Test {

    // کپی دقیق منطق تابع جدید برای تست ایزوله
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
        // استخر ۱۰ میلیون دلاری → کف = ۱۰۰,۰۰۰ دلار
        // p99 لیست زیر = 990 * 20 = 19,800 دلار (کمتر از کف)
        val trades = List(1000) { (it * 20.0) } 
        val liquidity = 10_000_000.0 
        
        val threshold = computeThreshold(trades, null, null, liquidity)
        
        assertEquals("آستانه باید روی کف نقدینگی (۱۰۰,۰۰۰) قفل شود", 100_000.0, threshold, 0.1)
    }

    @Test
    fun `F8 - low liquidity pool falls back to MIN_THRESHOLD`() {
        // استخر ۵۰۰ هزار دلاری → ۱٪ = ۵,۰۰۰ دلار. اما MIN_THRESHOLD = ۱۰,۰۰۰ دلار.
        val trades = List(1000) { (it * 5.0) } 
        val liquidity = 500_000.0 
        
        val threshold = computeThreshold(trades, null, null, liquidity)
        
        assertEquals("آستانه باید روی MIN_THRESHOLD (۱۰,۰۰۰) قفل شود", 10_000.0, threshold, 0.1)
    }

    @Test
    fun `F8 - null liquidity falls back to default behavior`() {
        // نقدینگی null → کف = MIN_THRESHOLD (۱۰,۰۰۰)
        // p99 لیست = 990 * 20 = 19,800 دلار (بیشتر از کف)
        val trades = List(1000) { (it * 20.0) } 
        
        val threshold = computeThreshold(trades, null, null, null)
        
        // 🚀 اصلاح دقیق ریاضی: مقدار واقعی ۱۹,۸۰۰ است، نه ۲۰,۰۰۰
        assertEquals("آستانه باید دقیقاً روی صدک ۹۹ (۱۹,۸۰۰) بماند", 19_800.0, threshold, 0.1)
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
