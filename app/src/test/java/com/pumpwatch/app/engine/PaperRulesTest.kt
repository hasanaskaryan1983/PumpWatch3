package com.pumpwatch.app.engine

import com.pumpwatch.app.ui.PaperState
import com.pumpwatch.app.ui.PaperTrade
import org.junit.Assert.*
import org.junit.Test

class PaperRulesTest {

    private fun openTrade(sizeUsd: Double): PaperTrade =
        PaperTrade(symbol = "BTC", sizeUsd = sizeUsd, status = "OPEN")

    private fun closedTrade(sizeUsd: Double, pnlPct: Double): PaperTrade =
        PaperTrade(symbol = "ETH", sizeUsd = sizeUsd, pnl = pnlPct, status = "CLOSED")

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
        assertEquals(0.3, PaperRules.roundTripCostPct(), 0.0001)
    }

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
    fun `closed winning trade returns stake plus profit minus fees`() {
        // 100 size, 10% pnl -> gross 10. cost 0.3. net 9.7. initial 1000 + 9.7 = 1009.7
        val state = PaperState(trades = mutableListOf(closedTrade(100.0, 10.0)))
        assertEquals(1009.7, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `closed losing trade returns stake minus loss minus fees`() {
        // 100 size, -50% pnl -> gross -50. cost 0.3. net -50.3. initial 1000 - 50.3 = 949.7
        val state = PaperState(trades = mutableListOf(closedTrade(100.0, -50.0)))
        assertEquals(949.7, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `closed flat trade returns exactly stake minus fees`() {
        // 200 size, 0% pnl -> gross 0. cost 0.6. net -0.6. initial 1000 - 0.6 = 999.4
        val state = PaperState(trades = mutableListOf(closedTrade(200.0, 0.0)))
        assertEquals(999.4, PaperRules.recomputeCash(state), 0.0001)
    }

    @Test
    fun `mixed open and closed trades combine correctly`() {
        // open 200 -> -200. closed 100 @ 20% -> gross 20, cost 0.3, net 19.7.
        // 1000 + 19.7 - 200 = 819.7
        val state = PaperState(
            trades = mutableListOf(openTrade(200.0), closedTrade(100.0, 20.0))
        )
        assertEquals(819.7, PaperRules.recomputeCash(state), 0.0001)
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
        val state = PaperState(cash = 99999.0, trades = mutableListOf(openTrade(100.0)))
        assertEquals(900.0, PaperRules.recomputeCash(state), 0.0001)
    }
}
