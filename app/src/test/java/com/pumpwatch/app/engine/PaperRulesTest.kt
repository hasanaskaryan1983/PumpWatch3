package com.pumpwatch.app.engine

import com.pumpwatch.app.ui.PaperState
import com.pumpwatch.app.ui.PaperTrade
import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 124: تست مدل هزینهٔ واحد و باز محاسبهٔ cash (کامیت ۱۰۷).
 *
 * چرا مهم؟
 * - roundTripCostPct تنها جای مجاز برای تعریف هزینهٔ رفت‌وبرگشت است (۰.۳٪)
 * - recomputeCash تنها منبع حقیقت برای cash است (نه مقدار ذخیره‌شده)
 *
 * اگر این دو بشکنند، کل ledger کاغذی اشتباه می‌شود.
 */
class PaperRulesTest {

    private fun openTrade(sizeUsd: Double): PaperTrade =
        PaperTrade(symbol = "BTC", sizeUsd = sizeUsd, status = "OPEN")

    private fun closedTrade(sizeUsd: Double, pnlPct: Double): PaperTrade =
        PaperTrade(symbol = "ETH", sizeUsd = sizeUsd, pnl = pnlPct, status = "CLOSED")

    // ========== ثابت‌ها ==========

    @Test
    fun `fee constant is 0_1 percent`() {
        assertEquals(0.1, PaperRules.FEE_PCT, 0.0001)
    }

    @Test
    fun `slippage constant is 0_05 percent`() {
        assertEquals(0.05, PaperRules.SLIPPAGE_PCT, 0.0001)
    }

    @Test
    fun `default capital is 1000`() {
        assertEquals(1000.0, PaperRules.DEFAULT_CAPITAL, 0.0001)
    }

    @Test
    fun `round trip cost is 0_3 percent`() {
        // 2 × (0.1 + 0.05) = 0.3
        assertEquals(0.3, PaperRules.roundTripCostPct(), 0.0001)
    }

    // ========== recomputeCash ==========

    @Test
    fun `empty state returns default capital`() {
        val state = PaperState()
        assertEquals(1000.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `open trade reduces cash by stake`() {
        val state = PaperState(trades = mutableListOf(openTrade(100.0)))
        assertEquals(900.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `closed winning trade returns stake plus profit`() {
        // 100 × (1 + 10/100) = 110 → 1000 + 110 = 1110
        val state = PaperState(trades = mutableListOf(closedTrade(100.0, 10.0)))
        assertEquals(1110.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `closed losing trade returns stake minus loss`() {
        // 100 × (1 - 50/100) = 50 → 1000 + 50 = 1050
        val state = PaperState(trades = mutableListOf(closedTrade(100.0, -50.0)))
        assertEquals(1050.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `closed flat trade returns exactly stake`() {
        val state = PaperState(trades = mutableListOf(closedTrade(200.0, 0.0)))
        assertEquals(1200.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `mixed open and closed trades combine correctly`() {
        // open 200 → -200 ; closed 100 @ +20% → +120
        // 1000 - 200 + 120 = 920
        val state = PaperState(
            trades = mutableListOf(openTrade(200.0), closedTrade(100.0, 20.0))
        )
        assertEquals(920.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `multiple open trades sum stakes`() {
        val state = PaperState(
            trades = mutableListOf(openTrade(100.0), openTrade(150.0), openTrade(50.0))
        )
        assertEquals(700.0, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `recomputeCash ignores stored cash value`() {
        // حتی اگر cash ذخیره‌شده غلط باشد، recompute از روی تریدها حساب می‌کند
        val state = PaperState(cash = 99999.0, trades = mutableListOf(openTrade(100.0)))
        assertEquals(900.0, PaperRules.recomputeCash(state), 0.0001)
    }
}
