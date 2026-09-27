package com.pumpwatch.app.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 🚀 Commit 71: تست‌های منطق `isTransient` (بدون نیاز به Android/Context).
 *
 * هدف: اثبات تفکیک صحیح transient vs terminal errors.
 */
class WorkerErrorClassificationTest {

    @Test
    fun `SocketTimeoutException is transient`() {
        assertTrue(MonitorWorker.isTransient(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `UnknownHostException is transient`() {
        assertTrue(MonitorWorker.isTransient(UnknownHostException("Unable to resolve host")))
    }

    @Test
    fun `IOException is transient`() {
        assertTrue(MonitorWorker.isTransient(IOException("Network unreachable")))
    }

    @Test
    fun `message containing 429 is transient`() {
        assertTrue(MonitorWorker.isTransient(RuntimeException("HTTP 429 Too Many Requests")))
    }

    @Test
    fun `message containing 503 is transient`() {
        assertTrue(MonitorWorker.isTransient(RuntimeException("HTTP 503 Service Unavailable")))
    }

    @Test
    fun `message containing timeout is transient`() {
        assertTrue(MonitorWorker.isTransient(RuntimeException("Connection timeout")))
    }

    @Test
    fun `NullPointerException is terminal`() {
        assertFalse(MonitorWorker.isTransient(NullPointerException("null")))
    }

    @Test
    fun `IllegalArgumentException is terminal`() {
        assertFalse(MonitorWorker.isTransient(IllegalArgumentException("Invalid input")))
    }

    @Test
    fun `ClassCastException is terminal`() {
        assertFalse(MonitorWorker.isTransient(ClassCastException("Cannot cast")))
    }

    @Test
    fun `generic RuntimeException without transient keywords is terminal`() {
        assertFalse(MonitorWorker.isTransient(RuntimeException("Something went wrong")))
    }

    @Test
    fun `TraderMonitorWorker has same isTransient logic`() {
        assertTrue(TraderMonitorWorker.isTransient(SocketTimeoutException("timeout")))
        assertFalse(TraderMonitorWorker.isTransient(NullPointerException("null")))
    }
}
