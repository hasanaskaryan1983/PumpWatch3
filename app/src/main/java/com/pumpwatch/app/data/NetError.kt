package com.pumpwatch.app.data

import android.util.Log
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.security.cert.CertificateException

/**
 * قدم ۵ بازبینی دوم: مدل خطای typed.
 * به‌جای قاطی‌شدن همهٔ خطاها در catch (_: Exception)،
 * هر خطا klass بندی می‌شود تا UI پیام درست بدهد و log قابل تشخیص باشد.
 *
 * 🚀 Commit 77 (فاز ۲ — بند ۵ CONSTITUTION): SSL coverage + بهبود classify.
 *
 * قبلاً: SSL exceptions (expired cert, untrusted, handshake failure)
 *   به `NetworkError` می‌افتادند → UI پیام اشتباه می‌داد («اتصال اینترنت
 *   را بررسی کن») در حالی که مشکل امنیت اتصال بود.
 *
 * حالا:
 *   - `SslError` type جدید با پیام فارسی اختصاصی
 *   - `SSLException` و `CertificateException` → `SslError`
 *   - بهبود substring matching در `IOException` (چک "ssl"/"cert"/"handshake")
 *   - log با try-catch روی `Log.w` (در JVM unit test crash نمی‌کند)
 */
sealed class NetError {
    object NetworkError : NetError()     // اینترنت/DNS/timeout
    object SslError : NetError()         // SSL/certificate/handshake failure
    object RateLimited : NetError()      // 429/418 یا RateLimitedException
    object InvalidPayload : NetError()   // پاسخ نامعتبر یا JSON خراب
    object EmptyData : NetError()        // پاسخ معتبر ولی خالی/ناکافی
    object Unknown : NetError()          // بقیه

    fun tag(): String = when (this) {
        NetworkError -> "NETWORK"
        SslError -> "SSL"
        RateLimited -> "RATE_LIMIT"
        InvalidPayload -> "PAYLOAD"
        EmptyData -> "EMPTY"
        Unknown -> "UNKNOWN"
    }
}

object NetErr {

    /**
     * طبقه‌بندی استثنا به نوع خطا.
     *
     * 🚀 Commit 77: SSL coverage اضافه شد.
     * ترتیب چک مهم است (SSL قبل از IOException، چون SSLException زیر IOException است).
     */
    fun classify(e: Throwable?): NetError = when {
        e == null -> NetError.Unknown
        e is RateLimitedException -> NetError.RateLimited
        // 🚀 Commit 77: SSL coverage (قبل از IOException چک شود)
        e is SSLException -> NetError.SslError
        e is CertificateException -> NetError.SslError
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
            // 🚀 Commit 77: بهبود substring matching — چک SSL keywords هم
            if (m.contains("ssl") || m.contains("certificate") || m.contains("handshake") ||
                m.contains("cert ") || m.contains("trust")) {
                NetError.SslError
            } else if (m.contains("429") || m.contains("rate")) {
                NetError.RateLimited
            } else {
                NetError.NetworkError
            }
        }
        else -> NetError.Unknown
    }

    /** پیام فارسی و قابل‌فهم برای کاربر */
    fun msg(err: NetError): String = when (err) {
        NetError.NetworkError -> "📡 خطای اتصال اینترنت. اتصال را بررسی کن و دوباره تلاش کن."
        NetError.SslError -> "🔒 خطای امنیت اتصال. ساعت گوشی و وضعیت SSL سرور را بررسی کن."
        NetError.RateLimited -> "⚠️ محدودیت نرخ درخواست سرور. لطفاً حدود ۱ دقیقه صبر کن و دوباره تلاش کن."
        NetError.InvalidPayload -> "⚠️ پاسخ سرور نامعتبر بود. کمی بعد دوباره تلاش کن."
        NetError.EmptyData -> "📭 دادهٔ معتبری برای این نماد دریافت نشد (پاسخ خالی)."
        NetError.Unknown -> "❓ خطای غیرمنتظره. دوباره تلاش کن."
    }

    fun msg(e: Throwable?): String = msg(classify(e))

    /**
     * لاگ یکنواخت: نوع خطا + نام endpoint + symbol + علت.
     * این همان چیزی است که گزارش بازبینی خواسته بود.
     *
     * 🚀 Commit 77: try-catch روی `Log.w` (در JVM unit test crash نمی‌کند).
     */
    fun log(tag: String, endpoint: String, symbol: String?, e: Throwable?) {
        val err = classify(e)
        val logMsg = "[${err.tag()}] endpoint=$endpoint symbol=${symbol ?: "-"} cause=${e?.javaClass?.simpleName}: ${e?.message}"
        try {
            Log.w(tag, logMsg)
        } catch (_: Throwable) {
            // Log در JVM unit test در دسترس نیست — fallback به println
            println("$tag: $logMsg")
        }
    }

    /** برای پاسخ‌های معتبر ولی خالی (بدون استثنا) */
    fun logEmpty(tag: String, endpoint: String, symbol: String?) {
        val logMsg = "[${NetError.EmptyData.tag()}] endpoint=$endpoint symbol=${symbol ?: "-"} cause=empty_response"
        try {
            Log.w(tag, logMsg)
        } catch (_: Throwable) {
            println("$tag: $logMsg")
        }
    }
}
