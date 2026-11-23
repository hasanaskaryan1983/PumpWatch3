package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 I10: رگرسیون پایهٔ زمان در DataEnvelopeFactory.
 *
 * باگ قبلی: کارخانه receivedAtMs را با currentTimeMillis می‌گذاشت، در حالی که
 * ageSec() با elapsedRealtime کم می‌کند → سن منفیِ عظیم → پاکت هرگز کهنه نمی‌شد.
 * این تست خودِ کارخانهٔ تولیدی را صدا می‌زند و مهر مونوتونیک را تزریق می‌کند.
 */
class DataEnvelopeFactoryClockTest {

    private val elapsed = 1_000_000L
    private val wall    = 1_700_000_000_000L

    private fun assertMonotonic(env: DataEnvelope<*>) {
        assertEquals(elapsed, env.receivedAtMs)
        assertTrue("receivedAtMs باید مونوتونیک باشد نه epoch", env.receivedAtMs < 1_000_000_000_000L)
    }

    @Test
    fun successUsesMonotonicReceivedAt() {
        val env = DataEnvelopeFactory.success("x", "test", wall, elapsed)
        assertMonotonic(env)
        assertEquals(wall, env.observedAtMs)
        assertEquals(ServedFrom.NETWORK, env.servedFrom)
    }

    @Test
    fun memoryCacheUsesMonotonicReceivedAt() {
        val env = DataEnvelopeFactory.fromMemoryCache("x", "test", wall, elapsed)
        assertMonotonic(env)
        assertEquals(ServedFrom.MEM_CACHE, env.servedFrom)
    }

    @Test
    fun diskCacheUsesMonotonicReceivedAt() {
        val env = DataEnvelopeFactory.fromDiskCache("x", "test", wall, elapsed)
        assertMonotonic(env)
        assertEquals(ServedFrom.DISK_CACHE, env.servedFrom)
    }

    @Test
    fun emptyValidUsesMonotonicReceivedAt() {
        val env = DataEnvelopeFactory.emptyValid<String>("test", elapsed)
        assertMonotonic(env)
        assertTrue(env.data == null)
    }

    @Test
    fun failureUsesMonotonicReceivedAt() {
        val env = DataEnvelopeFactory.failure<String>("test", wall, elapsed)
        assertMonotonic(env)
    }
}
