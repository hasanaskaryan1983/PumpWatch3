package com.pumpwatch.app.data

import com.pumpwatch.app.engine.WhaleFlowResult

/**
 * 🚀 Sprint 14 (مرحله ۲ / Commit 5): زیرساخت Provenance و هویت دارایی
 *
 * اصل حاکم: هر عددی که کاربر می‌بیند باید بتواند به این پرسش‌ها پاسخ دهد:
 *  - از کدام منبع آمد؟ (provider واقعی، نه برچسب تبلیغاتی)
 *  - چه زمانی مشاهده شد و چقدر تازه است؟ (observedAt + freshness)
 *  - دربارهٔ کدام دارایی است؟ (هویت رسمی: chain + contract + pool + venue)
 *
 * این فایل فقط مدل + برچسب‌ساز است (pure)؛ سیم‌کشی UI در Commit بعدی.
 */

// ---------- تازگی دادهٔ بازار ----------

enum class ServedFrom { NETWORK, MEM_CACHE, DISK_CACHE, UNKNOWN }

data class MarketMeta(
    val observedAtMs: Long,          // زمانی که داده واقعاً از شبکه گرفته شد
    val servedFrom: ServedFrom,      // لایه‌ای که همین حالا پاسخ داد
    val itemCount: Int
) {
    fun ageSec(): Long = (System.currentTimeMillis() - observedAtMs) / 1000

    /** برچسب صادقانهٔ تازگی برای UI — هرگز «زنده» وقتی داده از دیسک می‌آید */
    fun freshnessLabel(): String = when {
        observedAtMs <= 0L -> "نامشخص"
        servedFrom == ServedFrom.DISK_CACHE -> "ذخیرهٔ آفلاین (${ageSec() / 60} دقیقه)"
        ageSec() <= 130 -> "زنده"
        ageSec() <= 1800 -> "کش (${ageSec() / 60} دقیقه)"
        else -> "مانده (${ageSec() / 60} دقیقه)"
    }
}

// ---------- برچسب منبع واقعی ----------

/** برچسب خوانا برای منبع جریان نهنگ‌ها — جایگزین «aggTrades» سخت‌کدشده */
fun WhaleFlowResult.sourceLabel(): String = when (source) {
    "BINANCE", "BINANCE_AGG" -> "Binance"
    "BYBIT" -> "Bybit"
    "OKX" -> "OKX"
    "GATE" -> "Gate.io"
    "GECKO_DEX" -> "DEX آن‌چین"
    else -> source
}

/** برچسب خوانا برای منبع کندل‌ها */
fun klineSourceLabel(src: String): String = when (src) {
    "BYBIT" -> "Bybit"
    "OKX" -> "OKX"
    "GATE" -> "Gate.io"
    "UNKNOWN" -> "نامشخص"
    else -> src
}

// ---------- هویت رسمی دارایی ----------

/**
 * هویت رسمی یک دارایی. ticker فقط برچسب نمایشی است؛
 * کلید یکتا = chain + contract + pool + venue (درس P0#9:
 * دو توکن با ticker یکسان روی دو chain، دو دارایی متفاوت‌اند).
 */
data class AssetId(
    val chain: String?,
    val contract: String?,
    val pool: String?,
    val venue: String?,
    val ticker: String? = null
) {
    fun canonicalKey(): String = buildString {
        append(chain?.lowercase() ?: "-"); append('|')
        append(contract?.lowercase() ?: "-"); append('|')
        append(pool?.lowercase() ?: "-"); append('|')
        append(venue?.lowercase() ?: "-")
    }

    /** کوین بومی زنجیره (بدون contract مثل BTC/SOL) */
    fun isNative(): Boolean = contract.isNullOrBlank()

    fun displayLabel(): String = ticker
        ?: contract?.let { "${it.take(4)}...${it.takeLast(4)}" }
        ?: pool?.let { "${it.take(4)}...${it.takeLast(4)}" }
        ?: "?"
}
