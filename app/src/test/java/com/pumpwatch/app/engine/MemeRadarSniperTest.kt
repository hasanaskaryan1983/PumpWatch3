package com.pumpwatch.app.engine

import com.pumpwatch.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class MemeRadarSniperTest {

    @Test
    fun sniper_mode_with_unknown_security_must_limit_score_and_add_warning() = runBlocking {
        // ساخت یک استخر که همه فیلترهای SNIPER را پاس کند
        val pool = GeckoPool(
            id = "solana_test_pool_123",
            type = "pool",
            attributes = GeckoPoolAttributes(
                priceUsd = "0.001",
                name = "TestMemeToken",
                createdAt = Instant.now().minus(10, ChronoUnit.HOURS).toString(), // ۱۰ ساعت پیش (بین ۰.۵ و ۱۶۸)
                fdvUsd = 500000.0,
                priceChange = GeckoPriceChange(h1 = 5.0, h6 = 10.0, h24 = 20.0),
                transactions = GeckoTxns(
                    h1 = GeckoBuysSells(buys = 10.0, sells = 5.0),
                    h6 = GeckoBuysSells(buys = 50.0, sells = 30.0),
                    h24 = GeckoBuysSells(buys = 200.0, sells = 100.0)
                ),
                volume = GeckoVolume(h1 = 5000.0, h6 = 20000.0, h24 = 50000.0), // > 10000
                reserveUsd = "50000" // > 5000
            ),
            relationships = GeckoRelationships(
                network = GeckoRel(GeckoRelData("solana")),
                dex = GeckoRel(GeckoRelData("raydium")),
                base_token = GeckoRel(GeckoRelData("")) // آدرس خالی = امنیت نامشخص (UNKNOWN)
            )
        )

        val result = MemeRadar.analyzeWithReason(pool, ScanPolicy.SNIPER)

        assertTrue("نتیجه باید Success باشد، اما Rejected شد (دلیل: ${(result as? MemeRadar.AnalyzeResult.Rejected)?.reason})", 
            result is MemeRadar.AnalyzeResult.Success)
        
        val signal = (result as MemeRadar.AnalyzeResult.Success).signal
        
        assertTrue("باید هشدار 'عدم تأیید امنیت' در لیست دلایل وجود داشته باشد. دلایل فعلی: ${signal.reasons}", 
            signal.reasons.any { it.contains("عدم تأیید امنیت") })
        
        assertTrue("امتیاز در حالت UNKNOWN نباید از ۵۰ بیشتر شود. امتیاز فعلی: ${signal.score}", 
            signal.score <= 50)
    }
}
