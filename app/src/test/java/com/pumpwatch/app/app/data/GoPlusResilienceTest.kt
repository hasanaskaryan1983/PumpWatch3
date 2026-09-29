package com.pumpwatch.app.data

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * 🚀 Commit 89 (A2): تست pure توابع تاب‌آوری GoPlus.
 *
 * پوشش:
 *   - backoff نمایی با سقف
 *   - clamp برای attempt نامعتبر
 *   - طبقه‌بندی خطاها: retryable (429/5xx/network) در برابر terminal (4xx دیگر/bug)
 */
class GoPlusResilienceTest {

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    // ---------- backoff ----------

    @Test
    fun `backoff grows exponentially`() {
        assertEquals(GOPLUS_BASE_BACKOFF_MS, goPlusBackoffMs(1))          // 2s
        assertEquals(GOPLUS_BASE_BACKOFF_MS * 2, goPlusBackoffMs(2))      // 4s
        assertEquals(GOPLUS_BASE_BACKOFF_MS * 4, goPlusBackoffMs(3))      // 8s
    }

    @Test
    fun `backoff capped at max`() {
        assertEquals(GOPLUS_MAX_BACKOFF_MS, goPlusBackoffMs(10))
        assertEquals(GOPLUS_MAX_BACKOFF_MS, goPlusBackoffMs(50))
    }

    @Test
    fun `backoff clamps invalid attempt to base`() {
        assertEquals(GOPLUS_BASE_BACKOFF_MS, goPlusBackoffMs(0))
        assertEquals(GOPLUS_BASE_BACKOFF_MS, goPlusBackoffMs(-3))
    }

    // ---------- طبقه‌بندی خطاها ----------

    @Test
    fun `429 is retryable`() {
        assertTrue(isRetryableError(httpError(429)))
    }

    @Test
    fun `5xx is retryable`() {
        assertTrue(isRetryableError(httpError(500)))
        assertTrue(isRetryableError(httpError(502)))
        assertTrue(isRetryableError(httpError(503)))
        assertTrue(isRetryableError(httpError(504)))
    }

    @Test
    fun `4xx other than 429 is terminal`() {
        assertFalse(isRetryableError(httpError(400)))
        assertFalse(isRetryableError(httpError(404)))
        assertFalse(isRetryableError(httpError(451)))  // ژئوبلاک — retry بیهوده است
    }

    @Test
    fun `io and timeout are retryable`() {
        assertTrue(isRetryableError(IOException("network down")))
        assertTrue(isRetryableError(SocketTimeoutException("timeout")))
    }

    @Test
    fun `programming errors are terminal`() {
        assertFalse(isRetryableError(RuntimeException("bug")))
        assertFalse(isRetryableError(IllegalArgumentException("bad input")))
    }
}
