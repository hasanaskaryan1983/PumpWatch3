package com.pumpwatch.app.engine

import com.pumpwatch.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeRadarSniperTest {

    @Test
    fun sniper_mode_with_unknown_security_must_limit_score_and_add_warning() = runBlocking {
        // Arrange: ساخت یک استخر با آدرس قرارداد خالی برای شبیه‌سازی وضعیت UNKNOWN
        val pool = GeckoPool(
            id = "solana_test_pool_123",
            type = "pool",
            attributes = GeckoPoolAttributes(
                priceUsd = "0.001",
                name = "TestMemeToken",
                createdAt = "2023-10-27T10:00:00Z",
                fdvUsd = 50000.0,
                priceChange = null,
                transactions = null,
                volume = null,
                reserveUsd = "10000"
            ),
            relationships = GeckoRelationships(
                network = GeckoRel(GeckoRelData("solana")),
                dex = GeckoRel(GeckoRelData("raydium")),
                base_token = GeckoRel(GeckoRelData("")) // آدرس خالی = امنیت نامشخص
            )
        )

        // Act: فراخوانی تابع با سیاست SNIPER
        val result = MemeRadar.analyzeWithReason(pool, ScanPolicy.SNIPER)

        // Assert
        assertTrue("نتیجه باید در حالت SNIPER برای UNKNOWN از نوع Success باشد", 
            result is MemeRadar.AnalyzeResult.Success)
        
        val signal = (result as MemeRadar.AnalyzeResult.Success).signal
        
        assertTrue("باید هشدار 'عدم تأیید امنیت' در لیست دلایل وجود داشته باشد", 
            signal.reasons.any { it.contains("عدم تأیید امنیت") })
        
        assertTrue("امتیاز در حالت UNKNOWN نباید از ۵۰ بیشتر شود. امتیاز فعلی: ${signal.score}", 
            signal.score <= 50)
    }
}
