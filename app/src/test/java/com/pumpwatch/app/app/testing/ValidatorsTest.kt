package com.pumpwatch.app.testing

import com.pumpwatch.app.engine.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    private val evmOk = "0x" + "a".repeat(40)
    private val solOk = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
    private val tonOk = "EQ" + "A".repeat(46)

    @Test
    fun `evm address valid`() { assertNull(Validators.validateWalletAddress(evmOk)) }

    @Test
    fun `evm address wrong length rejected`() { assertNotNull(Validators.validateWalletAddress("0xabc")) }

    @Test
    fun `solana address valid`() { assertNull(Validators.validateWalletAddress(solOk)) }

    @Test
    fun `ton address valid`() { assertNull(Validators.validateWalletAddress(tonOk)) }

    @Test
    fun `whitespace inside address rejected`() {
        val broken = evmOk.substring(0, 20) + " " + evmOk.substring(20)
        assertNotNull(Validators.validateWalletAddress(broken))
    }

    @Test
    fun `empty address rejected`() { assertNotNull(Validators.validateWalletAddress("   ")) }

    @Test
    fun `detect chain families`() {
        assertEquals("evm", Validators.detectChainFamily(evmOk))
        assertEquals("solana", Validators.detectChainFamily(solOk))
        assertEquals("ton", Validators.detectChainFamily(tonOk))
        assertEquals("unknown", Validators.detectChainFamily("zzz"))
    }

    @Test
    fun `symbol rules`() {
        assertNull(Validators.validateSymbol("BTC"))
        assertNull(Validators.validateSymbol("pepe-2.0"))
        assertNotNull(Validators.validateSymbol(""))
        assertNotNull(Validators.validateSymbol("ABCDEFGHIJKLM"))
        assertNotNull(Validators.validateSymbol("BTC USD"))
    }

    @Test
    fun `rule symbol allows star`() {
        assertNull(Validators.validateRuleSymbol("*"))
        assertNull(Validators.validateRuleSymbol(""))
    }

    @Test
    fun `threshold rules`() {
        assertNull(Validators.validateThreshold("65000"))
        assertNull(Validators.validateThreshold("0.05"))
        assertNotNull(Validators.validateThreshold("abc"))
        assertNotNull(Validators.validateThreshold("-1"))
        assertNotNull(Validators.validateThreshold(""))
    }

    @Test
    fun `percent bounds`() {
        assertNull(Validators.validatePercent("10"))
        assertNotNull(Validators.validatePercent("0"))
        assertNotNull(Validators.validatePercent("95"))
    }

    @Test
    fun `mint follows chain family`() {
        assertNull(Validators.validateContractMint(solOk, "solana"))
        assertNotNull(Validators.validateContractMint(evmOk, "solana"))
        assertNull(Validators.validateContractMint(evmOk, "evm"))
        assertNull(Validators.validateContractMint("", "evm"))
    }
}
