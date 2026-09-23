package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 48: تست‌های JVM برای ChainRegistry و validators.
 */
class ChainRegistryTest {

    @Test
    fun solanaAddress_isValid() {
        val addr = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
        assertTrue(ChainRegistry.isValidAddress("solana", addr))
        assertEquals("solana", ChainRegistry.detectChain(addr)?.chainId)
    }

    @Test
    fun solanaAddress_tooShort_isInvalid() {
        val addr = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgA" // 31 chars
        assertFalse(ChainRegistry.isValidAddress("solana", addr))
    }

    @Test
    fun evmAddress_isValid() {
        val addr = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
        assertTrue(ChainRegistry.isValidAddress("1", addr))
        assertEquals("1", ChainRegistry.detectChain(addr)?.chainId)
    }

    @Test
    fun evmAddress_wrongLength_isInvalid() {
        val addr = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bE" // 39 chars
        assertFalse(ChainRegistry.isValidAddress("1", addr))
    }

    @Test
    fun suiAddress_isValid() {
        val addr = "0x" + "a".repeat(64)
        assertTrue(ChainRegistry.isValidAddress("sui", addr))
        assertEquals("sui", ChainRegistry.detectChain(addr)?.chainId)
    }

    @Test
    fun tonAddress_isValid() {
        val addr = "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2"
        assertTrue(ChainRegistry.isValidAddress("ton", addr))
        assertEquals("ton", ChainRegistry.detectChain(addr)?.chainId)
    }

    @Test
    fun invalidAddress_returnsNull() {
        val addr = "invalid_address_123"
        assertNull(ChainRegistry.detectChain(addr))
    }

    @Test
    fun explorerLink_isCorrect() {
        val addr = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
        val link = ChainRegistry.explorerLink("1", addr)
        assertEquals("https://etherscan.io/address/$addr", link)
    }

    @Test
    fun addressValidator_normalize_trimsWhitespace() {
        val addr = "  0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb  "
        assertEquals("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb", AddressValidator.normalize(addr))
    }
}
