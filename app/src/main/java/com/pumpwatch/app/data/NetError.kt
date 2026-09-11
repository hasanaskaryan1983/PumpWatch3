package com.pumpwatch.app.data

import android.util.Log
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * قدم ۵ بازبینی دوم: مدل خطای typed.
 * به‌جای قاطی‌شدن همهٔ خطاها در catch (_: Exception)،
 * هر خطا klass بندی می‌شود تا UI پیام درست بدهد و log قابل تشخیص باشد.
 */
sealed class NetError {
    object NetworkError : NetError()     // اینترنت/DNS/timeout
    object RateLimited : NetError()      // 429/418 یا RateLimitedException
    object InvalidPayload : NetError()   // پاسخ نامعتبر یا JSON خراب
    object EmptyData : NetError()        // پاسخ معتبر ولی خالی/ناکافی
    object Unknown : NetError()          // بقیه

    fun tag(): String = when (this) {
        NetworkError -> "NETWORK"
        RateLimited -> "RATE_LIMIT"
        InvalidPayload -> "PAYLOAD"
        EmptyData -> "EMPTY"
        Unknown -> "UNKNOWN"
    }
}

object NetErr {

    /** طبقه‌بندی استثنا به نوع خطا */
    fun classify(e: Throwable?): NetError = when {
        e == null -> NetError.Unknown
        e is RateLimitedException -> NetError.RateLimited
        e is SocketTimeoutException || e is UnknownHostException -> NetError.NetworkError
        e is HttpException -> when {
            e.code() == 429 || e.code() == 418 -> NetError.RateLimited
            e.code() in 500..599 -> NetError.NetworkError
            else -> NetError.InvalidPayload
        }
        e is com.google.gson.JsonSyntaxException -> NetError.InvalidPayload
        e is NoSuchElementException || e is IndexOutOfBoundsException -> NetError.InvalidPayload
        e is IOException -> {
            val m = e.message?.lowercase() ?: ""
            if (m.contains("429") || m.contains("rate")) NetError.RateLimited else NetError.NetworkError
        }
        else -> NetError.Unknown
    }

    /** پیام فارسی و قابل‌فهم برای کاربر */
    fun msg(err: NetError): String = when (err) {
        NetError.NetworkError -> "📡 خطای اتصال اینترنت. اتصال را بررسی کن و دوباره تلاش کن."
        NetError.RateLimited -> "⚠️ محدودیت نرخ درخواست سرور. لطفاً حدود ۱ دقیقه صبر کن و دوباره تلاش کن."
        NetError.InvalidPayload -> "⚠️ پاسخ سرور نامعتبر بود. کمی بعد دوباره تلاش کن."
        NetError.EmptyData -> "📭 دادهٔ معتبری برای این نماد دریافت نشد (پاسخ خالی)."
        NetError.Unknown -> "❓ خطای غیرمنتظره. دوباره تلاش کن."
    }

    fun msg(e: Throwable?): String = msg(classify(e))

    /**
     * لاگ یکنواص: نوع خطا + نام endpoint + symbol + علت.
     * این همان چیزی است که گزارش بازبینی خواسته بود.
     */
    fun log(tag: String, endpoint: String, symbol: String?, e: Throwable?) {
        val err = classify(e)
        Log.w(
            tag,
            "[${err.tag()}] endpoint=$endpoint symbol=${symbol ?: "-"} cause=${e?.javaClass?.simpleName}: ${e?.message}"
        )
    }

    /** برای پاسخ‌های معتبر ولی خالی (بدون استثنا) */
    fun logEmpty(tag: String, endpoint: String, symbol: String?) {
        Log.w(tag, "[${NetError.EmptyData.tag()}] endpoint=$endpoint symbol=${symbol ?: "-"} cause=empty_response")
    }
}
