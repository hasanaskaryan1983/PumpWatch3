package com.pumpwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import com.pumpwatch.app.store.SecureStorage

/**
 * 🚀 Commit 93: ذخیره نهنگ‌های دنبال‌شده
 *
 * این کلاس لیست نهنگ‌هایی که کاربر دنبال می‌کند را در فایل JSON ذخیره می‌کند.
 * هر نهنگ شامل آدرس، نماد، و تاریخ دنبال شدن است.
 */

data class FollowedWhale(
    val address: String,
    val symbol: String,
    val followedAt: Long = System.currentTimeMillis(),
    val alertThreshold: Double = 10_000.0 // آستانه آلرت به دلار
)

object FollowedWhalesStore {

    private const val FILE_NAME = "followed_whales.json"
    private val gson = Gson()

    // 🚀 S4: متغیرهای حالت برای الگوی fail-closed و ایمنی رشته‌ای
    @Volatile
    private var loaded = false

    @Volatile
    var lastLoadFailed = false

    private val cache = mutableListOf<FollowedWhale>()
    private val lock = Any()

    private const val KEY = "followed_whales_encrypted"

    fun load(context: Context): List<FollowedWhale> {
        synchronized(lock) {
            if (loaded) return cache.toList()

            lastLoadFailed = false
            cache.clear()

            try {
                var json = SecureStorage.getString(context, KEY)
                
                // Migration from old plaintext file (ایمن‌سازی شده)
                if (json.isNullOrEmpty()) {
                    val oldFile = java.io.File(context.filesDir, FILE_NAME)
                    if (oldFile.exists()) {
                        val oldJson = oldFile.readText()
                        SecureStorage.putString(context, KEY, oldJson)
                        oldFile.delete()
                        json = oldJson
                    }
                }

                if (!json.isNullOrEmpty()) {
                    val type = object : TypeToken<List<FollowedWhale>>() {}.type
                    val parsed: List<FollowedWhale>? = gson.fromJson(json, type)
                    if (parsed != null) {
                        cache.addAll(parsed)
                    }
                }
            } catch (e: Exception) {
                // 🚀 S4: الگوی fail-closed: ثبت خطا و بازگشت لیست خالی
                lastLoadFailed = true
            }

            loaded = true
            return cache.toList()
        }
    }

    fun save(context: Context, whales: List<FollowedWhale>) {
        synchronized(lock) {
            try {
                SecureStorage.putString(context, KEY, gson.toJson(whales))
                // به‌روزرسانی کش پس از ذخیره موفق
                cache.clear()
                cache.addAll(whales)
            } catch (e: Exception) {
                // 🚀 S4: جلوگیری از کرش هنگام ذخیره‌سازی (fail-closed)
            }
        }
    }

    fun addWhale(context: Context, whale: FollowedWhale) {
        val list = load(context).toMutableList()
        if (list.none { it.address == whale.address }) {
            list.add(whale)
            save(context, list)
        }
    }

    fun removeWhale(context: Context, address: String) {
        val list = load(context).filter { it.address != address }
        save(context, list)
    }

    fun isFollowing(context: Context, address: String): Boolean {
        return load(context).any { it.address == address }
    }

    fun updateAlertThreshold(context: Context, address: String, threshold: Double) {
        val list = load(context).map {
            if (it.address == address) it.copy(alertThreshold = threshold) else it
        }
        save(context, list)
    }
}
