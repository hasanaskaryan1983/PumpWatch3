package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.store.WatchlistStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * 🚀 S8: قرارداد ایمنی مهاجرت واچ‌لیست.
 * مطابق کنوانسیون مخزن (کامیت ۱۳۴): در Robolectric بدون Keystore مسیر fail-closed
 * اجرا می‌شود؛ تست‌های موفقیت فقط روی دستگاه/امولاتور اجرا می‌شوند (assumeTrue).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistMigrationS8Test {

    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences

    private val legacyJson =
        """[{"symbol":"BTC","chain":null,"contract":null,"addedAt":1700000000000},
            {"symbol":"ETH","chain":null,"contract":null,"addedAt":1700000000001}]"""

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        prefs.edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
    }

    private fun keystoreAvailable(): Boolean = try {
        val r = SecureStorage.putString(ctx, "s8_probe", "x")
        SecureStorage.remove(ctx, "s8_probe")
        r == SecureStorage.SecretResult.Saved
    } catch (e: Exception) {
        false
    }

    @Test
    fun `S8 - fail-closed abort preserves legacy and sets neither flag nor quarantine`() {
        assumeFalse(keystoreAvailable())
        prefs.edit().putString("watchlist_v1", legacyJson).commit()

        val result = WatchlistMigration.migrateIfNeeded(ctx)

        assertFalse("abort must return false", result)
        assertEquals("legacy preserved on abort", legacyJson, prefs.getString("watchlist_v1", null))
        assertFalse("flag must NOT be set on abort", prefs.getBoolean("watchlist_migrated_v2", false))
        assertNull("quarantine must NOT exist on abort", prefs.getString("watchlist_v1_quarantine", null))
    }

    @Test
    fun `S8 - verified success quarantines legacy atomically`() {
        assumeTrue(keystoreAvailable())
        prefs.edit().putString("watchlist_v1", legacyJson).commit()

        val result = WatchlistMigration.migrateIfNeeded(ctx)

        assertTrue("migration must succeed with healthy store", result)
        assertNull("legacy removed only after verified success", prefs.getString("watchlist_v1", null))
        assertEquals("recoverable quarantine copy", legacyJson, prefs.getString("watchlist_v1_quarantine", null))
        assertTrue("flag set", prefs.getBoolean("watchlist_migrated_v2", false))
        val symbols = WatchlistStore.loadGroups(ctx)
            .firstOrNull { it.name == "واردشده (Legacy)" }
            ?.coins?.map { it.symbol.uppercase(Locale.US) }?.toSet() ?: emptySet()
        assertEquals(setOf("BTC", "ETH"), symbols)
    }

    @Test
    fun `S8 - invariant holds in both worlds and second run is no-op`() {
        prefs.edit().putString("watchlist_v1", legacyJson).commit()

        val first = WatchlistMigration.migrateIfNeeded(ctx)
        val second = WatchlistMigration.migrateIfNeeded(ctx)

        assertFalse("second run must be no-op", second)
        if (first) {
            assertNull(prefs.getString("watchlist_v1", null))
            assertEquals(legacyJson, prefs.getString("watchlist_v1_quarantine", null))
            assertTrue(prefs.getBoolean("watchlist_migrated_v2", false))
        } else {
            assertEquals(legacyJson, prefs.getString("watchlist_v1", null))
            assertFalse(prefs.getBoolean("watchlist_migrated_v2", false))
            assertNull(prefs.getString("watchlist_v1_quarantine", null))
        }
    }

    @Test
    fun `S8 - concurrent calls migrate at most once (lock)`() {
        prefs.edit().putString("watchlist_v1", legacyJson).commit()
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val threads = (1..4).map {
            Thread { results.add(WatchlistMigration.migrateIfNeeded(ctx)) }.also { it.start() }
        }
        threads.forEach { it.join() }

        assertTrue("at most one successful migration", results.count { it } <= 1)
        val group = WatchlistStore.loadGroups(ctx).firstOrNull { it.name == "واردشده (Legacy)" }
        val symbols = group?.coins?.map { it.symbol.uppercase(Locale.US) } ?: emptyList()
        assertEquals("no duplicate coins", symbols.size, symbols.toSet().size)
        if (results.any { it }) {
            assertNull(prefs.getString("watchlist_v1", null))
            assertEquals(legacyJson, prefs.getString("watchlist_v1_quarantine", null))
        } else {
            assertEquals(legacyJson, prefs.getString("watchlist_v1", null))
        }
    }
}
