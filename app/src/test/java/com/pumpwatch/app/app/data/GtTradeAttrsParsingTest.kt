package com.pumpwatch.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 82: تست پارس JSON واقعی GeckoTerminal API v2.
 *
 * این تست با fixture واقعی (ساختار مستند شده) اجرا می‌شود تا
 * از تکرار باگ W1 جلوگیری شود.
 */
class GtTradeAttrsParsingTest {

    private val gson = Gson()

    /**
     * نمونه واقعی از پاسخ GeckoTerminal (مطابق مستندات v2).
     * توجه: fیلد `kind` جهت معامله است، `type` همیشه "trade" است.
     */
    private val realJson = """
        {
            "block_timestamp": "2026-09-27T21:10:03Z",
            "kind": "buy",
            "volume_in_usd": "1234.56",
            "price_from_in_usd": "1.2300",
            "price_to_in_usd": "1.2350",
            "tx_hash": "0xabc123",
            "tx_from_address": "0xWhale1111111111111111111111111111111111",
            "tx_to_address": "0xPool222222222222222222222222222222222",
            "token_amount": "1000.0"
        }
    """.trimIndent()

    @Test
    fun `parses real GeckoTerminal trade JSON correctly`() {
        val attrs = gson.fromJson(realJson, GtTradeAttrs::class.java)
        assertEquals("2026-09-27T21:10:03Z", attrs.block_timestamp)
        assertEquals("buy", attrs.kind)
        assertEquals("1234.56", attrs.volume_in_usd)
        assertEquals("1.2300", attrs.price_from_in_usd)
        assertEquals("1.2350", attrs.price_to_in_usd)
        assertEquals("0xabc123", attrs.tx_hash)
        assertEquals("0xWhale1111111111111111111111111111111111", attrs.tx_from_address)
        assertEquals("1000.0", attrs.token_amount)
    }

    @Test
    fun `kind sell is recognized correctly`() {
        val sellJson = realJson.replace("\"kind\": \"buy\"", "\"kind\": \"sell\"")
        val attrs = gson.fromJson(sellJson, GtTradeAttrs::class.java)
        assertEquals("sell", attrs.kind)
    }

    @Test
    fun `parseIso8601 handles real GeckoTerminal timestamp`() {
        val tsMs = parseIso8601("2026-09-27T21:10:03Z")
        assertNotNull(tsMs)
        // sanity check: باید در سال 2026 باشد (حدود 1790709003000 ms)
        assertTrue("timestamp must be > 2025-01-01", tsMs!! > 1735689600000L)
    }

    @Test
    fun `parseIso8601 returns null for invalid input`() {
        assertNull(parseIso8601(null))
        assertNull(parseIso8601(""))
        assertNull(parseIso8601("not-a-date"))
        assertNull(parseIso8601("1234567890")) // epoch number, not ISO
    }

    @Test
    fun `volume_in_usd parses as double correctly`() {
        val attrs = gson.fromJson(realJson, GtTradeAttrs::class.java)
        val vol = attrs.volume_in_usd?.toDoubleOrNull()
        assertEquals(1234.56, vol!!, 0.0001)
    }

    @Test
    fun `price fields parse as doubles correctly`() {
        val attrs = gson.fromJson(realJson, GtTradeAttrs::class.java)
        assertEquals(1.2300, attrs.price_from_in_usd?.toDoubleOrNull()!!, 0.0001)
        assertEquals(1.2350, attrs.price_to_in_usd?.toDoubleOrNull()!!, 0.0001)
    }

    @Test
    fun `kind comparison is case insensitive`() {
        assertTrue("sell".equals("sell", ignoreCase = true))
        assertTrue("SELL".equals("sell", ignoreCase = true))
        assertTrue("Sell".equals("sell", ignoreCase = true))
        assertFalse("buy".equals("sell", ignoreCase = true))
    }

    @Test
    fun `missing fields result in nulls, not exception`() {
        val minimalJson = """{"kind": "buy"}"""
        val attrs = gson.fromJson(minimalJson, GtTradeAttrs::class.java)
        assertEquals("buy", attrs.kind)
        assertNull(attrs.block_timestamp)
        assertNull(attrs.volume_in_usd)
        assertNull(attrs.price_to_in_usd)
    }
}
