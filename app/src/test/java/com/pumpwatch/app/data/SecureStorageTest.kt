package com.pumpwatch.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7A): تست‌های JVM-safe زیرساخت رمزنگاری.
 *
 * Keystore Android روی JVM تست در دسترس نیست؛ پس اینجا فقط منطق pure
 * (join/split blob) و ثابت‌های حیاتی تست می‌شوند. تست رمزنگازی واقعی
 * در androidTest روی دستگاه انجام می‌شود.
 */
class SecureStorageTest {

    @Test
    fun keystore_alias_is_stable_and_locked() {
        // اگر این alias عوض شود، دادهٔ رمزنگاری‌شدهٔ کاربران قبلی می‌سوزد
        val f = SecureStorage::class.java.getDeclaredField("KEYSTORE_ALIAS")
        f.isAccessible = true
        assertEquals("pumpwatch_master_key", f.get(null))
    }

    @Test
    fun insecure_flag_key_is_stable() {
        val f = SecureStorage::class.java.getDeclaredField("FLAG_INSECURE_FALLBACK")
        f.isAccessible = true
        assertEquals("secure_storage_fell_back", f.get(null))
    }

    @Test
    fun iv_length_is_twelve_bytes_for_gcm() {
        val f = SecureStorage::class.java.getDeclaredField("IV_BYTES")
        f.isAccessible = true
        assertEquals(12, f.get(null))
    }

    @Test
    fun join_split_roundtrip_is_exact() {
        val iv = ByteArray(12) { (it + 1).toByte() }
        val ct = byteArrayOf(9, 8, 7, 6, 5, 4)
        val raw = SecureStorage.joinRaw(iv, ct)
        assertEquals(iv.size + ct.size, raw.size)
        val (iv2, ct2) = SecureStorage.splitRaw(raw)!!
        assertArrayEquals(iv, iv2)
        assertArrayEquals(ct, ct2)
    }

    @Test
    fun split_rejects_blob_without_ciphertext() {
        // blob فقط به اندازهٔ iv یا کمتر → نامعتبر
        assertNull(SecureStorage.splitRaw(ByteArray(12)))
        assertNull(SecureStorage.splitRaw(ByteArray(3)))
        assertNull(SecureStorage.splitRaw(ByteArray(0)))
    }

    @Test
    fun split_keeps_everything_after_iv_as_ciphertext() {
        val raw = ByteArray(40) { it.toByte() }
        val (iv, ct) = SecureStorage.splitRaw(raw)!!
        assertEquals(12, iv.size)
        assertEquals(28, ct.size)
    }

    @Test
    fun public_api_surface_exists() {
        val names = SecureStorage::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue("putString missing", "putString" in names)
        assertTrue("getString missing", "getString" in names)
        assertTrue("wipeAll missing", "wipeAll" in names)
        assertTrue("deleteMasterKey missing", "deleteMasterKey" in names)
        assertTrue("isInsecureFallback missing", "isInsecureFallback" in names)
        assertTrue("isKeystoreAvailable missing", "isKeystoreAvailable" in names)
    }
}
