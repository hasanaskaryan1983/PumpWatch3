package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 48.2: تست‌های JVM برای ChainRegistry و validators.
 * آدرس‌ها با شمارش دقیق کاراکتر:
 * - Solana معتبر: 44 کاراکتر base58
 * - Solana کوتاه: 31 کاراکتر (زیر حداقل 32)
 * - EVM معتبر: 0x + 40 رقم hex (کانترکت واقعی USDC)
 * - EVM نامعتبر: 0x + 39 رقم hex
 */
class ChainRegistryTest {

    // 44 کاراکتر: 7xKXtg2C(8) W87d97TX(16) JSDpbD5j(24) BkheTqA8(32) 3TZRuJos(40) gAsU(44)
    private val SOLANA_VALID = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

    // 31 کاراکتر: 7xKXtg2C(8) W87d97TX(16) JSDpbD5j(24) BkheTqA(31)
    private val SOLANA_TOO_SHORT = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA"

    // 0x + 40 hex: A0b86991(8) c6218b36(16) c1d19D4a(24) 2e9Eb0cE(32) 3606eB48(40)
    private val EVM_VALID = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"

    // 0x + 39 hex (یک رقم کمتر)
    private val EVM_WRONG_LENGTH = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB4"

    @Test
    fun solanaAddress_isValid() {
        assertTrue(ChainRegistry.isValidAddress("solana", SOLANA_VALID))
        assertEquals("solana", ChainRegistry.detectChain(SOLANA_VALID)?.chainId)
    }

    @Test
    fun solanaAddress_tooShort_isInvalid() {
        assertFalse(ChainRegistry.isValidAddress("solana", SOLANA_TOO_SHORT))
        assertNull(ChainRegistry.detectChain(SOLANA_TOO_SHORT))
    }

    @Test
    fun evmAddress_isValid() {
        assertTrue(ChainRegistry.isValidAddress("1", EVM_VALID))
        assertEquals("1", ChainRegistry.detectChain(EVM_VALID)?.chainId)
    }

    @Test
    fun evmAddress_wrongLength_isInvalid() {
        assertFalse(ChainRegistry.isValidAddress("1", EVM_WRONG_LENGTH))
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
        assertNull(ChainRegistry.detectChain("invalid_address_123"))
    }

    @Test
    fun explorerLink_isCorrect() {
        val link = ChainRegistry.explorerLink("1", EVM_VALID)
        assertEquals("https://etherscan.io/address/$EVM_VALID", link)
    }

    @Test
    fun addressValidator_normalize_trimsWhitespace() {
        assertEquals(EVM_VALID, AddressValidator.normalize("  $EVM_VALID  "))
    }
}
