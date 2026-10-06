package com.pumpwatch.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 129: تست‌های pure برای WatchlistMigration.
 *
 * 🎯 استراتژی:
 * - تابع `migrateIfNeeded` نیاز به Context واقعی دارد (SharedPreferences +
 *   WatchlistStore رمزنگاری‌شده) که بدون Robolectric تست نمی‌شود.
 * - در اینجا:
 *   • ثابت‌ها (FLAG_MIGRATED, LEGACY_KEY, LEGACY_PREFS, LEGACY_GROUP_NAME)
 *   • JSON parsing با Gson (همان GSON instance که migration استفاده می‌کند)
 *   • WatchlistEntry data class
 *   • ساختار API
 *
 * تست‌های integration در CI با Robolectric پوشش داده می‌شوند.
 */
class WatchlistMigrationTest {

    private val GSON = Gson()

    // ========== Constants via reflection ==========

    @Test
    fun `FLAG_MIGRATED is watchlist_migrated_v2`() {
        val field = WatchlistMigration::class.java.getDeclaredField("FLAG_MIGRATED")
        field.isAccessible = true
        assertEquals("watchlist_migrated_v2", field.get(null))
    }

    @Test
    fun `LEGACY_KEY is watchlist_v1`() {
        val field = WatchlistMigration::class.java.getDeclaredField("LEGACY_KEY")
        field.isAccessible = true
        assertEquals("watchlist_v1", field.get(null))
    }

    @Test
    fun `LEGACY_PREFS is pumpwatch_prefs`() {
        val field = WatchlistMigration::class.java.getDeclaredField("LEGACY_PREFS")
        field.isAccessible = true
        assertEquals("pumpwatch_prefs", field.get(null))
    }

    @Test
    fun `LEGACY_GROUP_NAME is Persian text`() {
        val field = WatchlistMigration::class.java.getDeclaredField("LEGACY_GROUP_NAME")
        field.isAccessible = true
        assertEquals("واردشده (Legacy)", field.get(null))
    }

    @Test
    fun `GSON instance is not null`() {
        val field = WatchlistMigration::class.java.getDeclaredField("GSON")
        field.isAccessible = true
        assertNotNull(field.get(null))
    }

    @Test
    fun `TAG is WatchlistMigration`() {
        val field = WatchlistMigration::class.java.getDeclaredField("TAG")
        field.isAccessible = true
        assertEquals("WatchlistMigration", field.get(null))
    }

    // ========== JSON parsing compatibility ==========

    @Test
    fun `Gson parses valid WatchlistEntry JSON`() {
        val json = """[{"symbol":"BTC","chain":"ethereum","contract":"0x123","addedAt":1700000000000}]"""
        val type = object : TypeToken<List<WatchlistEntry>>() {}.type
        val entries: List<WatchlistEntry>? = GSON.fromJson(json, type)
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("BTC", entries[0].symbol)
        assertEquals("ethereum", entries[0].chain)
        assertEquals("0x123", entries[0].contract)
        assertEquals(1700000000000L, entries[0].addedAt)
    }

    @Test
    fun `Gson parses WatchlistEntry with null fields`() {
        val json = """[{"symbol":"ETH","chain":null,"contract":null}]"""
        val type = object : TypeToken<List<WatchlistEntry>>() {}.type
        val entries: List<WatchlistEntry>? = GSON.fromJson(json, type)
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("ETH", entries[0].symbol)
        assertNull(entries[0].chain)
        assertNull(entries[0].contract)
    }

    @Test
    fun `Gson parses empty WatchlistEntry array`() {
        val json = "[]"
        val type = object : TypeToken<List<WatchlistEntry>>() {}.type
        val entries: List<WatchlistEntry>? = GSON.fromJson(json, type)
        assertNotNull(entries)
        assertTrue(entries!!.isEmpty())
    }

    @Test
    fun `Gson parses multiple WatchlistEntries`() {
        val json = """[
            {"symbol":"BTC","chain":"bitcoin","contract":null,"addedAt":1700000000000},
            {"symbol":"ETH","chain":"ethereum","contract":"0x123","addedAt":1700000001000},
            {"symbol":"SOL","chain":"solana","contract":"abc","addedAt":1700000002000}
        ]"""
        val type = object : TypeToken<List<WatchlistEntry>>() {}.type
        val entries: List<WatchlistEntry>? = GSON.fromJson(json, type)
        assertNotNull(entries)
        assertEquals(3, entries!!.size)
        assertEquals("BTC", entries[0].symbol)
        assertEquals("ETH", entries[1].symbol)
        assertEquals("SOL", entries[2].symbol)
    }

    @Test
    fun `Gson handles malformed JSON gracefully`() {
        val json = """[{"symbol":"BTC","chain":"bitcoin"""  // truncated
        val type = object : TypeToken<List<WatchlistEntry>>() {}.type
        try {
            val entries: List<WatchlistEntry>? = GSON.fromJson(json, type)
            // Gson should throw or return null on malformed JSON
            fail("Should throw on malformed JSON")
        } catch (_: Exception) {
            // Expected
        }
    }

    // ========== WatchlistEntry data class ==========

    @Test
    fun `WatchlistEntry default addedAt is near current time`() {
        val before = System.currentTimeMillis()
        val entry = WatchlistEntry("BTC", "bitcoin", null)
        val after = System.currentTimeMillis()
        assertTrue("addedAt should be between before and after",
            entry.addedAt in before..after)
    }

    @Test
    fun `WatchlistEntry equality works`() {
        val e1 = WatchlistEntry("BTC", "bitcoin", null, 1000L)
        val e2 = WatchlistEntry("BTC", "bitcoin", null, 1000L)
        assertEquals(e1, e2)
    }

    @Test
    fun `WatchlistEntry inequality on different symbol`() {
        val e1 = WatchlistEntry("BTC", "bitcoin", null, 1000L)
        val e2 = WatchlistEntry("ETH", "ethereum", null, 1000L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `WatchlistEntry inequality on different contract`() {
        val e1 = WatchlistEntry("BTC", "bitcoin", "0x123", 1000L)
        val e2 = WatchlistEntry("BTC", "bitcoin", "0x456", 1000L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `WatchlistEntry copy preserves values`() {
        val original = WatchlistEntry("BTC", "bitcoin", "0x123", 1000L)
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun `WatchlistEntry copy with modified symbol`() {
        val original = WatchlistEntry("BTC", "bitcoin", "0x123", 1000L)
        val modified = original.copy(symbol = "ETH")
        assertEquals("ETH", modified.symbol)
        assertEquals(original.chain, modified.chain)
        assertEquals(original.contract, modified.contract)
        assertEquals(original.addedAt, modified.addedAt)
    }

    // ========== Uppercase conversion (used in migration) ==========

    @Test
    fun `symbol uppercased with US locale`() {
        val entry = WatchlistEntry("btc", "bitcoin", null)
        val upper = entry.symbol.uppercase(java.util.Locale.US)
        assertEquals("BTC", upper)
    }

    @Test
    fun `uppercase handles mixed case`() {
        val entry = WatchlistEntry("BtC", "bitcoin", null)
        val upper = entry.symbol.uppercase(java.util.Locale.US)
        assertEquals("BTC", upper)
    }

    @Test
    fun `blank symbol detection works`() {
        val blankEntry = WatchlistEntry("   ", "bitcoin", null)
        assertTrue("Blank symbol should be detected", blankEntry.symbol.isBlank())

        val emptyEntry = WatchlistEntry("", "bitcoin", null)
        assertTrue("Empty symbol should be detected", emptyEntry.symbol.isBlank())
    }
}
