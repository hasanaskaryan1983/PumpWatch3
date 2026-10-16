package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainRegistryTest {

    // === detectChain: باگ اصلی قبلی (هر EVM → Ethereum) باید fix شده باشد ===

    @Test
    fun `detectChain returns EVM_GENERIC for EVM addresses - NOT always Ethereum`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        val detected = ChainRegistry.detectChain(addr)
        assertNotNull("Any valid 0x address should be detected", detected)
        assertEquals(
            "EVM address must return EVM_GENERIC, not Ethereum",
            "evm", detected!!.chainId
        )
        assertNotEquals(
            "detectChain must NOT return '1' for arbitrary EVM address",
            "1", detected.chainId
        )
    }

    @Test
    fun `detectChain correctly identifies Solana addresses`() {
        val addr = "So11111111111111111111111111111111111111112"
        val detected = ChainRegistry.detectChain(addr)
        assertNotNull(detected)
        assertEquals("solana", detected!!.chainId)
    }

    @Test
    fun `detectChain correctly identifies TON addresses`() {
        val addr = "EQAbcDefGhi_123"
        val detected = ChainRegistry.detectChain(addr)
        assertNotNull(detected)
        assertEquals("ton", detected!!.chainId)
    }

    @Test
    fun `detectChain returns null for invalid addresses`() {
        assertNull(ChainRegistry.detectChain(""))
        assertNull(ChainRegistry.detectChain("not-a-valid-address"))
        assertNull(ChainRegistry.detectChain("0xTOOSHORT"))
    }

    // === resolveChain: مسیر اصلی و دقیق ===

    @Test
    fun `resolveChain with hint returns exact network`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        assertEquals("8453", ChainRegistry.resolveChain("8453", addr)?.chainId)
        assertEquals("137", ChainRegistry.resolveChain("137", addr)?.chainId)
        assertEquals("1", ChainRegistry.resolveChain("1", addr)?.chainId)
        assertEquals("56", ChainRegistry.resolveChain("56", addr)?.chainId)
        assertEquals("42161", ChainRegistry.resolveChain("42161", addr)?.chainId)
        assertEquals("10", ChainRegistry.resolveChain("10", addr)?.chainId)
    }

    @Test
    fun `resolveChain falls back to detectChain when hint is null or blank`() {
        val addr = "So11111111111111111111111111111111111111112"
        assertEquals("solana", ChainRegistry.resolveChain(null, addr)?.chainId)
        assertEquals("solana", ChainRegistry.resolveChain("", addr)?.chainId)
    }

    @Test
    fun `resolveChain with unknown hint falls back to detect`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        val resolved = ChainRegistry.resolveChain("unknown_chain_xyz", addr)
        assertNotNull(resolved)
        assertEquals("evm", resolved!!.chainId)
    }

    // === validation ===

    @Test
    fun `isValidAddress works for specific chains`() {
        val evm = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        assertTrue(ChainRegistry.isValidAddress("1", evm))
        assertTrue(ChainRegistry.isValidAddress("8453", evm))
        assertTrue(ChainRegistry.isValidAddress("137", evm))

        val sol = "So11111111111111111111111111111111111111112"
        assertTrue(ChainRegistry.isValidAddress("solana", sol))
        assertTrue(!ChainRegistry.isValidAddress("solana", evm))
    }

    // === cross-chain isolation (ترکیب AssetId + ChainRegistry) ===

    @Test
    fun `same contract on different EVM chains produces different AssetId keys`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        val eth = AssetId.evm("1", addr)
        val base = AssetId.evm("8453", addr)
        val polygon = AssetId.evm("137", addr)
        val bsc = AssetId.evm("56", addr)

        assertNotEquals(eth.key(), base.key())
        assertNotEquals(eth.key(), polygon.key())
        assertNotEquals(eth.key(), bsc.key())
        assertNotEquals(base.key(), polygon.key())
        assertNotEquals(base.key(), bsc.key())
        assertNotEquals(polygon.key(), bsc.key())
    }

    // === explorer ===

    @Test
    fun `explorerLink returns correct URL for known chains`() {
        val addr = "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
        assertEquals(
            "https://etherscan.io/address/$addr",
            ChainRegistry.explorerLink("1", addr)
        )
        assertEquals(
            "https://basescan.org/address/$addr",
            ChainRegistry.explorerLink("8453", addr)
        )
        assertNull("EVM_GENERIC has no explorer", ChainRegistry.explorerLink("evm", addr))
        assertNull("unknown chain returns null", ChainRegistry.explorerLink("xyz", addr))
    }
}
