package com.pumpwatch.app.wallet.domain

import java.math.BigDecimal

/**
 * 🚀 Commit 51: منبع قیمت قابل تزریق — برای تست JVM و برای ProviderGateway آینده.
 */
interface PriceSource {
    fun quoteFor(asset: AssetId, nowMs: Long): PriceQuote?
}

/**
 * 🚀 Commit 51 (فاز ۵): سرویس ارزش‌گذاری با TTL و اطمینان.
 * - quote قدیمی‌تر از TTL → ارزش null + اطمینان LOW (نه عدد جعلی)
 * - نقدینگی زیر آستانه → POOL_DERIVED + اطمینان MEDIUM
 * - بدون quote → UNKNOWN
 */
object ValuationService {

    const val DEFAULT_TTL_MS: Long = 5 * 60 * 1000L
    val LOW_LIQUIDITY_USD: BigDecimal = BigDecimal("10000")

    fun estimate(
        asset: AssetId,
        amount: BigDecimal,
        source: PriceSource,
        nowMs: Long,
        ttlMs: Long = DEFAULT_TTL_MS
    ): ValueEstimate {
        val q = source.quoteFor(asset, nowMs)
        return when {
            q == null ->
                ValueEstimate(asset, amount, null, null, ValuationMethod.UNKNOWN, Confidence.UNKNOWN, nowMs)

            q.priceUsd == null ->
                ValueEstimate(asset, amount, null, q, ValuationMethod.UNKNOWN, Confidence.UNKNOWN, nowMs)

            q.isStale(nowMs, ttlMs) ->
                ValueEstimate(asset, amount, null, q, ValuationMethod.UNKNOWN, Confidence.LOW, nowMs)

            else -> {
                val lowLiq = q.liquidityUsd != null && q.liquidityUsd < LOW_LIQUIDITY_USD
                val method = if (lowLiq) ValuationMethod.POOL_DERIVED else ValuationMethod.DIRECT_QUOTE
                val conf = when {
                    method == ValuationMethod.POOL_DERIVED -> Confidence.MEDIUM
                    q.confidence == Confidence.HIGH -> Confidence.HIGH
                    else -> q.confidence
                }
                ValueEstimate(
                    asset = asset,
                    amount = amount,
                    valueUsd = q.priceUsd.multiply(amount),
                    quote = q,
                    method = method,
                    confidence = conf,
                    computedAtMs = nowMs
                )
            }
        }
    }

    /**
     * جمع ارزش سبد: فقط اقلام با ارزش معلوم جمع می‌شوند؛
     * اقلام نامعلوم و قدیمی جدا شمرده می‌شوند تا UI صادق بماند.
     */
    fun portfolioTotal(
        estimates: List<ValueEstimate>,
        nowMs: Long,
        ttlMs: Long = DEFAULT_TTL_MS
    ): PortfolioTotal {
        var known = BigDecimal.ZERO
        var unknown = 0
        var stale = 0
        for (e in estimates) {
            when {
                e.valueUsd != null && e.confidence != Confidence.UNKNOWN -> known = known.add(e.valueUsd)
                e.quote != null && e.quote.isStale(nowMs, ttlMs) -> stale++
                else -> unknown++
            }
        }
        return PortfolioTotal(known, unknown, stale, nowMs)
    }
}
