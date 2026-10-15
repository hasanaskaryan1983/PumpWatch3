package com.pumpwatch.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🚀 Commit 75: تست‌های E2E برای NewsClient با MockWebServer.
 *
 * سناریوها:
 *   1. پاسخ سالم → parse درست (Data → list)
 *   2. JSON ناقص → فیلدهای موجود پر، بقیه null
 *   3. HTTP 500 → exception (caller باید catch کند — wrapper ندارد)
 *   4. تغییر baseUrl → cache invalidate → درخواست به server جدید
 */
class NewsClientE2ETest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        NewsClient.baseUrl = server.url("/").toString()
    }

    @After
    fun teardown() {
        try { server.shutdown() } catch (_: Exception) { }
        NewsClient.baseUrl = NewsClient.DEFAULT_BASE_URL
    }

    @Test
    fun `news healthy response parses items`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"Data":[
                        {"id":"1","title":"BTC up","url":"https://x","published_on":1700000000,"source":"CoinDesk"}
                    ]}"""
                )
        )
        val resp = NewsClient.api.news(null)
        assertNotNull(resp.data)
        assertEquals(1, resp.data!!.size)
        assertEquals("BTC up", resp.data!![0].title)
        assertEquals(1700000000L, resp.data!![0].publishedOn)
        // path شامل query string هم می‌شود (lang=EN پیش‌فرض)
        assertTrue(server.takeRequest().path!!.startsWith("/data/v2/news/"))
    }

    @Test
    fun `news partial JSON leaves missing fields null`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"Data":[{"id":"2","title":"Only title"}]}""")
        )
        val resp = NewsClient.api.news(null)
        assertEquals(1, resp.data!!.size)
        assertEquals("Only title", resp.data!![0].title)
        assertNull(resp.data!![0].url)
        assertNull(resp.data!![0].publishedOn)
        assertNull(resp.data!![0].source)
    }

    @Test
    fun `news server 500 throws - caller must catch`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val threw = try {
            NewsClient.api.news(null)
            false
        } catch (_: Exception) { true }
        assertTrue("Retrofit روی 500 exception می‌دهد؛ UI باید catch کند", threw)
    }

    @Test
    fun `baseUrl change invalidates cached client`() = runBlocking {
        // درخواست اول به server فعلی
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"Data":[]}"""))
        NewsClient.api.news(null)
        assertEquals(1, server.requestCount)

        // server دوم — تغییر baseUrl باید cache را باطل کند
        val server2 = MockWebServer()
        server2.start()
        try {
            NewsClient.baseUrl = server2.url("/").toString()
            server2.enqueue(MockResponse().setResponseCode(200).setBody("""{"Data":[]}"""))
            NewsClient.api.news(null)
            assertEquals("درخواست دوم باید به server جدید برود", 1, server2.requestCount)
            assertEquals("server اول نباید درخواست دوم را بگیرد", 1, server.requestCount)
        } finally {
            try { server2.shutdown() } catch (_: Exception) { }
        }
    }
}
