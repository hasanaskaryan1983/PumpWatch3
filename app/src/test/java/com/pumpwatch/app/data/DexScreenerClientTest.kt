package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تست‌های واحد برای انتخاب استخر/قیمت DexScreener (Sprint 10 / C2).
 */
class DexScreenerClientTest {

    private fun pair(
        chain: String,
        base: String,
        liq: Double?,
        price: String?
    ) = DexPair(
        chainId = chain,
        baseToken = DexToken(base, "X", "X"),
        quoteToken = DexToken("0xquote", "USDC", "USDC"),
        priceUsd = price,
        liquidity = DexLiquidity(liq)
    )

    @Test
    fun `picks most liquid pair on requested chain`() {
        val pairs = listOf(
            pair("solana", "M1", 1_000.0, "0.10"),
            pair("solana", "M1", 50_000.0, "0.25")
        )
        assertEquals(0.25, bestPriceUsd(pairs, "solana", "M1")!!, 1e-12)
    }

    @Test
    fun `ignores pairs from other chains`() {
        val pairs = listOf(pair("bsc", "M1", 999_999.0, "9.99"))
        assertNull(bestPriceUsd(pairs, "solana", "M1"))
    }

    @Test
    fun `ignores pairs where address is quote not base`() {
        val p = DexPair(
            chainId = "solana",
            baseToken = DexToken("0xquote", "USDC", "USDC"),
            quoteToken = DexToken("M1", "X", "X"),
            priceUsd = "5.0",
            liquidity = DexLiquidity(10_000.0)
        )
        assertNull(bestPriceUsd(listOf(p), "solana", "M1"))
    }

    @Test
    fun `zero or null liquidity is not trustworthy`() {
        val pairs = listOf(
            pair("solana", "M1", 0.0, "1.0"),
            pair("solana", "M1", null, "1.0")
        )
        assertNull(bestPriceUsd(pairs, "solana", "M1"))
    }

    @Test
    fun `garbage or non-positive priceUsd yields null`() {
        val pairs = listOf(
            pair("solana", "M1", 10_000.0, "abc"),
            pair("solana", "M1", 9_000.0, "0")
        )
        assertNull(bestPriceUsd(pairs, "solana", "M1"))
    }

    @Test
    fun `null or empty pairs yields null`() {
        assertNull(bestPriceUsd(null, "solana", "M1"))
        assertNull(bestPriceUsd(emptyList(), "solana", "M1"))
    }
}
