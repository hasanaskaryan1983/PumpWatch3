package com.pumpwatch.app.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 🔒 SecureStorage - wrapper برای EncryptedSharedPreferences با پشتیبانی Fail-Closed (S3b)
 * 
 * قانون Fail-Closed:
 * ۱. در صورت خرابی Keystore، برنامه کرش نمی‌کند.
 * ۲. هرگز به SharedPreferences معمولی (ناامن) fallback نمی‌شود.
 * ۳. عملیات خواندن مقدار پیش‌فرض ایمن (null/empty) برمی‌گرداند.
 * ۴. عملیات نوشتن شکست می‌خورد و false برمی‌گرداند (بدون ذخیره ناامن).
 */
object SecureStorage {

    private const val PREFERENCES_NAME = "encrypted_preferences"
    private const val TAG = "SecureStorage"

    private fun getSafeEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 🚀 S3b: Fail-closed. خطا ثبت می‌شود، اما fallback ناامن انجام نمی‌شود.
            Log.e(TAG, "Keystore unavailable or crypto error. Failing closed.", e)
            null
        }
    }

    fun putStringList(context: Context, key: String, value: List<String>): Boolean {
        return try {
            val json = value.joinToString("|||")
            getSafeEncryptedPrefs(context)?.edit()?.putString(key, json)?.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write failed for $key (fail-closed)", e)
            false
        }
    }

    fun getStringList(context: Context, key: String): List<String> {
        return try {
            val json = getSafeEncryptedPrefs(context)?.getString(key, null)
            if (json.isNullOrEmpty()) emptyList() else json.split("|||").filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Read failed for $key (fail-closed)", e)
            emptyList()
        }
    }

    fun putString(context: Context, key: String, value: String): Boolean {
        return try {
            getSafeEncryptedPrefs(context)?.edit()?.putString(key, value)?.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write failed for $key (fail-closed)", e)
            false
        }
    }

    fun getString(context: Context, key: String, default: String? = null): String? {
        return try {
            getSafeEncryptedPrefs(context)?.getString(key, default)
        } catch (e: Exception) {
            Log.e(TAG, "Read failed for $key (fail-closed)", e)
            default
        }
    }

    fun remove(context: Context, key: String): Boolean {
        return try {
            getSafeEncryptedPrefs(context)?.edit()?.remove(key)?.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Remove failed for $key (fail-closed)", e)
            false
        }
    }

    fun clear(context: Context): Boolean {
        return try {
            getSafeEncryptedPrefs(context)?.edit()?.clear()?.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clear failed (fail-closed)", e)
            false
        }
    }

    fun wipeAll(context: Context) {
        clear(context)
    }
}
