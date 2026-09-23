package com.pumpwatch.app.wallet.domain

import java.math.BigDecimal

/**
 * 🚀 Commit 47 (فاز ۲): نقل‌قول قیمت با provenance کامل.
 * هر قیمت باید منبع، زمان مشاهده، نقدینگی و اطمینان داشته باشد.
 * BigDecimal برای مالی؛ Double ممنوع.
 */
data class PriceQuote(
    val asset: AssetId,
    val priceUsd: BigDecimal?,
    val source: String,
    val observedAtMs: Long,
    val liquidityUsd: BigDecimal?,
    val confidence: Confidence,
    val pairAddress: String? = null
) {
    /** quote قدیمی‌تر از TTL باید stale اعلام شود، نه قطعی */
    fun isStale(nowMs: Long, ttlMs: Long): Boolean = (nowMs - observedAtMs) > ttlMs

    /** قیمت نامعلوم با صفر یکی نیست */
    val isUnknown: Boolean get() = priceUsd == null
}
