package com.pumpwatch.app.wallet.domain

import java.math.BigDecimal

/**
 * 🚀 Commit 47 (فاز ۲): رویداد کیف پول با هویت صحیح دارایی و provenance.
 * هر event باید txHash و source داشته باشد؛ بدون آن‌ها قابل استناد نیست.
 */
data class WalletEvent(
    val id: String,
    val wallet: String,
    val asset: AssetId,
    val kind: EventKind,
    val amount: BigDecimal,
    val quoteAmountUsd: BigDecimal?,
    val executionPriceUsd: BigDecimal?,
    val timestampMs: Long,
    val txHash: String,
    val source: String,
    val confidence: Confidence
)
