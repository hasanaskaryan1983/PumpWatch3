package com.pumpwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

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

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<FollowedWhale> {
        return try {
            val json = file(context).readText()
            val type = object : TypeToken<List<FollowedWhale>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, whales: List<FollowedWhale>) {
        try {
            file(context).writeText(gson.toJson(whales))
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
