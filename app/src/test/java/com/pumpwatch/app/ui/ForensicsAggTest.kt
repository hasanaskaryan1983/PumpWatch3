package com.pumpwatch.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ForensicsAggTest {
    @Test
    fun weightedAvgEntryIsPricePerTokenNotPerTrade() {
        val a = ForensicsAgg()
        a.addBuy(5_000.0, 1_000_000.0, 100L)
        a.addBuy(10_000.0, 4_000_000.0, 200L)
        assertEquals(0.003, a.avgEntry(), 1e-12)
    }
    @Test
    fun unknownTokenAmountYieldsZeroNotInfinity() {
        val a = ForensicsAgg()
        a.addBuy(5_000.0, 0.0, 100L)
        assertEquals(0.0, a.avgEntry(), 0.0)
    }
    @Test
    fun negativeTokenAmountIsIgnored() {
        val a = ForensicsAgg()
        a.addBuy(1_000.0, -5.0, 1L)
        assertEquals(0.0, a.tokens, 0.0)
        assertEquals(0.0, a.avgEntry(), 0.0)
    }
    @Test
    fun tracksCountFirstAndMaxSingle() {
        val a = ForensicsAgg()
        a.addBuy(5_000.0, 1_000.0, 300L)
        a.addBuy(9_000.0, 1_000.0, 100L)
        assertEquals(2, a.n)
        assertEquals(100L, a.first)
        assertEquals(9_000.0, a.maxSingle, 0.0)
        assertEquals(14_000.0, a.usd, 0.0)
        assertEquals(2_000.0, a.tokens, 0.0)
        assertEquals(7.0, a.avgEntry(), 1e-12)
    }
    @Test
    fun deltaCarriesTokenAmount() {
        val d = ForensicsDelta("wallet", 150.0, 5_000.0, 42L)
        assertEquals(5_000.0, d.tokens, 0.0)
        assertEquals(0.03, d.usd / d.tokens, 1e-12)
    }
}
