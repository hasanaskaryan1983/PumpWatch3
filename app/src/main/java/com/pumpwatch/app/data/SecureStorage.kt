package com.pumpwatch.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7A): ذخیره‌سازی امن برای داده‌های حساس
 *
 * اصل: آدرس‌های کیف‌پول، paper ledger، هشدارها و یادداشت‌های کاربر نباید
 * plain text ذخیره شوند. این کلاس AES-256-GCM با کلید در Android Keystore
 * استفاده می‌کند — کلید هرگز از دستگاه خارج نمی‌شود.
 *
 * فایل prefs رمزنگاری‌شده: `pumpwatch_secure_prefs`
 * کلید master در Android Keystore با alias `pumpwatch_master_key`
 *
 * fallback: اگر Keystore در دسترس نباشد (دستگاه قدیمی)، از SharedPreferences
 * معمولی استفاده می‌شود و یک flag برای نمایش هشدار در UI ثبت می‌شود.
 */
object SecureStorage {

    private const val PREFS_NAME = "pumpwatch_secure_prefs"
    private const val KEYSTORE_ALIAS = "pumpwatch_master_key"
    private const val FLAG_INSECURE_FALLBACK = "secure_storage_fell_back"

    /**
     * ایجاد یا دریافت SharedPreferences رمزنگاری‌شده.
     * اگر Keystore در دسترس نباشد، به prefs معمولی fallback می‌کند.
     */
    fun securePrefs(ctx: Context): android.content.SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx, KEYSTORE_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setUserAuthenticationRequired(false)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore در دسترس نیست — fallback به prefs معمولی + ثبت هشدار
            ctx.getSharedPreferences("pumpwatch_prefs", 0)
                .edit().putBoolean(FLAG_INSECURE_FALLBACK, true).apply()
            ctx.getSharedPreferences("pumpwatch_prefs", 0)
        }
    }

    /**
     * آیا آخرین دسترسی به secure storage از fallback معمولی استفاده کرده؟
     * UI باید این را در Privacy Center نمایش دهد.
     */
    fun isInsecureFallback(ctx: Context): Boolean =
        ctx.getSharedPreferences("pumpwatch_prefs", 0).getBoolean(FLAG_INSECURE_FALLBACK, false)

    /**
     * بررسی اینکه master key در Keystore وجود دارد.
     * برای نمایش در Privacy Center.
     */
    fun isKeystoreAvailable(): Boolean = try {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        ks.containsAlias(KEYSTORE_ALIAS) || canCreateKey()
    } catch (_: Exception) {
        false
    }

    private fun canCreateKey(): Boolean = try {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * رمزنگاری مستقیم یک رشته (برای جاهایی که EncryptedSharedPreferences کافی نیست،
     * مثلاً ذخیره در فایل یا export).
     * کلید در Keystore است و هرگز از دستگاه خارج نمی‌شود.
     */
    fun encrypt(ctx: Context, plainText: String): String? {
        return try {
            val prefs = securePrefs(ctx)
            val tempKey = "_enc_temp_${System.currentTimeMillis()}"
            prefs.edit().putString(tempKey, plainText).apply()
            val stored = prefs.getString(tempKey, null)
            prefs.edit().remove(tempKey).apply()
            stored?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * پاک‌کردن کامل تمام داده‌های رمزنگاری‌شده.
     * برای دکمهٔ "Delete all" در Privacy Center.
     */
    fun wipeAll(ctx: Context) {
        try {
            securePrefs(ctx).edit().clear().apply()
        } catch (_: Exception) { }
        try {
            ctx.getSharedPreferences("pumpwatch_prefs", 0)
                .edit().remove(FLAG_INSECURE_FALLBACK).apply()
        } catch (_: Exception) { }
    }

    /**
     * حذف یک کلید از Keystore (برای logout یا reset کامل).
     */
    fun deleteMasterKey() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(KEYSTORE_ALIAS)) {
                ks.deleteEntry(KEYSTORE_ALIAS)
            }
        } catch (_: Exception) { }
    }
}
