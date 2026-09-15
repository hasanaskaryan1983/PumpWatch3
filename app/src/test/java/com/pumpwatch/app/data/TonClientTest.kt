package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد برای تبدیل مقادیر TON/Jetton (Sprint 8 / T1).
 */
class TonClientTest {

    @Test
    fun `nanoton to TON with 9 decimals`() {
        assertEquals(1.5, tonAmount("1500000000", 9)!!, 1e-12)
    }

    @Test
    fun `jetton with 6 decimals`() {
        assertEquals(0.25, tonAmount("250000", 6)!!, 1e-12)
    }

    @Test
    fun `zero balance stays zero`() {
        assertEquals(0.0, tonAmount("0", 9)!!, 1e-12)
    }

    @Test
    fun `null raw returns null`() {
        assertNull(tonAmount(null, 9))
    }

    @Test
    fun `garbage raw returns null`() {
        assertNull(tonAmount("abc", 9))
    }

    @Test
    fun `decimals clamped to 18 so absurd values cannot explode`() {
        val v = tonAmount("10", 30)!!
        assertTrue(v > 0.0 && v < 1e-16)
    }
}
