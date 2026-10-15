package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.security.cert.CertificateException

/**
 * 🚀 Commit 77: تست‌های pure برای `NetErr.classify` (بدون نیاز به Android).
 *
 * هر exception type باید به نوع درست نگاشت شود.
 */
class NetErrClassifyTest {

    // ============ SslError ============

    @Test
    fun `SSLException classifies as SslError`() {
        assertEquals(NetError.SslError, NetErr.classify(SSLException("handshake failed")))
    }

    @Test
    fun `CertificateException classifies as SslError`() {
        assertEquals(NetError.SslError, NetErr.classify(CertificateException("expired")))
    }

    @Test
    fun `IOException with ssl in message classifies as SslError`() {
        assertEquals(NetError.SslError, NetErr.classify(IOException("SSL handshake aborted")))
    }

    @Test
    fun `IOException with certificate in message classifies as SslError`() {
        assertEquals(NetError.SslError, NetErr.classify(IOException("certificate not trusted")))
    }

    // ============ NetworkError ============

    @Test
    fun `SocketTimeoutException classifies as NetworkError`() {
        assertEquals(NetError.NetworkError, NetErr.classify(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `UnknownHostException classifies as NetworkError`() {
        assertEquals(NetError.NetworkError, NetErr.classify(UnknownHostException("Unable to resolve host")))
    }

    @Test
    fun `generic IOException classifies as NetworkError`() {
        assertEquals(NetError.NetworkError, NetErr.classify(IOException("Network unreachable")))
    }

    @Test
    fun `HttpException 5xx classifies as NetworkError`() {
        val ex = makeHttpException(500)
        assertEquals(NetError.NetworkError, NetErr.classify(ex))
    }

    @Test
    fun `HttpException 503 classifies as NetworkError`() {
        assertEquals(NetError.NetworkError, NetErr.classify(makeHttpException(503)))
    }

    // ============ RateLimited ============

    @Test
    fun `HttpException 429 classifies as RateLimited`() {
        assertEquals(NetError.RateLimited, NetErr.classify(makeHttpException(429)))
    }

    @Test
    fun `HttpException 418 classifies as RateLimited`() {
        assertEquals(NetError.RateLimited, NetErr.classify(makeHttpException(418)))
    }

    @Test
    fun `IOException with 429 in message classifies as RateLimited`() {
        assertEquals(NetError.RateLimited, NetErr.classify(IOException("HTTP 429 Too Many Requests")))
    }

    @Test
    fun `IOException with rate in message classifies as RateLimited`() {
        assertEquals(NetError.RateLimited, NetErr.classify(IOException("rate limit exceeded")))
    }

    @Test
    fun `RateLimitedException classifies as RateLimited`() {
        assertEquals(NetError.RateLimited, NetErr.classify(RateLimitedException("rate limited")))
    }

    // ============ InvalidPayload ============

    @Test
    fun `JsonSyntaxException classifies as InvalidPayload`() {
        assertEquals(NetError.InvalidPayload, NetErr.classify(com.google.gson.JsonSyntaxException("bad json")))
    }

    @Test
    fun `HttpException 400 classifies as InvalidPayload`() {
        assertEquals(NetError.InvalidPayload, NetErr.classify(makeHttpException(400)))
    }

    @Test
    fun `HttpException 404 classifies as InvalidPayload`() {
        assertEquals(NetError.InvalidPayload, NetErr.classify(makeHttpException(404)))
    }

    @Test
    fun `NoSuchElementException classifies as InvalidPayload`() {
        assertEquals(NetError.InvalidPayload, NetErr.classify(NoSuchElementException()))
    }

    @Test
    fun `IndexOutOfBoundsException classifies as InvalidPayload`() {
        assertEquals(NetError.InvalidPayload, NetErr.classify(IndexOutOfBoundsException()))
    }

    // ============ Unknown ============

    @Test
    fun `null throwable classifies as Unknown`() {
        assertEquals(NetError.Unknown, NetErr.classify(null))
    }

    @Test
    fun `generic RuntimeException classifies as Unknown`() {
        assertEquals(NetError.Unknown, NetErr.classify(RuntimeException("something")))
    }

    // ============ Helpers ============

    private fun makeHttpException(code: Int): HttpException {
        val response = Response.error<Any>(code, okhttp3.ResponseBody.create(null, ""))
        return HttpException(response)
    }
}
