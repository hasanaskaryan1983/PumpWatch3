package com.pumpwatch.app.wallet.domain

import java.math.BigDecimal

/**
 * 🚀 Commit 51 (فاز ۵ برنامهٔ اجرایی): تخمین ارزش با provenance کامل.
 * هر عدد دلاری در UI باید از این نوع بیاید؛ وگرنه نمایش داده نمی‌شود.
 */
data class ValueEstimate(
    val asset: AssetId,
    val amount: BigDecimal,
    val valueUsd: BigDecimal?,
    val quote: PriceQuote?,
    val method: ValuationMethod,
    val confidence: Confidence,
    val computedAtMs: Long
) {
    /** نامعلوم ≠ صفر */
    val isUnknown: Boolean get() = valueUsd == null
}

enum class ValuationMethod {
    /** قیمت مستقیم از منبع قابل اعتماد */
    DIRECT_QUOTE,

    /** از استخر DEX با نقدینگی کم → اطمینان کمتر */
    POOL_DERIVED,

    /** فقط نماد — ممنوع مگر با برچسب هشدار صریح */
    SYMBOL_HEURISTIC,

    /** بدون دادهٔ قابل استناد */
    UNKNOWN
}

/**
 * 🚀 Commit 51: جمع ارزش سبد با تفکیک صادقانهٔ اقلام نامعلوم/قدیمی.
 */
data class PortfolioTotal(
    val knownUsd: BigDecimal,
    val unknownAssets: Int,
    val staleAssets: Int,
    val computedAtMs: Long
)
