package com.pumpwatch.app.data

import android.os.SystemClock
import com.pumpwatch.app.engine.WhaleFlowResult

/**
 * 🚀 Sprint 14 (مرحله ۲ / Commit 5): زیرساخت Provenance و هویت دارایی
 * 🚀 Commit 80 (فاز ۲ — monotonic time): ageSec با elapsedRealtime
 *
 * اصل حاکم: هر عددی که کاربر می‌بیند باید بتواند به این پرسش‌ها پاسخ دهد:
 *  - از کدام منبع آمد؟ (provider واقعی، نه برچسب تبلیغاتی)
 *  - چه زمانی مشاهده شد و چقدر تازه است؟ (observedAt + freshness)
 *  - دربارهٔ کدام دارایی است؟ (هویت رسمی: chain + contract + pool + venue)
 *
 * این فایل فقط مدل + برچسب‌ساز است (pure)؛ سیم‌کشی UI در Commit بعدی.
 *
 * 🚀 Commit 80: ageSec با elapsedRealtime (monotonic).
 *
 * باگ قبلی: ageSec از System.currentTimeMillis() استفاده می‌کرد → اگر کاربر
 *   ساعت گوشی را جلو/عقب می‌برد یا NTP sync می‌شد، عدد age بی‌معنی می‌شد
 *   (حتی منفی!). freshnessLabel بر این اساس دروغ می‌گفت.
 *
 * حالا: ageSec اول elapsedRealtime را چک می‌کند (monotonic، unaffected by
 *   NTP/clock changes). اگر observedAtElapsedMs null بود (backward compat)،
 *   fallback به currentTimeMillis.
 */

// ---------- تازگی دادهٔ بازار ----------

enum class ServedFrom { NETWORK, MEM_CACHE, DISK_CACHE, UNKNOWN }

data class MarketMeta(
    val observedAtMs: Long,          // زمانی که داده واقعاً از شبکه گرفته شد (wall-clock)
    val servedFrom: ServedFrom,      // لایه‌ای که همین حالا پاسخ داد
    val itemCount: Int,
    val observedAtElapsedMs: Long? = SystemClock.elapsedRealtime()  // 🚀 Commit 80: monotonic
) {
    /**
     * 🚀 Commit 80: محاسبهٔ age با elapsedRealtime (monotonic).
     *
     * اگر observedAtElapsedMs موجود باشد (حالت عادی)، از آن استفاده می‌شود →
     *   تغییر ساعت گوشی/NTP sync روی نتیجه اثر نمی‌گذارد.
     * اگر null باشد (backward compat با دادهٔ قدیمی)، fallback به currentTimeMillis.
     *
     * @return age در ثانیه. هرگز منفی نمی‌شود.
     */
    fun ageSec(): Long {
        val elapsed = observedAtElapsedMs
        if (elapsed != null) {
            val now = SystemClock.elapsedRealtime()
            val ageMs = now - elapsed
            // محافظت: اگر به هر دلیلی منفی شد (مثلاً reboot)، صفر برگردان
            return if (ageMs >= 0) ageMs / 1000 else 0L
        }
        // backward compat fallback
        val ageMs = System.currentTimeMillis() - observedAtMs
        return if (ageMs >= 0) ageMs / 1000 else 0L
    }

    /** برچسب صادقانهٔ تازگی برای UI — هرگز «زنده» وقتی داده از دیسک می‌آید */
    fun freshnessLabel(): String = when {
        observedAtMs <= 0L -> "نامشخص"
        servedFrom == ServedFrom.DISK_CACHE -> "ذخیرهٔ آفلاین (${ageSec() / 60} دقیقه)"
        ageSec() <= 130 -> "زنده"
        ageSec() <= 1800 -> "کش (${ageSec() / 60} دقیقه)"
        else -> "مانده (${ageSec() / 60} دقیقه)"
    }

    companion object {
        /**
         * 🚀 Commit 80: constructor کمکی برای تست (elapsedRealtime قابل تنظیم).
         *
         * در تست نمی‌توانیم SystemClock.elapsedRealtime() را mock کنیم (Android API است)،
         * پس این constructor اجازه می‌دهد observedAtElapsedMs دلخواه بدهیم.
         */
        fun forTest(
            observedAtMs: Long,
            servedFrom: ServedFrom,
            itemCount: Int,
            observedAtElapsedMs: Long
        ): MarketMeta = MarketMeta(observedAtMs, servedFrom, itemCount, observedAtElapsedMs)
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
