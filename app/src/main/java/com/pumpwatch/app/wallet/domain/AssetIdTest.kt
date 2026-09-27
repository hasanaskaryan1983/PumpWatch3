package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetIdTest {

    @Test
    fun `Solana mint preserves exact case - no collision`() {
        val wsol = AssetId.solana("So11111111111111111111111111111111111111112")
        val fake = AssetId.solana("so11111111111111111111111111111111111111112")
        assertNotEquals(
            "Two different Solana mints must have different keys (case-sensitive)",
            wsol.key(), fake.key()
        )
        assertEquals("solana:So11111111111111111111111111111111111111112", wsol.key())
    }

    @Test
    fun `SUI coin type preserves exact case`() {
        val a = AssetId.sui("0x2::sui::SUI")
        val b = AssetId.sui("0x2::sui::sui")
        assertNotEquals(a.key(), b.key())
        assertTrue(a.key().contains("SUI"))
    }

    @Test
    fun `TON jetton master preserves exact case`() {
        val a = AssetId.ton("EQAbcDefGhi")
        val b = AssetId.ton("eqabcdefghi")
        assertNotEquals(a.key(), b.key())
    }

    @Test
    fun `EVM addresses are lowercased for case-insensitive matching`() {
        val upper = AssetId.evm("1", "0xABCDEF1234567890ABCDEF1234567890ABCDEF12")
        val lower = AssetId.evm("1", "0xabcdef1234567890abcdef1234567890abcdef12")
        assertEquals(
            "Same EVM address in different cases must produce same key",
            upper.key(), lower.key()
        )
        assertEquals("1:0xabcdef1234567890abcdef1234567890abcdef12", lower.key())
    }

    @Test
    fun `different EVM chains with same contract do not collide`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        val eth = AssetId.evm("1", addr)
        val base = AssetId.evm("8453", addr)
        val polygon = AssetId.evm("137", addr)
        assertNotEquals(eth.key(), base.key())
        assertNotEquals(eth.key(), polygon.key())
        assertNotEquals(base.key(), polygon.key())
    }

    @Test
    fun `native asset key format`() {
        assertEquals("solana:native", AssetId.native("solana").key())
        assertEquals("1:native", AssetId.native("1").key())
        assertTrue(AssetId.native("solana").isNative)
    }

    @Test
    fun `all EVM chainIds get lowercase treatment`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        val expected = addr.lowercase()
        for (chainId in AssetId.EVM_CHAIN_IDS) {
            val asset = AssetId.evm(chainId, addr)
            assertTrue(
                "chainId=$chainId should lowercase",
                asset.key().endsWith(expected)
            )
        }
    }

    @Test
    fun `EVM_CHAIN_IDS contains expected networks`() {
        assertTrue(AssetId.EVM_CHAIN_IDS.contains("1"))
        assertTrue(AssetId.EVM_CHAIN_IDS.contains("8453"))
        assertTrue(AssetId.EVM_CHAIN_IDS.contains("137"))
        assertTrue(AssetId.EVM_CHAIN_IDS.contains("56"))
        assertTrue(AssetId.EVM_CHAIN_IDS.contains("42161"))
        assertTrue(AssetId.EVM_CHAIN_IDS.contains("10"))
    }
}
