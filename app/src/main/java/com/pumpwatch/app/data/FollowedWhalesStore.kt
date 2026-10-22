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

    private const val KEY = "followed_whales_encrypted"

    fun load(context: Context): List<FollowedWhale> {
        return try {
            val json = SecureStorage.getString(context, KEY)
            if (json.isNullOrEmpty()) {
                // Migration from old plaintext file
                val oldFile = java.io.File(context.filesDir, FILE_NAME)
                if (oldFile.exists()) {
                    val oldJson = oldFile.readText()
                    SecureStorage.putString(context, KEY, oldJson)
                    oldFile.delete()
                    val type = object : TypeToken<List<FollowedWhale>>() {}.type
                    return gson.fromJson(oldJson, type) ?: emptyList()
                }
                return emptyList()
            }
            val type = object : TypeToken<List<FollowedWhale>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, whales: List<FollowedWhale>) {
        try {
            SecureStorage.putString(context, KEY, gson.toJson(whales))
        } catch (_: Exception) {
            // خطای نوشتن نباید اپ را کرش کند
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
