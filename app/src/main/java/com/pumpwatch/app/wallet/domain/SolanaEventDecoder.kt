package com.pumpwatch.app.wallet.domain

import java.math.BigDecimal

/**
 * 🚀 Commit 50 (فاز ۴): ورودی خالص decode سولانا (بدون وابستگی به لایهٔ data).
 * delta مثبت = کیف دریافت کرده؛ منفی = داده.
 */
data class SolanaTokenBalanceChange(
    val owner: String,
    val mint: String,
    val delta: BigDecimal
)

data class SolanaTxSummary(
    val wallet: String,
    val timestampMs: Long,
    val txHash: String,
    val tokenChanges: List<SolanaTokenBalanceChange>,
    /** تغییر SOL بومی بر حسب lamports (اختیاری) */
    val nativeSolDeltaLamports: Long? = null,
    /** برنامه‌های صدا زده‌شده در تراکنش (برای تشخیص DEX) */
    val programIds: List<String> = emptyList()
)

/**
 * 🚀 Commit 50: decoder رویدادهای سولانا با انواع صادقانه.
 * WSOL به‌عنوان پایهٔ بومی در نظر گرفته می‌شود.
 * بدون برنامهٔ DEX، الگوی «توکن+SOL» هرگز BUY/SELL نمی‌شود → UNKNOWN.
 */
object SolanaEventDecoder {

    const val WSOL_MINT = "So11111111111111111111111111111111111111112"

    val DEX_PROGRAMS: Set<String> = setOf(
        "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8", // Raydium AMM v4
        "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", // PumpSwap
        "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4", // Jupiter v6
        "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK"  // Raydium CLMM
    )

    fun decode(tx: SolanaTxSummary): WalletEvent? {
        if (!AddressValidator.isValidForChain("solana", tx.wallet)) return null
        val mine = tx.tokenChanges.filter { it.owner == tx.wallet }
        if (mine.isEmpty()) return null

        val tokenLegs = mine.filter { it.mint != WSOL_MINT }
        if (tokenLegs.isEmpty()) return null   // فقط SOL جابه‌جا شده → رویداد توکنی نیست

        val solLeg: BigDecimal? = mine.firstOrNull { it.mint == WSOL_MINT }?.delta
            ?: tx.nativeSolDeltaLamports?.let { BigDecimal(it).movePointLeft(9) }
        val solSign = solLeg?.signum() ?: 0

        val received = tokenLegs.filter { it.delta.signum() > 0 }
        val sent = tokenLegs.filter { it.delta.signum() < 0 }
        val isDex = tx.programIds.any { it in DEX_PROGRAMS }

        val kind: EventKind
        val conf: Confidence
        when {
            received.size == 1 && sent.isEmpty() && solSign == 0 -> {
                kind = EventKind.TRANSFER_IN; conf = Confidence.MEDIUM
            }
            sent.size == 1 && received.isEmpty() && solSign == 0 -> {
                kind = EventKind.TRANSFER_OUT; conf = Confidence.MEDIUM
            }
            received.size == 1 && sent.size == 1 -> {
                kind = EventKind.SWAP; conf = Confidence.MEDIUM
            }
            isDex && received.size == 1 && sent.isEmpty() && solSign < 0 -> {
                kind = EventKind.BUY; conf = Confidence.HIGH
            }
            isDex && sent.size == 1 && received.isEmpty() && solSign > 0 -> {
                kind = EventKind.SELL; conf = Confidence.HIGH
            }
            else -> {
                kind = EventKind.UNKNOWN; conf = Confidence.LOW
            }
        }

        val primary = (received + sent).maxByOrNull { it.delta.abs() } ?: return null
        return WalletEvent(
            id = "${tx.txHash}:${primary.mint}:${kind.name}",
            wallet = tx.wallet,
            asset = AssetId.solana(primary.mint),
            kind = kind,
            amount = primary.delta.abs(),
            quoteAmountUsd = null,          // فاز ۵: ارزش‌گذاری جدا
            executionPriceUsd = null,
            timestampMs = tx.timestampMs,
            txHash = tx.txHash,
            source = "solana-rpc",
            confidence = conf
        )
    }
}
