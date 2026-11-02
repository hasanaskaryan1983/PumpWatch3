package com.pumpwatch.app.engine

import com.pumpwatch.app.ui.PaperState
import com.pumpwatch.app.ui.PaperTrade

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperRulesF10Test {

    @Test
    fun testF10_RecomputeCashDeductsFeesAndSlippage() {
        // سناریو: سرمایه اولیه ۱۰۰۰ دلار
        // باز کردن معامله ۱۰۰ دلاری (وجه نقد باید ۹۰۰ شود)
        // بستن معامله با ۱۰٪ سود ناخالص (۱۰ دلار)، اما با ۰.۳٪ هزینه رفت‌وبرگشت (۰.۳ دلار)
        // سود خالص = ۹.۷ دلار. وجه نقد نهایی باید ۹۰۰ + ۱۰۰ + ۹.۷ = ۱۰۰۹.۷ دلار باشد.
        
        val state = PaperState(initialCapital = 1000.0, cash = 1000.0, trades = mutableListOf())
        
        // ۱. شبیه‌سازی معامله باز
        state.trades.add(PaperTrade(
            symbol = "BTC", status = "OPEN", sizeUsd = 100.0, entry = 100.0, price = 100.0
        ))
        
        var cashAfterOpen = PaperRules.recomputeCash(state)
        assertEquals("وجه نقد پس از باز کردن معامله باید ۹۰۰ باشد", 900.0, cashAfterOpen, 0.01)
        
        // ۲. شبیه‌سازی بستن معامله با ۱۰٪ سود
        state.trades[0].status = "CLOSED"
        state.trades[0].pnl = 10.0 // ۱۰٪ سود ناخالص
        
        val finalCash = PaperRules.recomputeCash(state)
        
        // محاسبه دستی مورد انتظار:
        // هزینه = 100 * (0.3 / 100) = 0.3
        // سود خالص = (100 * 0.10) - 0.3 = 9.7
        // وجه نقد = 1000 (اولیه) - 100 (قفل‌شده، که حالا آزاد شده) + 100 (اصل) + 9.7 (سود خالص) = 1009.7
        val expectedCash = 1009.7
        
        assertEquals("وجه نقد نهایی باید سود خالص پس از کسر هزینه را منعکس کند", expectedCash, finalCash, 0.01)
    }

    @Test
    fun testF10_RecomputeCashHandlesLossCorrectly() {
        val state = PaperState(initialCapital = 1000.0, cash = 1000.0, trades = mutableListOf())
        
        state.trades.add(PaperTrade(
            symbol = "ETH", status = "CLOSED", sizeUsd = 200.0, entry = 100.0, price = 90.0, pnl = -10.0 // ۱۰٪ ضرر
        ))
        
        val finalCash = PaperRules.recomputeCash(state)
        
        // هزینه = 200 * 0.003 = 0.6
        // ضرر ناخالص = 200 * -0.10 = -20
        // ضرر خالص = -20 - 0.6 = -20.6
        // وجه نقد = 1000 - 20.6 = 979.4
        val expectedCash = 979.4
        
        assertEquals("وجه نقد باید ضرر خالص (شامل هزینه) را کسر کند", expectedCash, finalCash, 0.01)
    }
}
