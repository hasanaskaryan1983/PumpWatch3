package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TradePnLTest {

    @Test
    fun futures_pnl_must_apply_leverage() {
        val trade = createBaseTrade(
            entryPrice = 100.0,
            exitPrice = 110.0,  // 10% افزایش
            side = "PUMP",
            leverage = 10.0,    // اهرم 10x
            feePct = 0.1,
            slippagePct = 0.0,
            fundingPaid = 0.0
        )
        
        // 10% × 10 = 100% سود، منهای کارمزد 0.2% (ورود و خروج)
        val pnl = trade.realizedPnl()
        assertEquals("PnL با اهرم 10x باید حدود 99.8% باشد", 99.8, pnl, 0.1)
    }

    @Test
    fun futures_pnl_must_deduct_funding() {
        val trade = createBaseTrade(
            entryPrice = 100.0,
            exitPrice = 100.0,  // بدون تغییر قیمت
            side = "PUMP",
            leverage = 1.0,
            feePct = 0.1,
            slippagePct = 0.0,
            fundingPaid = 5.0,  // 5 دلار فاندینگ پرداختی
            sizeUsd = 100.0
        )
        
        val pnl = trade.realizedPnl()
        // سود 0، کارمزد -0.2، فاندینگ -5% (5 دلار از 100 دلار)
        assertEquals("فاندینگ باید از PnL کسر شود", -5.2, pnl, 0.1)
    }

    @Test
    fun entry_price_zero_or_negative_must_not_produce_nan() {
        val trade = createBaseTrade(
            entryPrice = 0.0,
            exitPrice = 100.0,
            side = "PUMP",
            leverage = 10.0
        )
        
        val pnl = trade.realizedPnl()
        assertFalse("PnL نباید NaN باشد", pnl.isNaN())
        assertEquals("PnL با entryPrice صفر باید 0.0 باشد", 0.0, pnl, 0.01)
    }

    @Test
    fun spot_trade_with_leverage_1_behaves_normally() {
        val trade = createBaseTrade(
            entryPrice = 100.0,
            exitPrice = 110.0,
            side = "PUMP",
            leverage = 1.0,  // اسپات عادی
            feePct = 0.1,
            slippagePct = 0.0,
            fundingPaid = 0.0
        )
        
        val pnl = trade.realizedPnl()
        // 10% سود منهای 0.2% کارمزد
        assertEquals("PnL اسپات عادی باید حدود 9.8% باشد", 9.8, pnl, 0.1)
    }

    private fun createBaseTrade(
        entryPrice: Double,
        exitPrice: Double,
        side: String,
        leverage: Double,
        feePct: Double = 0.1,
        slippagePct: Double = 0.0,
        fundingPaid: Double = 0.0,
        sizeUsd: Double = 100.0
    ): Trade {
        return Trade(
            id = "test_1",
            coinId = "bitcoin",
            symbol = "BTC",
            name = "Bitcoin",
            side = side,
            mode = if (leverage > 1.0) "FUT" else "SPOT",
            entryPrice = entryPrice,
            currentPrice = exitPrice,
            entryTime = 1000L,
            exitTime = 2000L,
            exitPrice = exitPrice,
            initialStop = entryPrice * 0.95,
            currentStop = entryPrice * 0.95,
            target1 = entryPrice * 1.1,
            target2 = entryPrice * 1.2,
            exitReason = "TARGET",
            status = "CLOSED",
            sizeUsd = sizeUsd,
            leverage = leverage,
            fundingPaid = fundingPaid,
            feePct = feePct,
            slippagePct = slippagePct
        )
    }
}
