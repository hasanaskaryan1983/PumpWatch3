package com.pumpwatch.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DexScreenerFetchTokenCacheTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
        DexScreenerClient.baseUrl = DexScreenerClient.DEFAULT_BASE_URL
    }

    @Test
    fun testFetchTokenCachesResponseWithinTtl() = runBlocking {
        server.start()
        DexScreenerClient.baseUrl = server.url("/").toString()

        val body = """{"schemaVersion":"1.0.0","pairs":[{"chainId":"solana","baseToken":{"address":"TestAddr1","name":"Test","symbol":"TST"},"quoteToken":{"address":"Q","name":"Quote","symbol":"USDC"},"priceUsd":"1.5","liquidity":{"usd":10000.0}}]}"""
        server.enqueue(MockResponse().setBody(body))

        val first = DexScreenerClient.fetchToken("TestAddr1")
        val second = DexScreenerClient.fetchToken("TestAddr1")

        assertEquals(1, first.pairs!!.size)
        assertEquals(1, second.pairs!!.size)
        // کش باید جلوی درخواست شبکهٔ دوم را بگیرد
        assertEquals(1, server.requestCount)
    }
}
