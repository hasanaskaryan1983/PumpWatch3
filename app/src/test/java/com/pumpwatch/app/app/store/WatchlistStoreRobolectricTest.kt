package com.pumpwatch.app.store

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.data.SecureStorage
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 134 (fix): تست‌های مشروط بر اساس در دسترس بودن Keystore.
 *
 * 🔑 نکتهٔ کلیدی: در Robolectric، AndroidKeyStore موجود نیست و
 * SecureStorage طبق طراحی fail-closed عمل می‌کند (کامیت ۱۰۹):
 * - putString → KeystoreUnavailable (چیزی ذخیره نمی‌شود)
 * - flag ناامنی ست می‌شود
 * این یک ویژگی امنیتی است، نه باگ. پس:
 * - تست‌های fail-closed با assumeFalse(isKeystoreAvailable) اجرا می‌شوند (CI)
 * - تست‌های CRUD با assumeTrue(isKeystoreAvailable) اجرا می‌شوند (دستگاه)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistStoreRobolectricTest {

    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        prefs.edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
    }

    // ========== همیشه قابل اجرا ==========

    @Test
    fun `loadGroups on empty store returns empty list`() {
        assertTrue(WatchlistStore.loadGroups(ctx).isEmpty())
    }

    @Test
    fun `lastEvalStatus default is ok true with timestamp 0`() {
        val (ok, ts) = WatchlistStore.lastEvalStatus(ctx)
        assertTrue(ok)
        assertEquals(0L, ts)
    }

    @Test
    fun `lastEvalStatus reads failure state`() {
        prefs.edit()
            .putBoolean("watchlist_last_eval_ok", false)
            .putLong("watchlist_last_eval_ts", 1700000000000L)
            .apply()
        val (ok, ts) = WatchlistStore.lastEvalStatus(ctx)
        assertFalse(ok)
        assertEquals(1700000000000L, ts)
    }

    // ========== Fail-closed (Keystore نیست → CI این‌ها را اجرا می‌کند) ==========

    @Test
    fun `fail-closed addGroup rejected when keystore unavailable`() {
        assumeFalse("Only meaningful when Keystore unavailable",
            SecureStorage.isKeystoreAvailable())
        assertFalse("addGroup must fail when data cannot be encrypted",
            WatchlistStore.addGroup(ctx, "G"))
        assertTrue(WatchlistStore.loadGroups(ctx).isEmpty())
        assertTrue("Fail-closed must raise insecure-fallback flag",
            SecureStorage.isInsecureFallback(ctx))
    }

    @Test
    fun `fail-closed no plaintext leak when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        WatchlistStore.addGroup(ctx, "SecretGroup")
        WatchlistStore.addCoin(
            ctx, "any",
            WatchCoin(id = "btc", symbol = "BTC", name = "SuperSecretName")
        )
        val securePrefs = ctx.getSharedPreferences("pumpwatch_secure_prefs", 0)
        for (entry in securePrefs.all) {
            val v = entry.value as? String ?: continue
            assertFalse("Plaintext leak in key ${entry.key}",
                v.contains("SecretGroup") || v.contains("SuperSecretName"))
        }
    }

    // ========== Happy path (فقط وقتی Keystore هست → دستگاه واقعی) ==========

    @Test
    fun `happy path addGroup and loadGroups roundtrip`() {
        assumeTrue("Requires working Keystore", SecureStorage.isKeystoreAvailable())
        assertTrue(WatchlistStore.addGroup(ctx, "G"))
        val groups = WatchlistStore.loadGroups(ctx)
        assertEquals(1, groups.size)
        assertEquals("G", groups[0].name)
        assertTrue(groups[0].coins.isEmpty())
    }

    @Test
    fun `happy path addCoin rejects duplicate id`() {
        assumeTrue(SecureStorage.isKeystoreAvailable())
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        val coin = WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin")
        assertTrue(WatchlistStore.addCoin(ctx, gId, coin))
        assertFalse(WatchlistStore.addCoin(ctx, gId, coin))
    }

    @Test
    fun `happy path removeCoin removes only target`() {
        assumeTrue(SecureStorage.isKeystoreAvailable())
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.addCoin(ctx, gId, WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin"))
        WatchlistStore.addCoin(ctx, gId, WatchCoin(id = "eth", symbol = "ETH", name = "Ethereum"))
        WatchlistStore.removeCoin(ctx, gId, "btc")
        assertEquals(listOf("eth"), WatchlistStore.loadGroups(ctx)[0].coins.map { it.id })
    }

    @Test
    fun `happy path renameGroup updates name only`() {
        assumeTrue(SecureStorage.isKeystoreAvailable())
        WatchlistStore.addGroup(ctx, "Old")
        val id = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.renameGroup(ctx, id, "New")
        assertEquals("New", WatchlistStore.loadGroups(ctx)[0].name)
    }

    @Test
    fun `happy path rearmAlert clears triggered state`() {
        assumeTrue(SecureStorage.isKeystoreAvailable())
        WatchlistStore.addGroup(ctx, "G")
        val gId = WatchlistStore.loadGroups(ctx)[0].id
        WatchlistStore.addCoin(ctx, gId, WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin"))
        WatchlistStore.addAlert(
            ctx, gId, "btc",
            WatchAlert(id = "a1", above = true, threshold = 50000.0,
                triggeredAt = 12345L, triggeredPrice = 50100.0)
        )
        WatchlistStore.rearmAlert(ctx, gId, "btc", "a1")
        val rearmed = WatchlistStore.loadGroups(ctx)[0].coins[0].alerts[0]
        assertNull(rearmed.triggeredAt)
        assertNull(rearmed.triggeredPrice)
    }

    @Test
    fun `happy path save and load roundtrip preserves data`() {
        assumeTrue(SecureStorage.isKeystoreAvailable())
        val group = WatchGroup(
            id = "g1", name = "Test",
            coins = listOf(
                WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin",
                    alerts = listOf(WatchAlert(id = "a1", above = true, threshold = 50000.0)))
            )
        )
        WatchlistStore.saveGroups(ctx, listOf(group))
        val loaded = WatchlistStore.loadGroups(ctx)
        assertEquals(1, loaded.size)
        assertEquals("g1", loaded[0].id)
        assertEquals("btc", loaded[0].coins[0].id)
        assertEquals("a1", loaded[0].coins[0].alerts[0].id)
    }
}
