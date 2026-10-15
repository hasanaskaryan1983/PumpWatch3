package com.pumpwatch.app.testing

import com.pumpwatch.app.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 🚀 P1-4: تست‌های هم‌زمانی (Concurrency Tests)
 *
 * این تست‌ها اثبات می‌کنند که الگوی SingleFlight در ApiClient
 * جلوی اجرای موازی درخواست‌های تکراری را می‌گیرد:
 *   - وقتی ۵ کوروتین هم‌زمان یک درخواست را صدا می‌زنند،
 *     بلوک اجراشونده فقط ۱ بار فراخوانی می‌شود (نه ۵ بار).
 *   - خطاها به همهٔ منتظرها به‌درستی منتشر می‌شوند.
 */
class ConcurrencyTest {

    /**
     * تست اصلی: ۵ کوروتین هم‌زمان → فقط ۱ اجرای واقعی.
     * این همان تضمینی است که از Rate-Limit شدن توسط CoinGecko جلوگیری می‌کند.
     */
    @Test
    fun `SingleFlight prevents concurrent execution of same request`() = runBlocking {
        val singleFlight = ApiClient.SingleFlight<String>()
        val executionCount = AtomicInteger(0)

        // شبیه‌سازی ۵ درخواست هم‌زمان (مثل باز شدن هم‌زمان چند تب)
        val results = (1..5).map {
            async(Dispatchers.Default) {
                singleFlight.joinOrRun {
                    executionCount.incrementAndGet()
                    delay(100) // شبیه‌سازی تأخیر شبکه
                    "SuccessResult"
                }
            }
        }.awaitAll()

        // همه ۵ کوروتین باید دقیقاً همان نتیجه را دریافت کنند
        assertEquals(5, results.size)
        assertTrue("All results must be identical", results.all { it == "SuccessResult" })

        // بلوک اجراشونده باید فقط و فقط ۱ بار فراخوانی شده باشد
        assertEquals(
            "Block should execute exactly once despite 5 concurrent callers",
            1,
            executionCount.get()
        )
    }

    /**
     * تست خطا: اگر اولین درخواست شکست بخورد، همه منتظرها همان خطا را دریافت می‌کنند
     * (نه اینکه هرکدام جداگانه دوباره تلاش کنند).
     */
    @Test
    fun `SingleFlight propagates exceptions to all waiting coroutines`() = runBlocking {
        val singleFlight = ApiClient.SingleFlight<String>()
        val executionCount = AtomicInteger(0)

        val results = (1..3).map {
            async(Dispatchers.Default) {
                runCatching {
                    singleFlight.joinOrRun {
                        executionCount.incrementAndGet()
                        delay(50)
                        throw RuntimeException("NetworkError")
                    }
                }
            }
        }.awaitAll()

        // بلوک فقط ۱ بار اجرا می‌شود
        assertEquals("Block should execute exactly once even on failure", 1, executionCount.get())

        // همه منتظرها باید همان خطا را دریافت کنند
        assertTrue("All results must be failures", results.all { it.isFailure })
        assertTrue(
            "All exceptions must have the same message",
            results.all { it.exceptionOrNull()?.message == "NetworkError" }
        )
    }

    /**
     * تست توالی: بعد از اتمام یک درخواست، درخواست بعدی باید مستقل اجرا شود.
     * این تضمین می‌کند که SingleFlight برای درخواست‌های بعدی قفل نمی‌کند.
     */
    @Test
    fun `SingleFlight allows sequential requests after completion`() = runBlocking {
        val singleFlight = ApiClient.SingleFlight<String>()
        val executionCount = AtomicInteger(0)

        // درخواست اول
        val first = singleFlight.joinOrRun {
            executionCount.incrementAndGet()
            delay(50)
            "First"
        }

        // درخواست دوم (بعد از اتمام اولی)
        val second = singleFlight.joinOrRun {
            executionCount.incrementAndGet()
            delay(50)
            "Second"
        }

        assertEquals("First", first)
        assertEquals("Second", second)
        assertEquals("Both sequential requests should execute", 2, executionCount.get())
    }
}
