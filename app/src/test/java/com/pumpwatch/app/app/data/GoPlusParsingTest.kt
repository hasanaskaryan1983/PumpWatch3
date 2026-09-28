package com.pumpwatch.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 85 (M1 + M2): تست پارس پاسخ‌های واقعی GoPlus API.
 *
 * این تست‌ها باگ‌های M1 و M2 را پوشش می‌دهند:
 *   - M1: lp_holders باید به‌صورت List پارس شود
 *   - M1: end_time باید ISO و epoch را تحمل کند
 *   - M2: SolanaTokenSecurity فیلدهای متفاوت از EVM دارد
 */
class GoPlusParsingTest {

    private val gson = Gson()

    @Test
    fun `M1 - lp_holders parses as List not Map`() {
        // این JSON باگ قبلی را نشان می‌دهد: اگر lp_holders Map تعریف شود، پارس می‌شکند
        val json = """
            {
                "is_honeypot": "0",
                "lp_holders": [
                    {
                        "address": "0xDead",
                        "percent": 0.5,
                        "is_locked": "1",
                        "locked_detail": [
                            {"amount": "100", "end_time": "2026-12-31 23:59:59"}
                        ]
                    }
                ],
                "holders": []
            }
        """.trimIndent()

        val security = gson.fromJson(json, GoPlusTokenSecurity::class.java)
        assertNotNull(security.lp_holders)
        assertEquals(1, security.lp_holders!!.size)
        assertEquals("0xDead", security.lp_holders!![0].address)
        assertEquals("1", security.lp_holders!![0].is_locked)
    }

    @Test
    fun `M1 - parseLockEndTime handles ISO format`() {
        val iso = "2026-12-31 23:59:59"
        val result = parseLockEndTime(iso)
        assertNotNull(result)
        // sanity check: باید بعد از ۲۰۲۶-۰۱-۰۱ باشد
        assertTrue("ISO timestamp must be > 2026-01-01", result!! > 1767225600000L)
    }

    @Test
    fun `M1 - parseLockEndTime handles ISO with T separator`() {
        val iso = "2026-12-31T23:59:59Z"
        val result = parseLockEndTime(iso)
        assertNotNull(result)
        assertTrue(result!! > 1767225600000L)
    }

    @Test
    fun `M1 - parseLockEndTime handles epoch seconds`() {
        val epoch = "1798761599"  // 2026-12-31 ~23:59:59 UTC
        val result = parseLockEndTime(epoch)
        assertNotNull(result)
        // باید ~1798761599000 باشد
        assertEquals(1798761599000L, result)
    }

    @Test
    fun `M1 - parseLockEndTime handles epoch milliseconds`() {
        val epochMs = "1798761599000"
        val result = parseLockEndTime(epochMs)
        assertNotNull(result)
        assertEquals(1798761599000L, result)
    }

    @Test
    fun `M1 - parseLockEndTime handles date only`() {
        val date = "2026-12-31"
        val result = parseLockEndTime(date)
        assertNotNull(result)
        assertTrue(result!! > 1767225600000L)
    }

    @Test
    fun `M1 - parseLockEndTime returns null for invalid input`() {
        assertNull(parseLockEndTime(null))
        assertNull(parseLockEndTime(""))
        assertNull(parseLockEndTime("not-a-date"))
    }

    @Test
    fun `M2 - SolanaTokenSecurity parses with Solana-specific fields`() {
        val json = """
            {
                "total_supply": "1000000",
                "holder_count": "1000",
                "creator_address": "CreatorAddress111",
                "creator_percent": 0.05,
                "mintable": "1",
                "freezable": "0",
                "closable": "0",
                "balance_mutable_authority": "0",
                "transfer_fee": "5%",
                "non_transferable": "0",
                "trusted_token": "1",
                "top_holders": [
                    {"address": "Top1", "percent": 0.15}
                ]
            }
        """.trimIndent()

        val sol = gson.fromJson(json, SolanaTokenSecurity::class.java)
        assertEquals("1000000", sol.total_supply)
        assertEquals("1000", sol.holder_count)
        assertEquals("1", sol.mintable)
        assertEquals("0", sol.freezable)
        assertEquals("5%", sol.transfer_fee)
        assertEquals(1, sol.top_holders?.size)
        assertEquals(0.15, sol.top_holders!![0].percent!!, 0.001)
    }

    @Test
    fun `M2 - anyToBool handles all common formats`() {
        assertTrue(anyToBool("1") == true)
        assertTrue(anyToBool("true") == true)
        assertTrue(anyToBool(true) == true)
        assertTrue(anyToBool(1) == true)

        assertFalse(anyToBool("0") == true)
        assertFalse(anyToBool("false") == true)
        assertFalse(anyToBool(false) == true)
        assertFalse(anyToBool(0) == true)
        assertFalse(anyToBool("") == true)

        assertNull(anyToBool(null))
        assertNull(anyToBool("maybe"))
    }

    @Test
    fun `M2 - SolanaTokenSecurity with boolean fields (not string)`() {
        val json = """
            {
                "mintable": true,
                "freezable": false,
                "trusted_token": true
            }
        """.trimIndent()

        val sol = gson.fromJson(json, SolanaTokenSecurity::class.java)
        assertEquals(true, sol.mintable)
        assertEquals(false, sol.freezable)
        assertEquals(true, sol.trusted_token)

        // anyToBool باید هر دو را درست تشخیص دهد
        assertTrue(anyToBool(sol.mintable) == true)
        assertFalse(anyToBool(sol.freezable) == true)
    }
}
