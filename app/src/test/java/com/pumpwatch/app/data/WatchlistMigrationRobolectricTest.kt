package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 133: integration test برای WatchlistMigration.
 *
 * 🔑 خاصیت ایمنی که تست می‌کنیم (فارغ از در دسترس بودن Keystore در Robolectric):
 * - اگر migrateIfNeeded == true  → کلید legacy پاک شده و flag ست شده
 * - اگر migrateIfNeeded == false → کلید legacy دست‌نخورده مانده
 *
 * این همان guarantee ای است که دادهٔ کاربر هرگز گم نمی‌شود.
 * در Robolectric معمولاً Keystore در دسترس نیست → مسیر fail-safe (abort)
 * اجرا می‌شود و تست تأیید می‌کند که حتی در آن مسیر هم legacy حفظ می‌ماند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistMigrationRobolectricTest {

    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences

    private val LEGACY_JSON =
        """[{"symbol":"btc","chain":"bitcoin","contract":null,"addedAt":1700000000000}]"""

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        prefs.edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
    }

    private fun seedLegacy(json: String) {
        prefs.edit().putString("watchlist_v1", json).apply()
    }

    // ========== سناریو ۱: دادهٔ legacy نیست ==========

    @Test
    fun `no legacy data sets flag and returns false`() {
        val result = WatchlistMigration.migrateIfNeeded(ctx)
        assertFalse("Nothing to migrate → false", result)
        assertTrue("Flag must be set even when nothing to migrate",
            prefs.getBoolean("watchlist_migrated_v2", false))
    }

    // ========== سناریو ۲: قبلاً مهاجرت شده ==========

    @Test
    fun `already migrated returns false without touching legacy`() {
        prefs.edit().putBoolean("watchlist_migrated_v2", true).apply()
        seedLegacy(LEGACY_JSON)
        val result = WatchlistMigration.migrateIfNeeded(ctx)
        assertFalse(result)
        assertEquals("Legacy must remain untouched when flag set",
            LEGACY_JSON, prefs.getString("watchlist_v1", null))
    }

    // ========== سناریو ۳: خاصیت ایمنی اصلی ==========

    @Test
    fun `SAFETY INVARIANT legacy never lost`() {
        seedLegacy(LEGACY_JSON)
        val result = WatchlistMigration.migrateIfNeeded(ctx)
        val legacyStillThere = prefs.getString("watchlist_v1", null) != null
        val flagSet = prefs.getBoolean("watchlist_migrated_v2", false)

        if (result) {
            assertFalse("On success legacy key must be removed", legacyStillThere)
            assertTrue("On success flag must be set", flagSet)
        } else {
            assertTrue("On abort legacy key must be preserved", legacyStillThere)
        }
    }

    @Test
    fun `abort path keeps flag unset so retry is possible later`() {
        seedLegacy(LEGACY_JSON)
        val result = WatchlistMigration.migrateIfNeeded(ctx)
        if (!result) {
            // اگر abort شده (مثلاً Keystore در دسترس نیست)، flag نباید ست شود
            // تا پس از رفع مشکل، مهاجرت دوباره تلاش شود
            val legacyThere = prefs.getString("watchlist_v1", null) != null
            if (legacyThere) {
                assertFalse("Abort must not set flag while legacy exists",
                    prefs.getBoolean("watchlist_migrated_v2", false))
            }
        }
    }

    // ========== سناریو ۴: JSON خراب ==========

    @Test
    fun `malformed legacy json is treated as empty and flag set`() {
        seedLegacy("""[{"symbol":"btc""")  // truncated
        val result = WatchlistMigration.migrateIfNeeded(ctx)
        assertFalse("Malformed JSON must not crash or migrate", result)
        assertTrue("Malformed = nothing to migrate → flag set",
            prefs.getBoolean("watchlist_migrated_v2", false))
    }

    // ========== سناریو ۵: reset flag ==========

    @Test
    fun `resetMigrationFlag clears the flag`() {
        prefs.edit().putBoolean("watchlist_migrated_v2", true).apply()
        WatchlistMigration.resetMigrationFlag(ctx)
        assertFalse(prefs.getBoolean("watchlist_migrated_v2", false))
    }

    @Test
    fun `resetMigrationFlag is safe when flag never set`() {
        WatchlistMigration.resetMigrationFlag(ctx)  // نباید crash کند
        assertFalse(prefs.getBoolean("watchlist_migrated_v2", false))
    }
}
