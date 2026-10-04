package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 🚀 Sprint 14 + Commit 78 + Commit 109 + Commit 111:
 * ذخیره‌سازی امن AES-256-GCM با کلید داخل Android Keystore.
 *
 * 🚀 Commit 111: نام SecretResult و isInsecureFallback برای سازگاری
 * با RpcKeyStore و WalletForensics حفظ شده است.
 *
 * 🚀 Commit 109: putString دیگر بی‌صدا plaintext نمی‌نویسد (fail-closed).
 */
object SecureStorage {

    private const val PREFS_NAME = "pumpwatch_secure_prefs"
    private const val KEYSTORE_ALIAS = "pumpwatch_master_key"
    private const val FLAG_INSECURE_FALLBACK = "secure_storage_fell_back"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG = "SecureStorage"

    /** 🚀 Commit 111: نام SecretResult برای سازگاری با callers قدیمی */
    sealed class SecretResult {
        object Saved : SecretResult()
        object KeystoreUnavailable : SecretResult()
        data class CryptoError(val message: String) : SecretResult()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun flagPrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("pumpwatch_prefs", Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

            val existingKey = try {
                ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            } catch (e: UnrecoverableKeyException) {
                Log.w(TAG, "Master key unrecoverable (PIN/biometric changed?), regenerating", e)
                null
            } catch (e: KeyPermanentlyInvalidatedException) {
                Log.w(TAG, "Master key permanently invalidated, regenerating", e)
                null
            }

            if (existingKey != null) {
                try {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, existingKey)
                    return existingKey
                } catch (e: KeyPermanentlyInvalidatedException) {
                    Log.w(TAG, "Master key invalidated during use, regenerating", e)
                    if (ks.containsAlias(KEYSTORE_ALIAS)) {
                        ks.deleteEntry(KEYSTORE_ALIAS)
                    }
                }
            }

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
            kg.generateKey()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create master key", e)
            null
        }
    }

    fun isKeystoreAvailable(): Boolean = getOrCreateKey() != null

    /** 🚀 Commit 111: alias سازگاری برای callers قدیمی */
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

    // ---------- API عمومی (Fail-Closed) ----------

    /**
     * 🚀 Commit 109: اگر Keystore در دسترس نباشد، داده ذخیره نمی‌شود.
     */
    fun putString(ctx: Context, key: String, value: String): SecretResult {
        val masterKey = getOrCreateKey()
        if (masterKey == null) {
            Log.e(TAG, "Keystore unavailable — putString rejected (fail-closed)")
            flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, true).apply()
            return SecretResult.KeystoreUnavailable
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv ?: throw IllegalStateException("GCM iv missing")
            val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs(ctx).edit().putString(key, packBlob(iv, ct)).apply()
            if (isInsecureFallback(ctx)) {
                flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, false).apply()
            }
            SecretResult.Saved
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.e(TAG, "Key invalidated during putString, data not saved", e)
            flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, true).apply()
            SecretResult.KeystoreUnavailable
        } catch (e: Exception) {
            Log.e(TAG, "Crypto error during putString", e)
            SecretResult.CryptoError(e.message ?: e::class.java.simpleName)
        }
    }

    fun getString(ctx: Context, key: String): String? {
        val blob = prefs(ctx).getString(key, null) ?: return null
        val unpacked = unpackBlob(blob)
        if (unpacked != null) {
            val masterKey = getOrCreateKey()
            if (masterKey == null) {
                Log.e(TAG, "Keystore unavailable — getString returns null (fail-closed)")
                flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, true).apply()
                return null
            }
            try {
                val (iv, ct) = unpacked
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(TAG_BITS, iv))
                return String(cipher.doFinal(ct), Charsets.UTF_8)
            } catch (e: KeyPermanentlyInvalidatedException) {
                Log.e(TAG, "Key invalidated during getString, data lost", e)
                flagPrefs(ctx).edit().putBoolean(FLAG_INSECURE_FALLBACK, true).apply()
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Crypto error during getString", e)
                return null
            }
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

    // ---------- API fail-closed برای secretها ----------

    fun putSecret(ctx: Context, key: String, value: String): SecretResult =
        putString(ctx, key, value)

    fun getSecret(ctx: Context, key: String): String? =
        getString(ctx, key)

    fun hasSecret(ctx: Context, key: String): Boolean =
        prefs(ctx).getString(key, null) != null
}
