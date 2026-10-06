package com.pumpwatch.app.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 134: integration tests برای WatchlistStore.
 *
 * 🛡️ نکتهٔ Robolectric:
 * Android Keystore در محیط Robolectric معمولاً در دسترس است (از نسخهٔ ۴.۷ به بعد)
 * و SecureStorage موفق می‌شود کلید بسازد. اگر Keystore در دسترس نباشد،
 * saveGroups SecretResult.KeystoreUnavailable برمی‌گرداند و loadGroups خالی است.
 * این تست‌ها بر روی رفتار happy path تمرکز دارند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistStoreRobolectricTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // پاک کردن store و eval status
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
    }

    // ========== Empty store ==========

    @Test
    fun `loadGroups on empty store returns empty list`() {
        val groups = WatchlistStore.loadGroups(ctx)
        assertTrue(groups.isEmpty())
    }

    // ========== addGroup / removeGroup ==========

    @Test
    fun `addGroup creates group with empty coins`() {
        val ok = WatchlistStore.addGroup(ctx, "Test Group")
        assertTrue(ok)
        val groups = WatchlistStore.loadGroups(ctx)
        assertEquals(1, groups.size)
        assertEquals("Test Group", groups[0].name)
        assertTrue(groups[0].coins.isEmpty())
    }

    @Test
    fun `addGroup at MAX_GROUPS returns false`() {
        repeat(WatchlistStore.MAX_GROUPS) { i ->
            val ok = WatchlistStore.addGroup(ctx, "G$i")
            assertTrue("Group $i should be added", ok)
        }
        val overLimit = WatchlistStore.addGroup(ctx, "Overflow")
        assertFalse("Must reject over MAX_GROUPS", overLimit)
        assertEquals(WatchlistStore.MAX_GROUPS, WatchlistStore.loadGroups(ctx).size)
    }

    @Test
    fun `removeGroup removes the correct group`() {
        WatchlistStore.addGroup(ctx, "A")
        WatchlistStore.addGroup(ctx, "B")
        val idToRemove = WatchlistStore.loadGroups(ctx).first { it.name == "A" }.id
        WatchlistStore.removeGroup(ctx, idToRemove)
        val remaining = WatchlistStore.loadGroups(ctx).map { it.name }
        assertEquals(listOf("B"), remaining)
    }

    @Test
    fun `renameGroup updates name only`() {
        WatchlistStore.addGroup(ctx, "Old")
        val id = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.renameGroup(ctx, id, "New")
        assertEquals("New", WatchlistStore.loadGroups(ctx)[0].name)
    }

    // ========== addCoin / removeCoin ==========

    @Test
    fun `addCoin adds coin to existing group`() {
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        val coin = WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin")
        val ok = WatchlistStore.addCoin(ctx, gId, coin)
        assertTrue(ok)
        val coins = WatchlistStore.loadGroups(ctx)[0].coins
        assertEquals(1, coins.size)
        assertEquals("btc", coins[0].id)
    }

    @Test
    fun `addCoin rejects duplicate id`() {
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        val coin = WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin")
        assertTrue(WatchlistStore.addCoin(ctx, gId, coin))
        assertFalse("Duplicate id must be rejected", WatchlistStore.addCoin(ctx, gId, coin))
    }

    @Test
    fun `removeCoin removes coin from group`() {
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.addCoin(ctx, gId, WatchCoin("btc", "BTC", "Bitcoin"))
        WatchlistStore.addCoin(ctx, gId, WatchCoin("eth", "ETH", "Ethereum"))
        WatchlistStore.removeCoin(ctx, gId, "btc")
        val remaining = WatchlistStore.loadGroups(ctx)[0].coins.map { it.id }
        assertEquals(listOf("eth"), remaining)
    }

    // ========== addAlert / rearmAlert ==========

    @Test
    fun `addAlert adds alert to coin`() {
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.addCoin(ctx, gId, WatchCoin("btc", "BTC", "Bitcoin"))
        val alert = WatchAlert(id = "a1", above = true, threshold = 50000.0)
        val ok = WatchlistStore.addAlert(ctx, gId, "btc", alert)
        assertTrue(ok)
        val alerts = WatchlistStore.loadGroups(ctx)[0].coins[0].alerts
        assertEquals(1, alerts.size)
        assertEquals("a1", alerts[0].id)
    }

    @Test
    fun `rearmAlert clears triggeredAt`() {
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.addCoin(ctx, gId, WatchCoin("btc", "BTC", "Bitcoin"))
        val alert = WatchAlert(id = "a1", above = true, threshold = 50000.0,
            triggeredAt = 12345L, triggeredPrice = 50100.0)
        WatchlistStore.addAlert(ctx, gId, "btc", alert)
        WatchlistStore.rearmAlert(ctx, gId, "btc", "a1")
        val rearmed = WatchlistStore.loadGroups(ctx)[0].coins[0].alerts[0]
        assertNull("triggeredAt should be cleared", rearmed.triggeredAt)
        assertNull("triggeredPrice should be cleared", rearmed.triggeredPrice)
    }

    // ========== lastEvalStatus ==========

    @Test
    fun `lastEvalStatus default is ok true with timestamp 0`() {
        val (ok, ts) = WatchlistStore.lastEvalStatus(ctx)
        assertTrue(ok)
        assertEquals(0L, ts)
    }

    @Test
    fun `markEval ok false is readable`() {
        // markEval private است → از checkAndFire شبیه‌سازی می‌کنیم
        // چون checkAndFire به شبکه نیاز دارد، مستقیم prefs را set می‌کنیم
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("watchlist_last_eval_ok", false)
            .putLong("watchlist_last_eval_ts", 1700000000000L)
            .apply()
        val (ok, ts) = WatchlistStore.lastEvalStatus(ctx)
        assertFalse(ok)
        assertEquals(1700000000000L, ts)
    }

    @Test
    fun `markEval ok true is readable`() {
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("watchlist_last_eval_ok", true)
            .putLong("watchlist_last_eval_ts", 1700000000000L)
            .apply()
        val (ok, ts) = WatchlistStore.lastEvalStatus(ctx)
        assertTrue(ok)
        assertEquals(1700000000000L, ts)
    }

    // ========== Save/load roundtrip ==========

    @Test
    fun `save and load roundtrip preserves data`() {
        val group = WatchGroup(
            id = "g1",
            name = "Test",
            coins = listOf(
                WatchCoin("btc", "BTC", "Bitcoin",
                    alerts = listOf(WatchAlert("a1", true, 50000.0)))
            )
        )
        WatchlistStore.saveGroups(ctx, listOf(group))
        val loaded = WatchlistStore.loadGroups(ctx)
        assertEquals(1, loaded.size)
        assertEquals("g1", loaded[0].id)
        assertEquals("Test", loaded[0].name)
        assertEquals(1, loaded[0].coins.size)
        assertEquals("btc", loaded[0].coins[0].id)
        assertEquals(1, loaded[0].coins[0].alerts.size)
        assertEquals("a1", loaded[0].coins[0].alerts[0].id)
    }
}
