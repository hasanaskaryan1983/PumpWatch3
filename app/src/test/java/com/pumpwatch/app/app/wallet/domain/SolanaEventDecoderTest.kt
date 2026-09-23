package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * 🚀 Commit 50: تست‌های JVM برای decoder سولانا.
 */
class SolanaEventDecoderTest {

    private val WALLET = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU" // 44 کاراکتر
    private val MINT_X = "MintXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    private val MINT_Y = "MintYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"
    private val PUMP = "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA"

    private fun ch(mint: String, delta: String, owner: String = WALLET) =
        SolanaTokenBalanceChange(owner, mint, BigDecimal(delta))

    private fun tx(
        changes: List<SolanaTokenBalanceChange>,
        solLamports: Long? = null,
        programs: List<String> = emptyList()
    ) = SolanaTxSummary(
        wallet = WALLET,
        timestampMs = 1_700_000_000_000L,
        txHash = "5sigTestHash",
        tokenChanges = changes,
        nativeSolDeltaLamports = solLamports,
        programIds = programs
    )

    @Test
    fun tokenInOnly_isTransferIn() {
        val e = SolanaEventDecoder.decode(tx(listOf(ch(MINT_X, "+100"))))
        assertEquals(EventKind.TRANSFER_IN, e?.kind)
        assertEquals(0, BigDecimal("100").compareTo(e!!.amount))
    }

    @Test
    fun tokenOutOnly_isTransferOut() {
        val e = SolanaEventDecoder.decode(tx(listOf(ch(MINT_X, "-50"))))
        assertEquals(EventKind.TRANSFER_OUT, e?.kind)
    }

    @Test
    fun dexTokenInPlusSolOut_isBuy_highConfidence() {
        val e = SolanaEventDecoder.decode(
            tx(listOf(ch(MINT_X, "+1000")), solLamports = -500_000_000L, programs = listOf(PUMP))
        )
        assertEquals(EventKind.BUY, e?.kind)
        assertEquals(Confidence.HIGH, e?.confidence)
    }

    @Test
    fun dexTokenOutPlusSolIn_isSell() {
        val e = SolanaEventDecoder.decode(
            tx(listOf(ch(MINT_X, "-1000")), solLamports = +500_000_000L, programs = listOf(PUMP))
        )
        assertEquals(EventKind.SELL, e?.kind)
    }

    @Test
    fun tokenInPlusSolOut_withoutDex_isUnknown_neverBuy() {
        val e = SolanaEventDecoder.decode(
            tx(listOf(ch(MINT_X, "+1000")), solLamports = -500_000_000L, programs = emptyList())
        )
        assertEquals(EventKind.UNKNOWN, e?.kind)
        assertEquals(Confidence.LOW, e?.confidence)
    }

    @Test
    fun tokenToToken_isSwap() {
        val e = SolanaEventDecoder.decode(tx(listOf(ch(MINT_X, "+100"), ch(MINT_Y, "-20"))))
        assertEquals(EventKind.SWAP, e?.kind)
    }

    @Test
    fun otherOwnerChanges_ignored() {
        val e = SolanaEventDecoder.decode(
            tx(listOf(ch(MINT_X, "+100", owner = "OtherOwner1111111111111111111111111111111111")))
        )
        assertNull(e)
    }

    @Test
    fun invalidWallet_returnsNull() {
        val bad = tx(listOf(ch(MINT_X, "+100"))).copy(wallet = "short")
        assertNull(SolanaEventDecoder.decode(bad))
    }
}
