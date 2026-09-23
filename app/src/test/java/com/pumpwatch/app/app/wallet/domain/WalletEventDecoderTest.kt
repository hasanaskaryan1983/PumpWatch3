package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * 🚀 Commit 49: تست‌های JVM برای decoder (معیار قبولی فاز ۴).
 */
class WalletEventDecoderTest {

    private val WALLET = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"   // 40 hex
    private val OTHER = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb1"      // 40 hex
    private val USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"

    private fun transfer(to: String, from: String, raw: String, decimals: Int = 6) =
        EvmTokenTransfer(
            wallet = WALLET,
            chainId = "1",
            contractAddress = USDC,
            decimals = decimals,
            rawValue = raw,
            from = from,
            to = to,
            timestampMs = 1_700_000_000_000L,
            txHash = "0xabc123"
        )

    @Test
    fun incomingTransfer_isTransferIn_withCorrectAmount() {
        val e = WalletEventDecoder.evmTokenTransfer(transfer(to = WALLET, from = OTHER, raw = "1500000"))
        assertEquals(EventKind.TRANSFER_IN, e?.kind)
        // 1500000 با 6 decimals = 1.5
        assertEquals(0, BigDecimal("1.5").compareTo(e!!.amount))
        assertEquals(Confidence.MEDIUM, e.confidence)
        assertEquals("0xabc123", e.txHash)
    }

    @Test
    fun outgoingTransfer_isTransferOut() {
        val e = WalletEventDecoder.evmTokenTransfer(transfer(to = OTHER, from = WALLET, raw = "2000000"))
        assertEquals(EventKind.TRANSFER_OUT, e?.kind)
    }

    @Test
    fun transferNeverBecomesBuy() {
        // حتی ورودی بزرگ هم BUY نمی‌شود — فقط TRANSFER
        val e = WalletEventDecoder.evmTokenTransfer(transfer(to = WALLET, from = OTHER, raw = "999999999999"))
        assertEquals(EventKind.TRANSFER_IN, e?.kind)
    }

    @Test
    fun unrelatedWallet_returnsNull() {
        val e = WalletEventDecoder.evmTokenTransfer(transfer(to = OTHER, from = OTHER, raw = "100"))
        assertNull(e)
    }

    @Test
    fun invalidRawValue_returnsNull_notZero() {
        val e = WalletEventDecoder.evmTokenTransfer(transfer(to = WALLET, from = OTHER, raw = "not_a_number"))
        assertNull(e)
    }

    @Test
    fun invalidWalletAddress_returnsNull() {
        val bad = transfer(to = WALLET, from = OTHER, raw = "100").copy(wallet = "0x1234")
        assertNull(WalletEventDecoder.evmTokenTransfer(bad))
    }
}
