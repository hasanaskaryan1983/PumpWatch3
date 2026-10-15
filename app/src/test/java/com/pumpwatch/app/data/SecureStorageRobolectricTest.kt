package com.pumpwatch.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 134 (bonus): تست‌هایی که در JVM معمولی ممکن نبودند
 * (چون android.util.Base64 فقط در محیط Android/Robolectric کار می‌کند).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureStorageRobolectricTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
    }

    @Test
    fun `packBlob and unpackBlob roundtrip`() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = byteArrayOf(20, 21, 22, 23, 24)
        val blob = SecureStorage.packBlob(iv, ct)
        val unpacked = SecureStorage.unpackBlob(blob)
        assertNotNull(unpacked)
        val (uIv, uCt) = unpacked!!
        assertArrayEquals(iv, uIv)
        assertArrayEquals(ct, uCt)
    }

    @Test
    fun `getString returns null when nothing stored`() {
        assertNull(SecureStorage.getString(ctx, "missing"))
    }

    @Test
    fun `putString fail-closed invariant holds in any environment`() {
        val result = SecureStorage.putString(ctx, "k", "secret-value")
        val read = SecureStorage.getString(ctx, "k")
        val plaintextLeak = ctx.getSharedPreferences("pumpwatch_secure_prefs", 0)
            .all.values.any { (it as? String)?.contains("secret-value") == true }

        if (result == SecureStorage.SecretResult.Saved) {
            assertEquals("secret-value", read)
            assertFalse(plaintextLeak)  // حتی در مسیر موفق، plaintext ذخیره نمی‌شود
        } else {
            assertNull("Fail-closed must not return data", read)
            assertFalse("Fail-closed must not write plaintext", plaintextLeak)
            assertTrue(SecureStorage.isInsecureFallback(ctx))
        }
    }

    @Test
    fun `insecure fallback read path returns raw blob when flag set`() {
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit()
            .putString("legacy_key", "raw-plaintext").apply()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("secure_storage_fell_back", true).apply()
        assertEquals("raw-plaintext", SecureStorage.getString(ctx, "legacy_key"))
    }

    @Test
    fun `wipeAll clears secure prefs and fallback flag`() {
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit()
            .putString("a", "b").apply()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("secure_storage_fell_back", true).apply()
        SecureStorage.wipeAll(ctx)
        assertTrue(ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).all.isEmpty())
        assertFalse(SecureStorage.isInsecureFallback(ctx))
    }
}
