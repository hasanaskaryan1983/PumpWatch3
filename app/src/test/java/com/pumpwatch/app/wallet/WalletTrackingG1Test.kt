package com.pumpwatch.app.wallet

import com.pumpwatch.app.wallet.domain.SolanaTxSummary
import com.pumpwatch.app.wallet.domain.SolanaTokenBalanceChange
import com.pumpwatch.app.wallet.domain.SolanaEventDecoder
import com.pumpwatch.app.wallet.domain.EventKind
import com.pumpwatch.app.wallet.domain.Conidence
import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal

/**
 * 🚀 G1: تست رهگیری واقعی ولت
 * شرط: تراکنش خودِ ولت → هشدار؛ تراکنش غریبه → بدون هشدار
 */
class WalletTrackingG1Test {
    
    private val wallet = "HN7cABqLq46Es1jh92dQQisAq66XSm4ZY9Hq5f26sUXn"
    private val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    private val solMint = SolanaEventDecoder.WSOL_MINT
    
    @Test
    fun `G1 - own wallet BUY triggers alert`() {
        // ولت USDC داده، توکن گرفته (BUY)
        val tx = SolanaTxSummary(
            wallet = wallet,
            timestampMs = System.currentTimeMillis(),
            txHash = "5j7s6Nzp6vAnfC6DZr8HqXw1Y2F3K4M5L6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3",
            tokenChanges = listOf(
                SolanaTokenBalanceChange(wallet, usdcMint, BigDecimal("-1000.0")),  // USDC داد
                SolanaTokenBalanceChange(wallet, "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnV7YaB1pump", BigDecimal("50000.0"))  // توکن گرفت
            ),
            programIds = listOf(SolanaEventDecoder.DEX_PROGRAMS.first())  // Raydium
        )
        
        val event = SolanaEventDecoder.decode(tx)
        assertNotNull("Event should not be null for own wallet transaction", event)
        assertEquals("Should be BUY", EventKind.BUY, event?.kind)
        assertEquals("Should be HIGH confidence", Confidence.HIGH, event?.confidence)
    }
    
    @Test
    fun `G1 - own wallet SELL triggers alert`() {
        // ولت توکن داده، SOL گرفته (SELL)
        val tx = SolanaTxSummary(
            wallet = wallet,
            timestampMs = System.currentTimeMillis(),
            txHash = "4j7s6Nzp6vAnfC6DZr8HqXw1Y2F3K4M5L6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3",
            tokenChanges = listOf(
                SolanaTokenBalanceChange(wallet, "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnV7YaB1pump", BigDecimal("-50000.0")),  // توکن داد
                SolanaTokenBalanceChange(wallet, solMint, BigDecimal("1.5"))  // SOL گرفت
            ),
            nativeSolDeltaLamports = 1_500_000_000L,
            programIds = listOf(SolanaEventDecoder.DEX_PROGRAMS.first())
        )
        
        val event = SolanaEventDecoder.decode(tx)
        assertNotNull("Event should not be null for own wallet transaction", event)
        assertEquals("Should be SELL", EventKind.SELL, event?.kind)
        assertEquals("Should be HIGH confidence", Confidence.HIGH, event?.confidence)
    }
    
    @Test
    fun `G1 - stranger transaction no alert`() {
        // تراکنش غریبه (owner != wallet) → decode null برمی‌گرداند
        val stranger = "SomeoneElseAddress12345678901234567890123456789012345"
        val tx = SolanaTxSummary(
            wallet = wallet,  // ولت ما
            timestampMs = System.currentTimeMillis(),
            txHash = "3j7s6Nzp6vAnfC6DZr8HqXw1Y2F3K4M5L6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3",
            tokenChanges = listOf(
                // ولی تغییرات مال غریبه است
                SolanaTokenBalanceChange(stranger, usdcMint, BigDecimal("-1000.0")),
                SolanaTokenBalanceChange(stranger, "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnV7YaB1pump", BigDecimal("50000.0"))
            ),
            programIds = listOf(SolanaEventDecoder.DEX_PROGRAMS.first())
        )
        
        val event = SolanaEventDecoder.decode(tx)
        assertNull("Stranger transaction should not trigger alert", event)
    }
    
    @Test
    fun `G1 - only SOL transfer no alert`() {
        // فقط SOL جابه‌جا شده (بدون توکن) → رویداد توکنی نیست
        val tx = SolanaTxSummary(
            wallet = wallet,
            timestampMs = System.currentTimeMillis(),
            txHash = "2j7s6Nzp6vAnfC6DZr8HqXw1Y2F3K4M5L6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3",
            tokenChanges = listOf(
                SolanaTokenBalanceChange(wallet, solMint, BigDecimal("-1.0"))
            ),
            nativeSolDeltaLamports = -1_000_000_000L,
            programIds = emptyList()
        )
        
        val event = SolanaEventDecoder.decode(tx)
        assertNull("SOL-only transfer should not trigger alert", event)
    }
}
