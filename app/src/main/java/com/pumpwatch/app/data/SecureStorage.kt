package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7A): ذخیره‌سازی امن بدون dependency خارجی
 *
 * رمزنگاری: AES-256-GCM با کلید داخل Android Keystore
 * (کلید هرگز از دستگاه خارج نمی‌شود؛ حتی root هم نمی‌تواند استخراجش کند).
 *
 * ساختار blob ذخیره‌شده: Base64( iv[12] + ciphertext )
 * فایل prefs: pumpwatch_secure_prefs
 *
 * fallback صادقانه: اگر Keystore در دسترس نباشد، مقدار plain ذخیره می‌شود
 * و پرچم secure_storage_fell_back برای افشا در Privacy Center ثبت می‌گردد.
 * هرگز وانمود نمی‌کنیم رمزنگاری شده وقتی نشده.
 *
 * 🚀 Commit 44 (فاز ۱ برنامهٔ اجرایی): putSecret/getSecret fail-closed
 * برای API keyها که نباید به هیچ وجه plain ذخیره شوند. اگر Keystore
 * در دسترس نباشد، ذخیره نمی‌شود و false/خطا برگردانده می‌شود.
 */
object SecureStorage {

    private const val PREFS_NAME = "pumpwatch_secure_prefs"
    private const val KEYSTORE_ALIAS = "pumpwatch_master_key"
    private const val FLAG_INSECURE_FALLBACK = "secure_storage_fell_back"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** نتیجه تلاش putSecret — برای گزارش صادقانه به UI */
    sealed class SecretResult {
        object Saved : SecretResult()
        object KeystoreUnavailable : SecretResult()
        data class CryptoError(val message: String) : SecretResult()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun flagPrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("pumpwatch_prefs", Context.MODE_PRIVATE)

    /** ساخت یا دریافت کلید AES از Android Keystore */
    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    /** آیا Keystore روی این دستگاه کار می‌کند؟ (برای Privacy Center) */
    fun isKeystoreAvailable(): Boolean = try {
        getOrCreateKey()
        true
    } catch (_: Exception) {
        false
    }

    /** آیا آخرین ذخیره‌سازی از fallback ناامن استفاده کرده؟ (برای افشا در UI) */
    fun isInsecureFallback(ctx: Context): Boolean =
        flagPrefs(ctx).getBoolean(FLAG_INSECURE_FALLBACK, false)

    // ---------- توابع pure و internal برای تست JVM ----------

    internal fun joinRaw(iv: ByteArray, ct: ByteArray): ByteArray = iv + ct

    internal fun splitRaw(raw: ByteArray): Pair<ByteArray, ByteArray>? =
        if (raw.size <= IV_BYTES) null
        else raw.copyOfRange(0, IV_BYTES) to raw.copyOfRange(IV_BYTES, raw.size)

    internal fun packBlob(iv: ByteArray, ct: ByteArray): String =
        Base64.encodeToString(joinRaw(iv, ct), Base64.NO_WRAP)

    internal fun unpackBlob(blob: String): Pair<ByteArray, ByteArray>? = try {
        splitRaw(Base64.decode(blob, Base64.NO_WRAP))
    } catch (_: Exception) {
        null
    }

    // ---------- API عمومی (با fallback صادقانه — برای ledger/user data) ----------

    fun putString(ctx: Context, key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv ?: throw IllegalStateException("GCM iv missing")
            val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs(ctx).edit().putString(key, packBlob(iv, ct)).apply()
            if (isInsecureFallback(ctx)) {
                flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, false).apply()
            }
        } catch (_: Exception) {
            prefs(ctx).edit().putString(key, value).apply()
            flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, true).apply()
        }
    }

    fun getString(ctx: Context, key: String): String? {
        val blob = prefs(ctx).getString(key, null) ?: return null
        val unpacked = unpackBlob(blob)
        if (unpacked != null) {
            try {
                val (iv, ct) = unpacked
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
                return String(cipher.doFinal(ct), Charsets.UTF_8)
            } catch (_: Exception) { }
        }
        return if (isInsecureFallback(ctx)) blob else null
    }

    fun remove(ctx: Context, key: String) {
        prefs(ctx).edit().remove(key).apply()
    }

    fun wipeAll(ctx: Context) {
        try { prefs(ctx).edit().clear().apply() } catch (_: Exception) { }
        try { flagPrefs(ctx).edit().remove(FLAG_INSECURE_FALLBACK).apply() } catch (_: Exception) { }
    }

    fun deleteMasterKey() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(KEYSTORE_ALIAS)) ks.deleteEntry(KEYSTORE_ALIAS)
        } catch (_: Exception) { }
    }

    // ---------- API fail-closed برای secretها (Commit 44) ----------

    /**
     * ذخیرهٔ رمزنگاری‌شده بدون fallback. اگر Keystore کار نکند،
     * ذخیره نمی‌شود و KeystoreUnavailable برگردانده می‌شود.
     * این برای API keyها که نباید به هیچ وجه plain بمانند.
     */
    fun putSecret(ctx: Context, key: String, value: String): SecretResult {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv ?: throw IllegalStateException("GCM iv missing")
            val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs(ctx).edit().putString(key, packBlob(iv, ct)).apply()
            SecretResult.Saved
        } catch (keystoreEx: java.security.KeyStoreException) {
            SecretResult.KeystoreUnavailable
        } catch (ex: Exception) {
            val msg = ex.message ?: ex::class.java.simpleName
            if (msg.contains("Keystore", true) || msg.contains("Keymaster", true)
                || msg.contains("unavailable", true)) {
                SecretResult.KeystoreUnavailable
            } else {
                SecretResult.CryptoError(msg)
            }
        }
    }

    /**
     * خواندن secret: فقط رمزگشایی امن. اگر blob خراب بود یا Keystore
     * در دسترس نبود، null برگردانده می‌شود (نه plain text).
     */
    fun getSecret(ctx: Context, key: String): String? {
        val blob = prefs(ctx).getString(key, null) ?: return null
        val unpacked = unpackBlob(blob) ?: return null
        return try {
            val (iv, ct) = unpacked
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * بررسی وجود secret بدون رمزگشایی (ارزان؛ برای نمایش "Configured" در UI).
     */
    fun hasSecret(ctx: Context, key: String): Boolean =
        prefs(ctx).getString(key, null) != null
}
