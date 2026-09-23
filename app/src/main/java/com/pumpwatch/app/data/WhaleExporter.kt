package com.pumpwatch.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.ui.FavWallet

/**
 * 🚀 Commit 56: Export/Import پروندهٔ نهنگ‌ها (JSON format).
 * کاربر می‌تواند لیست نهنگ‌های شکارشده را بکاپ بگیرد یا به دستگاه دیگر منتقل کند.
 */
object WhaleExporter {

    private val gson = Gson()

    /** export لیست نهنگ‌ها به JSON string */
    fun exportToFavorites(favorites: List<FavWallet>): String = gson.toJson(favorites)

    /** import لیست نهنگ‌ها از JSON string — dedupe خودکار */
    fun importFromJson(
        json: String,
        existing: List<FavWallet>
    ): ImportResult {
        return try {
            val imported: List<FavWallet>? = gson.fromJson(
                json,
                object : TypeToken<List<FavWallet>>() {}.type
            )
            if (imported == null) return ImportResult.Error("فایل خالی یا نامعتبر است")

            val existingAddrs = existing.map { it.addr }.toSet()
            val newWhales = imported.filter { it.addr !in existingAddrs }
            ImportResult.Success(newWhales.size, imported.size - newWhales.size)
        } catch (e: Exception) {
            ImportResult.Error("خطا در خواندن فایل: ${e.message}")
        }
    }

    sealed class ImportResult {
        data class Success(val added: Int, val duplicates: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}
