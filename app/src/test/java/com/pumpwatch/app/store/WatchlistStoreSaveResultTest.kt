package com.pumpwatch.app.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.data.SecureStorage
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 150: تست انتشار نتیجهٔ واقعی ذخیره.
 *
 * invariant: وقتی Keystore در دسترس نیست، همهٔ mutator ها false
 * برمی‌گردانند و کاربر هرگز پیام «موفقیت» دروغین نمی‌بیند.
 * این همان شکاف UX مستند در ADR-0002 است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistStoreSaveResultTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
    }

    @Test
    fun `addGroup returns false when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        assertFalse("addGroup must return false when save fails",
            WatchlistStore.addGroup(ctx, "Test"))
    }

    @Test
    fun `addCoin returns false when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        assertFalse(WatchlistStore.addCoin(ctx, "any",
            WatchCoin(id = "btc", symbol = "BTC", name = "Bitcoin")))
    }

    @Test
    fun `removeGroup returns false when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        assertFalse(WatchlistStore.removeGroup(ctx, "any"))
    }

    @Test
    fun `saveGroups returns false when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        assertFalse("saveGroups must surface real failure",
            WatchlistStore.saveGroups(ctx, emptyList()))
    }

    @Test
    fun `renameGroup returns false when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        assertFalse(WatchlistStore.renameGroup(ctx, "any", "new"))
    }

    @Test
    fun `rearmAlert returns false when keystore unavailable`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        assertFalse(WatchlistStore.rearmAlert(ctx, "g", "c", "a"))
    }

    @Test
    fun `insecure fallback flag is raised after failed save attempt`() {
        assumeFalse(SecureStorage.isKeystoreAvailable())
        WatchlistStore.addGroup(ctx, "Test")
        assertTrue("Insecure flag must be raised after failed save",
            SecureStorage.isInsecureFallback(ctx))
    }
}
