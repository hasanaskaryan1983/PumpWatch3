package com.pumpwatch.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.store.WatchlistStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class WatchlistMigrationS8Test {

    private val legacyJson =
        """[{"symbol":"BTC","chain":null,"contract":null,"addedAt":1700000000000},
            {"symbol":"ETH","chain":null,"contract":null,"addedAt":1700000000001}]"""

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    private fun prefs() = ctx().getSharedPreferences("pumpwatch_prefs", Context.MODE_PRIVATE)

    @Test
    fun `S8 - success moves legacy to quarantine and verifies persistence`() {
        prefs().edit().putString("watchlist_v1", legacyJson).commit()

        val result = WatchlistMigration.migrateIfNeeded(ctx())

        assertTrue("migration must succeed", result)
        assertNull("legacy key removed only after verified success", prefs().getString("watchlist_v1", null))
        assertEquals("quarantine keeps recoverable copy", legacyJson, prefs().getString("watchlist_v1_quarantine", null))
        assertTrue("flag set", prefs().getBoolean("watchlist_migrated_v2", false))
        val group = WatchlistStore.loadGroups(ctx()).firstOrNull { it.name == "واردشده (Legacy)" }
        val symbols = group?.coins?.map { it.symbol.uppercase(Locale.US) }?.toSet() ?: emptySet()
        assertEquals(setOf("BTC", "ETH"), symbols)
    }

    @Test
    fun `S8 - second run is a no-op and quarantine intact`() {
        prefs().edit().putString("watchlist_v1", legacyJson).commit()
        assertTrue(WatchlistMigration.migrateIfNeeded(ctx()))

        assertFalse("second run must be false", WatchlistMigration.migrateIfNeeded(ctx()))
        assertEquals(legacyJson, prefs().getString("watchlist_v1_quarantine", null))
    }

    @Test
    fun `S8 - concurrent calls migrate exactly once (lock)`() {
        prefs().edit().putString("watchlist_v1", legacyJson).commit()
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val threads = (1..4).map {
            Thread { results.add(WatchlistMigration.migrateIfNeeded(ctx())) }.also { it.start() }
        }
        threads.forEach { it.join() }

        assertEquals("exactly one successful migration", 1, results.count { it })
        val group = WatchlistStore.loadGroups(ctx()).firstOrNull { it.name == "واردشده (Legacy)" }
        assertEquals("no duplicate coins", 2, group?.coins?.size ?: 0)
    }
}
