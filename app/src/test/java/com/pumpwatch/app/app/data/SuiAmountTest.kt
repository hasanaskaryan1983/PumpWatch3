package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🚀 Commit 74: تست تابع pure `suiAmount`.
 *
 * SUI همیشه ۹ اعشار دارد (مثل Ethereum wei ولی با 9 digit).
 * این تابع مستقل از Android/Network کار می‌کند و کاملاً تست‌پذیر است.
 */
class SuiAmountTest {

    @Test
    fun `1 SUI equals 1_000_000_000 mist`() {
        assertEquals(1.0, suiAmount("1000000000")!!, 0.0001)
    }

    @Test
    fun `0_5 SUI equals 500_000_000 mist`() {
        assertEquals(0.5, suiAmount("500000000")!!, 0.0001)
    }

    @Test
    fun `small amounts are converted correctly`() {
        assertEquals(0.000000001, suiAmount("1")!!, 0.0000000001)
        assertEquals(0.000001, suiAmount("1000")!!, 0.0000001)
    }

    @Test
    fun `null input returns null`() {
        assertNull(suiAmount(null))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(suiAmount(""))
    }

    @Test
    fun `non-numeric input returns null`() {
        assertNull(suiAmount("not-a-number"))
        assertNull(suiAmount("abc"))
    }

    @Test
    fun `large amounts are handled correctly`() {
        // 1 میلیارد SUI = 1e18 mist
        assertEquals(1_000_000_000.0, suiAmount("1000000000000000000")!!, 0.0001)
    }

    @Test
    fun `zero returns zero`() {
        assertEquals(0.0, suiAmount("0")!!, 0.0001)
    }

    @Test
    fun `decimal string input returns null`() {
        // ورودی باید integer string باشد (mist همیشه integer است)
        assertNull(suiAmount("1.5"))
    }
}
