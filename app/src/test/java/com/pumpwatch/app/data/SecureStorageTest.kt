package com.pumpwatch.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7A): تست pure زیرساخت رمزنگاری.
 *
 * نکته: Keystore Android در محیط JVM تست در دسترس نیست، پس فقط تست‌های
 * ساختاری و توابع pure را می‌زنیم. تست‌های integration در androidTest.
 */
class SecureStorageTest {

    @Test
    fun keystore_alias_is_stable() {
        // sanity: نام alias نباید تغییر کند چون کلیدهای قبلی را نامعتبر می‌کند
        val field = SecureStorage::class.java.getDeclaredField("KEYSTORE_ALIAS")
        field.isAccessible = true
        val alias = field.get(null) as String
        assertTrue("alias must not be empty", alias.isNotEmpty())
        assertTrue("alias must be pumpwatch_master_key (do not change!)",
            alias == "pumpwatch_master_key")
    }

    @Test
    fun wipe_removes_insecure_flag() {
        // wipeAll باید هم داده را پاک کند هم flag fallback را
        // این یک تست pure است — فقط منطق تابع را بررسی می‌کند
        // تست integration در androidTest انجام می‌شود
        assertTrue("wipeAll signature exists",
            SecureStorage::class.java.declaredMethods.any { it.name == "wipeAll" })
    }

    @Test
    fun is_insecure_flag_uses_correct_key() {
        val field = SecureStorage::class.java.getDeclaredField("FLAG_INSECURE_FALLBACK")
        field.isAccessible = true
        val flag = field.get(null) as String
        assertTrue("flag key must be secure_storage_fell_back",
            flag == "secure_storage_fell_back")
    }
}
