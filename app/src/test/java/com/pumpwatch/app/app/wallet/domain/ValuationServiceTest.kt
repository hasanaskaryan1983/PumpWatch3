package com.pumpwatch.app.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * 🚀 Commit 51: تست‌های JVM برای ارزش‌گذاری (معیار قبولی فاز ۵).
 */
class ValuationServiceTest {

    private val ASSET = AssetId.solana("MintXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
    private val NOW = 1_700_000_000_000L

    private fun sourceReturning(q: PriceQuote?) = object : PriceSource {
        override fun quoteFor(asset: AssetId, nowMs: Long): PriceQuote? = q
    }

    private fun quote(
        price: String?,
        observedAt: Long = NOW,
        liquidity: String? = null,
        conf: Confidence = Confidence.HIGH
    ) = PriceQuote(
        asset = ASSET,
        priceUsd = price?.let { BigDecimal(it) },
        source = "test",
        observedAtMs = observedAt,
        liquidityUsd = liquidity?.let { BigDecimal(it) },
        confidence = conf
    )

    @Test
    fun freshQuote_givesValue_withDirectMethod() {
        val e = ValuationService.estimate(ASSET, BigDecimal("2"), sourceReturning(quote("1.5")), NOW)
        assertEquals(0, BigDecimal("3.0").compareTo(e.valueUsd!!))
        assertEquals(ValuationMethod.DIRECT_QUOTE, e.method)
        assertEquals(Confidence.HIGH, e.confidence)
    }

    @Test
    fun staleQuote_givesNullValue_andLowConfidence() {
        val old = quote("1.5", observedAt = NOW - 10 * 60 * 1000L) // 10 دقیقه قبل
        val e = ValuationService.estimate(ASSET, BigDecimal("2"), sourceReturning(old), NOW)
        assertNull(e.valueUsd)
        assertEquals(Confidence.LOW, e.confidence)
        assertTrue(e.isUnknown)
    }

    @Test
    fun lowLiquidity_givesPoolDerived_andMediumConfidence() {
        val q = quote("1.5", liquidity = "5000")
        val e = ValuationService.estimate(ASSET, BigDecimal("2"), sourceReturning(q), NOW)
        assertEquals(ValuationMethod.POOL_DERIVED, e.method)
        assertEquals(Confidence.MEDIUM, e.confidence)
    }

    @Test
    fun missingQuote_isUnknown_notZero() {
        val e = ValuationService.estimate(ASSET, BigDecimal("2"), sourceReturning(null), NOW)
        assertNull(e.valueUsd)
        assertEquals(Confidence.UNKNOWN, e.confidence)
        assertTrue(e.isUnknown)
    }

    @Test
    fun portfolioTotal_separatesKnownUnknownStale() {
        val known1 = ValuationService.estimate(ASSET, BigDecimal("1"), sourceReturning(quote("2")), NOW)
        val known2 = ValuationService.estimate(ASSET, BigDecimal("3"), sourceReturning(quote("1")), NOW)
        val stale = ValuationService.estimate(
            ASSET, BigDecimal("5"),
            sourceReturning(quote("1", observedAt = NOW - 9 * 60 * 1000L)), NOW
        )
        val unknown = ValuationService.estimate(ASSET, BigDecimal("7"), sourceReturning(null), NOW)

        val total = ValuationService.portfolioTotal(listOf(known1, known2, stale, unknown), NOW)
        assertEquals(0, BigDecimal("5").compareTo(total.knownUsd)) // 2 + 3
        assertEquals(1, total.staleAssets)
        assertEquals(1, total.unknownAssets)
    }
}
