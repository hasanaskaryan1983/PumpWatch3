package com.pumpwatch.app.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * \U+1F512 SecureStorage - wrapper برای EncryptedSharedPreferences
 * 
 * استفاده:
 * - همهٔ داده‌های حساس (آدرس‌های کیف‌پول، توکن‌ها، کلیدها) باید اینجا ذخیره شوند
 * - migration از SharedPreferences قدیمی در خود Storeها انجام می‌شود
 */
object SecureStorage {
    
    private const val PREFERENCES_NAME = "encrypted_preferences"
    
    private fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            getMasterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * ذخیرهٔ لیست رشته‌ها به‌صورت JSON
     */
    fun putStringList(context: Context, key: String, value: List<String>) {
        val json = value.joinToString("|||")
        getEncryptedPrefs(context).edit().putString(key, json).apply()
    }
    
    /**
     * خواندن لیست رشته‌ها از JSON
     */
    fun getStringList(context: Context, key: String): List<String> {
        val json = getEncryptedPrefs(context).getString(key, null) ?: return emptyList()
        return if (json.isEmpty()) emptyList()
        else json.split("|||").filter { it.isNotEmpty() }
    }
    
    /**
     * ذخیره رشتهٔ ساده
     */
    fun putString(context: Context, key: String, value: String) {
        getEncryptedPrefs(context).edit().putString(key, value).apply()
    }
    
    /**
     * خواندن رشتهٔ ساده
     */
    fun getString(context: Context, key: String, default: String? = null): String? {
        return getEncryptedPrefs(context).getString(key, default)
    }
    
    /**
     * حذف یک key
     */
    fun remove(context: Context, key: String) {
        getEncryptedPrefs(context).edit().remove(key).apply()
    }
    
    /**
     * پاک کردن همه
     */
    fun clear(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
    }

    /**
     * پاک‌سازی کامل (مورد استفاده در Privacy Center)
     */
    fun wipeAll(context: Context) {
        clear(context)
    }
}
