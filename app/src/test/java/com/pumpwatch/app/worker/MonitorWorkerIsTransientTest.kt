package com.pumpwatch.app.worker

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 🚀 Commit 124: تست طبقه‌بندی خطای موقتی (transient) از قطعی (terminal).
 *
 * چرا مهم؟
 * - transient → Result.retry() (شبکه/429/5xx)
 * - terminal → Result.failure() (باگ کد؛ retry فقط باتری و quota می‌سوزاند)
 *
 * اگر این طبقه‌بندی بشکند، Worker یا بی‌دلیل retry می‌کند
 * یا خطای شبکه را برای همیشه fail می‌کند.
 */
class MonitorWorkerIsTransientTest {

    // ========== Transient: کلاس‌های شناخته‌شده ==========

    @Test
    fun `SocketTimeoutException is transient`() {
        assertTrue(MonitorWorker.isTransient(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `UnknownHostException is transient`() {
        assertTrue(MonitorWorker.isTransient(UnknownHostException("api.bybit.com")))
    }

    @Test
    fun `IOException is transient`() {
        assertTrue(MonitorWorker.isTransient(IOException("stream closed")))
    }

    @Test
    fun `ConnectException is transient`() {
        assertTrue(MonitorWorker.isTransient(ConnectException("Connection refused")))
    }

    // ========== Transient: الگوهای پیام ==========

    @Test
    fun `message containing 429 is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("HTTP 429 Too Many Requests")))
    }

    @Test
    fun `message containing 503 is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("Service Unavailable 503")))
    }

    @Test
    fun `message containing 502 is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("Bad Gateway 502")))
    }

    @Test
    fun `lowercase timeout in message is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("connect timeout while reading")))
    }

    @Test
    fun `message containing connection is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("connection reset by peer")))
    }

    @Test
    fun `message containing unreachable is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("host unreachable")))
    }

    @Test
    fun `message containing network is transient`() {
        assertTrue(MonitorWorker.isTransient(Exception("network interface down")))
    }

    // ========== Terminal: باگ‌های کد ==========

    @Test
    fun `NullPointerException is terminal`() {
        assertFalse(MonitorWorker.isTransient(NullPointerException("null field")))
    }

    @Test
    fun `IllegalArgumentException is terminal`() {
        assertFalse(MonitorWorker.isTransient(IllegalArgumentException("bad input")))
    }

    @Test
    fun `ClassCastException is terminal`() {
        assertFalse(MonitorWorker.isTransient(ClassCastException("String cannot be Int")))
    }

    @Test
    fun `generic parse error is terminal`() {
        assertFalse(MonitorWorker.isTransient(Exception("parse error at line 3")))
    }

    @Test
    fun `empty exception is terminal`() {
        assertFalse(MonitorWorker.isTransient(Exception()))
    }

    // ========== رفتار مستند: اعداد HTTP در پیام ==========

    @Test
    fun `message containing 500 is transient (documented behavior)`() {
        // طراحی فعلی: هر پیامی که "500" داشته باشد transient است.
        // این یک false-positive شناخته‌شده است ولی رفتار مستند فعلی است.
        assertTrue(MonitorWorker.isTransient(Exception("server returned 500")))
    }
}
