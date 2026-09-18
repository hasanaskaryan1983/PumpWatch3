package com.pumpwatch.app.data

import com.pumpwatch.app.engine.WhaleFlowResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۲ / Commit 5): تست pure زیرساخت Provenance
 */
class ProvenanceTest {

    @Test
    fun canonical_key_is_case_insensitive_and_stable() {
        val a = AssetId("Solana", "ABC123", null, "GECKO", "BONK")
        val b = AssetId("solana", "abc123", null, "gecko", "bonk")
        assertEquals(a.canonicalKey(), b.canonicalKey())
    }

    @Test
    fun same_ticker_different_chain_are_different_assets() {
        // درس P0#9: ticker به‌تنهایی هویت نیست
        val sol = AssetId("solana", "So1111", null, "DEX", "SOL")
        val fake = AssetId("base", "0xabc", null, "DEX", "SOL")
        assertNotEquals(sol.canonicalKey(), fake.canonicalKey())
    }

    @Test
    fun native_coin_has_no_contract() {
        assertTrue(AssetId("bitcoin", null, null, "SPOT", "BTC").isNative())
    }

    @Test
    fun whale_source_labels_are_honest() {
        fun res(src: String) = WhaleFlowResult("BTC", src, 0L, 0, 0, 0.0, 0.0, 0.0, 0.5, "BALANCED")
        assertEquals("Binance", res("BINANCE").sourceLabel())
        assertEquals("Binance", res("BINANCE_AGG").sourceLabel())
        assertEquals("Bybit", res("BYBIT").sourceLabel())
        assertEquals("DEX آن‌چین", res("GECKO_DEX").sourceLabel())
    }

    @Test
    fun freshness_never_says_live_for_disk_data() {
        val now = System.currentTimeMillis()
        val disk = MarketMeta(now, ServedFrom.DISK_CACHE, 100)
        assertTrue("disk data must never be labeled live", disk.freshnessLabel().contains("آفلاین"))

        val live = MarketMeta(now, ServedFrom.NETWORK, 100)
        assertTrue(live.freshnessLabel().contains("زنده"))

        val old = MarketMeta(now - 3_600_000L, ServedFrom.MEM_CACHE, 100)
        assertTrue("1h-old data must be labeled stale", old.freshnessLabel().contains("مانده"))
    }
}
