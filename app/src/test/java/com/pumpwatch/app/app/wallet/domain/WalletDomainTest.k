package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * 🚀 Commit 47: تست‌های JVM مدل‌های دامنه (معیار قبولی فاز ۲).
 */
class AssetIdTest {
    @Test
    fun sameSymbolDifferentContract_areDifferentAssets() {
        // دو توکن هم‌نام با contract متفاوت نباید یکی تلقی شوند
        val a = AssetId.solana("MintAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        val b = AssetId.solana("MintBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
        assertNotEquals(a.key(), b.key())
    }

    @Test
    fun nativeAsset_hasNullContract_andNativeStandard() {
        val n = AssetId.native("solana")
        assertEquals(null, n.contractAddress)
        assertEquals("NATIVE", n.standard)
        assertTrue(n.isNative)
    }

    @Test
    fun evmKey_isCaseInsensitive() {
        assertEquals(
            AssetId.evm("1", "0xABCDEF0123456789ABCDEF0123456789ABCDEF01").key(),
            AssetId.evm("1", "0xabcdef0123456789abcdef0123456789abcdef01").key()
        )
    }
}

class PriceQuoteTest {
    @Test
    fun staleDetection_respectsTtl() {
        val q = PriceQuote(
            asset = AssetId.solana("mintX"),
            priceUsd = BigDecimal("1.5"),
            source = "dex",
            observedAtMs = 1_000L,
            liquidityUsd = null,
            confidence = Confidence.MEDIUM
        )
        assertTrue(q.isStale(nowMs = 2_001L, ttlMs = 1_000L))
        assertFalse(q.isStale(nowMs = 1_500L, ttlMs = 1_000L))
    }

    @Test
    fun unknownPrice_isNotZero() {
        val q = PriceQuote(AssetId.solana("mintY"), null, "none", 0L, null, Confidence.UNKNOWN)
        assertTrue(q.isUnknown)
        assertEquals(null, q.priceUsd) // نامعلوم ≠ صفر
    }
}
