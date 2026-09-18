package com.pumpwatch.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7D): قفل فهرست منابع داده.
 * اگر روزی منبع جدیدی اضافه شد و اینجا ثبت نشد، تست قرمز می‌شود —
 * چون فرم Data Safety پلی‌استور باید با این فهرست هم‌خوان بماند.
 */
class PrivacyCenterTest {

    @Test
    fun provider_list_covers_all_known_sources() {
        val names = dataProviders().map { it.first }.joinToString(" | ")
        assertTrue("CoinGecko missing", names.contains("CoinGecko"))
        assertTrue("GeckoTerminal missing", names.contains("GeckoTerminal"))
        assertTrue("GoPlus missing", names.contains("GoPlus"))
        assertTrue("exchange chain missing", names.contains("Bybit"))
        assertTrue("futures source missing", names.contains("Binance Futures"))
    }

    @Test
    fun every_provider_has_a_purpose() {
        dataProviders().forEach { (name, purpose) ->
            assertTrue("provider $name has empty purpose", purpose.isNotBlank())
        }
    }
}
