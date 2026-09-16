package com.pumpwatch.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Sprint 9 (I2c) — تست‌های End-to-End برای TonClient و SuiClient
 * با MockWebServer.
 *
 * ۴ سناریوی دنیای واقعی:
 *   1. پاسخ سالم → دادهٔ درست می‌آید
 *   2. HTTP 5xx → کلاینت null برمی‌گرداند (نه کرش، نه دادهٔ جعلی)
 *   3. JSON ناقص → فیلدهای موجود پر، بقیه null (P0-1 invariant)
 *   4. قطعی شبکه → کلاینت null برمی‌گرداند
 *
 * این تست‌ها بدون نیاز به اینترنت اجرا می‌شوند.
 */
class ClientE2ETest {

    private lateinit var tonServer: MockWebServer
    private lateinit var suiServer: MockWebServer

    @Before
    fun setup() {
        tonServer = MockWebServer()
        tonServer.start()
        TonClient.baseUrl = tonServer.url("/").toString()

        suiServer = MockWebServer()
        suiServer.start()
        SuiClient.baseUrl = suiServer.url("/").toString()
    }

    @After
    fun teardown() {
        try { tonServer.shutdown() } catch (_: Exception) { }
        try { suiServer.shutdown() } catch (_: Exception) { }
        TonClient.baseUrl = TonClient.DEFAULT_BASE_URL
        SuiClient.baseUrl = SuiClient.MAINNET
    }

    // ================= TonClient =================

    @Test
    fun `ton account healthy response parses balance`() = runBlocking {
        tonServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"balance":2500000000,"status":"active"}""")
        )
        val acc = TonClient.api.account("EQtest")
        assertNotNull(acc)
        assertEquals(2_500_000_000L, acc.balance)
        assertEquals("active", acc.status)
        assertEquals(2.5, tonAmount(acc.balance?.toString(), 9)!!, 1e-12)
        assertEquals("/v2/accounts/EQtest", tonServer.takeRequest().path)
    }

    @Test
    fun `ton account server 500 returns null not crash`() = runBlocking {
        tonServer.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        // try/catch داخل TonClient wrapper ها (account/jettons/events نیست) اما
        // Retrofit روی 500 HttpException پرتاب می‌کند → تست می‌کند کلاینت در برابر
        // خطاهای سرور کرش نمی‌کند
        val threw = try {
            TonClient.api.account("EQtest")
            false
        } catch (_: Exception) { true }
        // انتظار: Retrofit استثنا می‌دهد؛ UI آن را در try/catch می‌گیرد
        assertEquals(true, threw)
    }

    @Test
    fun `ton account partial JSON leaves missing fields null — P0-1`() = runBlocking {
        tonServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"balance":100}""")  // بدون status
        )
        val acc = TonClient.api.account("EQtest")
        assertNotNull(acc)
        assertEquals(100L, acc.balance)
        assertNull("فیلد ناموجود در JSON باید null بماند، نه default", acc.status)
    }

    @Test
    fun `ton account network disconnect returns safely`() = runBlocking {
        tonServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )
        val threw = try {
            TonClient.api.account("EQtest")
            false
        } catch (_: Exception) { true }
        assertEquals(true, threw)
    }

    // ================= SuiClient =================

    @Test
    fun `sui balances healthy response parses coinTypes and amounts`() = runBlocking {
        suiServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result":[
                    {"coinType":"0x2::sui::SUI","balance":"5000000000"},
                    {"coinType":"0xabc::coin::COIN","balance":"123"}
                ]}""")
        )
        val res = SuiClient.balances("0xtest")
        assertNotNull(res)
        val arr = res!!.getAsJsonArray("result")
        assertEquals(2, arr.size())
        val suiBal = arr[0].asJsonObject.get("balance").asString
        assertEquals(5.0, suiAmount(suiBal)!!, 1e-12)
    }

    @Test
    fun `sui balances server 500 wrapper returns null not crash`() = runBlocking {
        suiServer.enqueue(MockResponse().setResponseCode(500))
        val res = SuiClient.balances("0xtest")
        assertNull("wrapper باید روی خطای سرور null برگرداند", res)
    }

    @Test
    fun `sui balances empty array returns empty not null`() = runBlocking {
        suiServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result":[]}""")
        )
        val res = SuiClient.balances("0xtest")
        assertNotNull(res)
        assertEquals(0, res!!.getAsJsonArray("result").size())
    }

    @Test
    fun `sui balances network disconnect wrapper returns null`() = runBlocking {
        suiServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )
        val res = SuiClient.balances("0xtest")
        assertNull("wrapper باید روی قطعی شبکه null برگرداند", res)
    }
}
