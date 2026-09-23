package com.pumpwatch.app.wallet.gateway

import com.pumpwatch.app.data.BlockscoutApi
import com.pumpwatch.app.data.GeckoPriceApi
import com.pumpwatch.app.data.GatewayProviders
import com.pumpwatch.app.data.GtTokenAttrs
import com.pumpwatch.app.data.GtTokenData
import com.pumpwatch.app.data.GtTokenInfo
import com.pumpwatch.app.data.GtTrade
import com.pumpwatch.app.data.GtTrades
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 53: تست JVM برای عبور منابع از Gateway — بدون شبکه.
 */
class GatewayProvidersTest {

    private var now = 5_000_000L
    private val gw = ProviderGateway(clock = { now }, sleep = { }, sleepSuspend = { })

    private class FakeGecko(private val failTimes: Int) : GeckoPriceApi {
        var calls = 0
        override suspend fun tokenInfo(network: String, address: String): GtTokenInfo {
            calls++
            if (calls <= failTimes) throw ProviderException(ProviderError.RateLimited)
            return GtTokenInfo(GtTokenData(GtTokenAttrs("Test", "TST", "0.5")))
        }
        override suspend fun poolTrades(network: String, address: String, before: Long?): GtTrades =
            GtTrades(emptyList<GtTrade>())
    }

    @Test
    fun tokenInfo_retriesThenCaches() = runBlocking {
        val fake = FakeGecko(failTimes = 1)
        val p = GatewayProviders(gecko = fake, gateway = gw)
        val r1 = p.geckoTokenInfo("solana", "addr1")
        assertTrue(r1.isSuccess)
        assertEquals(2, fake.calls)          // ۱ شکست + ۱ موفق (retry)
        val r2 = p.geckoTokenInfo("solana", "addr1")
        assertTrue(r2.fromCache)
        assertEquals(2, fake.calls)          // تماس دوم از کش
    }

    @Test
    fun breakerOpens_onRepeatedSourceFailures() = runBlocking {
        val fake = FakeGecko(failTimes = 100)
        val p = GatewayProviders(gecko = fake, gateway = gw)
        repeat(3) { p.geckoTokenInfo("solana", "fail$it") }   // کلید متفاوت → کش نیست
        assertEquals("open", gw.breakerState("geckoterminal"))
        val blocked = p.geckoTokenInfo("solana", "blocked")
        assertEquals(ProviderError.CircuitOpen, blocked.error)
    }
}
