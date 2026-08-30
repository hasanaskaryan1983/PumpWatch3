package com.pumpwatch.app.engine

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.pumpwatch.app.data.ScanApi
// توجه: پکیج مدل‌های خود را اینجا دقیق چک کنید
import com.pumpwatch.app.data.model.Market 

/**
 * BatchScanner Pro:
 * دارای قابلیت موازی‌سازی، مدیریت نرخ درخواست (Rate Limiting) و سیستم تلاش مجدد (Retry Mechanism).
 */
object BatchScanner {
    private const val TAG = "BatchScannerPro"

    // اجازه حداکثر 6 درخواست همزمان برای تعادل بین سرعت و امنیت (عدم مسدود شدن)
    private val semaphore = Semaphore(6)

    // لیست استیبل‌ها برای فیلتر سریع
    private val STABLES = setOf("USDT", "USDC", "DAI", "FDUSD", "TUSD", "BUSD")

    suspend fun scan(mode: String, limit: Int = 100): List<AnalysisResult> {
        return try {
            Log.d(TAG, "🚀 Starting high-performance scan: $mode")
            
            // ۱. بارگذاری بازارها
            val markets = loadMarkets(mode)

            // ۲. فیلترینگ هوشمند
            val candidates = markets
                .filter { market ->
                    val isStable = STABLES.any { stable -> 
                        market.symbol.contains(stable, ignoreCase = true) 
                    }
                    val hasVolume = market.volume > 500.0 // آستانه حجم قابل تنظیم
                    !isStable && hasVolume
                }
                .sortedByDescending { quickScore(it) }
                .take(limit)

            Log.d(TAG, "🎯 Found ${candidates.size} candidates. Launching parallel engines...")

            // ۳. اجرای موازی با مدیریت خطا و تلاش مجدد
            coroutineScope {
                candidates.map { token ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            // فراخوانی متد با قابلیت Retry
                            analyzeWithRetry(token, maxRetries = 3)
                        }
                    }
                }.awaitAll()
            }
            .filterNotNull()
            .sortedByDescending { it.score }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical failure in BatchScanner: ${e.message}")
            emptyList()
        }
    }

    /**
     * متد اصلی تحلیل با مکانیزم Retry
     * اگر درخواست با خطا مواجه شود، تا ۳ بار تلاش می‌کند.
     */
    private suspend fun analyzeWithRetry(token: Market, maxRetries: Int): AnalysisResult? {
        var currentAttempt = 0
        var lastException: Exception? = null

        while (currentAttempt < maxRetries) {
            try {
                // اجرای اصلی تحلیل
                return SignalEngine.analyze(token)
            } catch (e: Exception) {
                currentAttempt++
                lastException = e
                Log.w(TAG, "⚠️ Attempt $currentAttempt failed for ${token.symbol}: ${e.message}")
                
                if (currentAttempt < maxRetries) {
                    // ایجاد یک وقفه کوتاه و تصادفی بین تلاش‌ها برای جلوگیری از فشار دوباره
                    delay(500L * currentAttempt) 
                }
            }
        }

        Log.e(TAG, "🚫 All $maxRetries attempts failed for ${token.symbol}. Error: ${lastException?.message}")
        return null
    }

    private suspend fun loadMarkets(mode: String): List<Market> {
        return try {
            ScanApi.getMarkets(mode)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading markets: ${e.message}")
            emptyList()
        }
    }

    private fun quickScore(market: Market): Double {
        // فرمول امتیازدهی سریع برای اولویت‌بندی (مثال: ترکیب حجم و قیمت)
        return market.volume * 0.01 
    }
}
