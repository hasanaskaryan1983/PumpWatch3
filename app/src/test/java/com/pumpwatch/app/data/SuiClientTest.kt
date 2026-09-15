package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تست‌های واحد برای تبدیل مقادیر SUI (Sprint 8 / S1).
 */
class SuiClientTest {

    @Test
    fun `myst to SUI with 9 decimals`() {
        assertEquals(1.0, suiAmount("1000000000")!!, 1e-12)
    }

    @Test
    fun `fractional amount keeps precision`() {
        assertEquals(123.456789012, suiAmount("123456789012")!!, 1e-9)
    }

    @Test
    fun `zero balance stays zero`() {
        assertEquals(0.0, suiAmount("0")!!, 1e-12)
    }

    @Test
    fun `null raw returns null`() {
        assertNull(suiAmount(null))
    }

    @Test
    fun `garbage raw returns null`() {
        assertNull(suiAmount("abc"))
    }
}
