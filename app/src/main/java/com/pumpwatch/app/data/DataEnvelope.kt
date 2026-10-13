package com.pumpwatch.app.data

import java.util.concurrent.TimeUnit

/**
 * وضعیت اعتبار داده
 * 
 * طبق پیشنهاد P0 مشترک: تفکیک داده مفقود از صفر/خالی/خطا
 */
enum class DataStatus {
    SUCCESS,          // داده با موفقیت و کامل دریافت شد
    EMPTY_VALID,      // درخواست موفق بود اما داده‌ای وجود نداشت (مثلاً هیچ نهنگی پیدا نشد)
    PARTIAL,          // بخشی از داده‌ها دریافت شد (مثلاً ۲ از ۳ منبع)
    STALE,            // داده از کش خوانده شده و از حداکثر سن مجاز گذشته است
    UNAVAILABLE,      // منبع داده در دسترس نیست (مثلاً API محدودیت دارد)
    FAILURE           // خطا در دریافت یا پردازش داده
}

/**
 * منشأ ارائه داده
 * 
 * طبق پیشنهاد P0 مشترک: freshness و provenance
 */
enum class ServedFrom {
    NETWORK,          // داده مستقیماً از شبکه دریافت شد
    DISK_CACHE,       // داده از کش دیسک خوانده شد
    MEMORY_CACHE,     // داده از کش حافظه خوانده شد
    UNKNOWN           // منشأ نامشخص
}

/**
 * پوشش استاندارد برای بسته‌بندی داده‌ها همراه با متادیتای تازگی و منشأ
 * 
 * T: نوع داده اصلی (مثلاً List<CoinMarket>)
 * 
 * این کلاس پیاده‌سازی پیشنهاد P0 مشترک برای:
 * - envelope اجباری data + source + observedAt + receivedAt + servedFrom + age + status
 * - stale gate قبل از score/alert/Paper/backtest
 * - تفکیک داده مفقود از صفر/خالی/خطا
 */
data class DataEnvelope<T>(
    val data: T?,
    val source: String,                  // نام منبع (مثلاً "coingecko", "binance")
    val observedAtMs: Long,              // زمانی که داده در منبع تولید/مشاهده شده
    val receivedAtMs: Long = System.currentTimeMillis(), // زمانی که توسط اپ دریافت شده
    val servedFrom: ServedFrom = ServedFrom.NETWORK,
    val status: DataStatus = if (data != null) DataStatus.SUCCESS else DataStatus.EMPTY_VALID
) {
    /**
     * محاسبه سن داده به ثانیه (بر اساس زمان مشاهده)
     */
    fun ageSec(): Long {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - observedAtMs)
    }

    /**
     * بررسی اینکه آیا داده کهنه (stale) است یا خیر
     * 
     * @param maxAgeSec حداکثر سن مجاز به ثانیه (پیش‌فرض ۵ دقیقه = ۳۰ ثانیه)
     * @return true اگر داده کهنه باشد
     */
    fun isStale(maxAgeSec: Long = 300L): Boolean {
        return ageSec() > maxAgeSec || status == DataStatus.STALE
    }

    /**
     * Stale Gate: جلوگیری از استفاده از داده‌های کهنه در منطق حساس
     * 
     * این متد قبل از تولید سیگنال، هشدار، یا اجرای Paper Trading فراخوانی می‌شود.
     * اگر داده کهنه باشد، استثنا پرتاب می‌کند تا سیستم بداند نباید اقدام کند.
     * 
     * @param maxAgeSec حداکثر سن مجاز به ثانیه
     * @return داده اصلی (اگر تازه باشد)
     * @throws StaleDataException اگر داده کهنه باشد
     * @throws NoDataException اگر داده null باشد
     */
    fun requireFresh(maxAgeSec: Long = 300L): T {
        if (isStale(maxAgeSec)) {
            throw StaleDataException(
                "Data is stale. Age: ${ageSec()}s, Max allowed: ${maxAgeSec}s, Source: $source"
            )
        }
        return data ?: throw NoDataException("Data is null for source: $source")
    }
    
    /**
     * ایجاد یک کپی از Envelope با وضعیت STALE
     * 
     * وقتی داده از کش خوانده می‌شود و سن آن از حد مجاز گذشته،
     * این متد برای علامت‌گذاری داده به عنوان stale استفاده می‌شود.
     */
    fun markAsStale(): DataEnvelope<T> {
        return this.copy(status = DataStatus.STALE)
    }
    
    /**
     * بررسی وضعیت داده برای نمایش در UI
     * 
     * @return متن توضیحی وضعیت داده برای نمایش به کاربر
     */
    fun statusDescription(): String {
        return when (status) {
            DataStatus.SUCCESS -> "داده تازه از $source"
            DataStatus.EMPTY_VALID -> "جستجو انجام شد، داده‌ای یافت نشد"
            DataStatus.PARTIAL -> "بخشی از داده‌ها از $source دریافت شد"
            DataStatus.STALE -> "داده کهنه (${ageSec()} ثانیه پیش)"
            DataStatus.UNAVAILABLE -> "$source در دسترس نیست"
            DataStatus.FAILURE -> "خطا در دریافت داده از $source"
        }
    }
}

// استثناهای اختصاصی برای مدیریت خطاهای داده

/**
 * استثنا برای داده کهنه
 * 
 * وقتی داده از حداکثر سن مجاز گذشته باشد، این استثنا پرتاب می‌شود
 * تا سیستم بداند نباید بر اساس این داده تصمیم بگیرد.
 */
class StaleDataException(message: String) : Exception(message)

/**
 * استثنا برای داده مفقود
 * 
 * وقتی داده null باشد، این استثنا پرتاب می‌شود.
 * این با EMPTY_VALID متفاوت است: EMPTY_VALID یعنی "جستجو کردیم، چیزی نبود" (معتبر)،
 * اما NoDataException یعنی "داده باید وجود می‌داشت ولی نیست" (خطا).
 */
class NoDataException(message: String) : Exception(message)

/**
 * توابع کمکی برای ساخت آسان DataEnvelope
 */
object DataEnvelopeFactory {
    
    /**
     * ساخت Envelope موفق از داده شبکه
     */
    fun <T> success(
        data: T,
        source: String,
        observedAtMs: Long = System.currentTimeMillis()
    ): DataEnvelope<T> {
        return DataEnvelope(
            data = data,
            source = source,
            observedAtMs = observedAtMs,
            receivedAtMs = System.currentTimeMillis(),
            servedFrom = ServedFrom.NETWORK,
            status = DataStatus.SUCCESS
        )
    }
    
    /**
     * ساخت Envelope از کش حافظه
     */
    fun <T> fromMemoryCache(
        data: T,
        source: String,
        observedAtMs: Long
    ): DataEnvelope<T> {
        return DataEnvelope(
            data = data,
            source = source,
            observedAtMs = observedAtMs,
            receivedAtMs = System.currentTimeMillis(),
            servedFrom = ServedFrom.MEMORY_CACHE,
            status = DataStatus.SUCCESS
        )
    }
    
    /**
     * ساخت Envelope از کش دیسک
     */
    fun <T> fromDiskCache(
        data: T,
        source: String,
        observedAtMs: Long
    ): DataEnvelope<T> {
        return DataEnvelope(
            data = data,
            source = source,
            observedAtMs = observedAtMs,
            receivedAtMs = System.currentTimeMillis(),
            servedFrom = ServedFrom.DISK_CACHE,
            status = DataStatus.SUCCESS
        )
    }
    
    /**
     * ساخت Envelope برای داده خالی معتبر
     */
    fun <T> emptyValid(
        source: String
    ): DataEnvelope<T> {
        return DataEnvelope(
            data = null,
            source = source,
            observedAtMs = System.currentTimeMillis(),
            receivedAtMs = System.currentTimeMillis(),
            servedFrom = ServedFrom.NETWORK,
            status = DataStatus.EMPTY_VALID
        )
    }
    
    /**
     * ساخت Envelope برای خطا
     */
    fun <T> failure(
        source: String,
        observedAtMs: Long = System.currentTimeMillis()
    ): DataEnvelope<T> {
        return DataEnvelope(
            data = null,
            source = source,
            observedAtMs = observedAtMs,
            receivedAtMs = System.currentTimeMillis(),
            servedFrom = ServedFrom.NETWORK,
            status = DataStatus.FAILURE
        )
    }
}
