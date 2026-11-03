package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeF2Test {

    @Test
    fun `F2 - Guard prevents NaN when entryPrice is zero`() {
        // سناریو: قیمت ورودی نامعتبر (صفر یا منفی) باید ۰.۰ برگرداند، نه NaN یا Infinity
        val trade = Trade(
            id = "1", coinId = "btc", symbol = "BTCUSDT", name = "Bitcoin",
            side = "PUMP", mode = "FUT", entryPrice = 0.0, currentPrice = 100.0,
            entryTime = System.currentTimeMillis(), exitTime = null, exitPrice = null,
            initialStop = 90.0, currentStop = 90.0, target1 = 110.0, target2 = 120.0,
            exitReason = null, status = "OPEN", sizeUsd = 1000.0, leverage = 10.0
        )
        
        assertEquals("PnL لحظه‌ای باید برای قیمت ورودی صفر، ۰.۰ باشد", 0.0, trade.unrealizedPnl(), 0.001)
        assertEquals("PnL تحقق‌یافته باید برای قیمت ورودی صفر، ۰.۰ باشد", 0.0, trade.realizedPnl(), 0.001)
    }

    @Test
    fun `F2 - Leveraged PnL correctly deducts fees and funding`() {
        // سناریو: معامله ۱۰۰۰ دلاری با اهرم ۱۰، قیمت از ۱۰۰ به ۱۱۰ می‌رود (۱۰٪ سود قیمت)
        // سود ناخالص اهرم‌دار = ۱۰٪ * ۱۰ = ۱۰۰٪
        // هزینه رفت‌وبرگشت = (feePct 0.1% * 2) + slippage 0.1% = ۰.۳٪
        // فاندینگ پرداختی = ۵.۰ دلار. تأثیر درصدی روی ۱۰۰۰ دلار = (5.0 * 100.0) / 1000.0 = ۰.۵٪
        // PnL خالص درصدی مورد انتظار = ۱۰۰.۰ - ۰.۳ - ۰.۵ = ۹۹.۲٪
        
        val trade = Trade(
            id = "2", coinId = "eth", symbol = "ETHUSDT", name = "Ethereum",
            side = "PUMP", mode = "FUT", entryPrice = 100.0, currentPrice = 110.0,
            entryTime = System.currentTimeMillis(), exitTime = System.currentTimeMillis() + 3600000, 
            exitPrice = 110.0, initialStop = 90.0, currentStop = 90.0, target1 = 120.0, target2 = 130.0,
            exitReason = "TARGET", status = "CLOSED", sizeUsd = 1000.0, 
            leverage = 10.0, feePct = 0.1, slippagePct = 0.1, fundingPaid = 5.0
        )
        
        val expectedPnlPct = 99.2
        assertEquals("PnL تحقق‌یافته درصدی باید اهرم، کارمزد و فاندینگ را به‌درستی کسر کند", expectedPnlPct, trade.realizedPnl(), 0.01)
        
        // بررسی معادل دلاری: ۱۰۰۰ دلار * ۹۹.۲٪ = ۹۹۲.۰ دلار سود خالص
        assertEquals("PnL تحقق‌یافته دلاری باید صحیح باشد", 992.0, trade.realizedPnlUsd(), 0.01)
    }

    @Test
    fun `F2 - Short position (DUMP) calculates inverse PnL correctly`() {
        // سناریو: شورت (DUMP) با اهرم ۵. قیمت از ۲۰۰ به ۱۹۰ می‌رود (۵٪ کاهش قیمت = ۲۵٪ سود اهرم‌دار)
        val trade = Trade(
            id = "3", coinId = "sol", symbol = "SOLUSDT", name = "Solana",
            side = "DUMP", mode = "FUT", entryPrice = 200.0, currentPrice = 190.0,
            entryTime = System.currentTimeMillis(), exitTime = System.currentTimeMillis() + 3600000, 
            exitPrice = 190.0, initialStop = 220.0, currentStop = 220.0, target1 = 180.0, target2 = 170.0,
            exitReason = "TARGET", status = "CLOSED", sizeUsd = 500.0, 
            leverage = 5.0, feePct = 0.1, slippagePct = 0.1, fundingPaid = 0.0
        )
        
        // basePct = -(190 - 200) / 200 * 100 = 5.0%
        // leveragedPct = 5.0 * 5.0 = 25.0%
        // deductions = (0.1 * 2) + 0.1 = 0.3%
        // expected = 25.0 - 0.3 = 24.7%
        assertEquals("PnL شورت باید جهت معکوس قیمت را به‌درستی محاسبه کند", 24.7, trade.realizedPnl(), 0.01)
    }
}
