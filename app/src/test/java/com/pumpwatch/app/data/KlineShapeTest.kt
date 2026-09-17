package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۱): تست رگرسیون شکل آرایهٔ کندل.
 * درس C1: آرایهٔ ۶ عضوی + مصرف‌کنندهٔ k[6] = مرگ بی‌صدای کل اسکنر.
 * این تست تضمین می‌کند عضو هفتم (closeTime) همیشه hadir است.
 */
class KlineShapeTest {

    @Test
    fun kline_array_has_seven_members_with_close_time() {
        val c = BinanceCandle(
            time = 1_700_000_000_000L,
            open = 1.0, high = 2.0, low = 0.5, close = 1.5, volume = 10.0
        )
        val arr = BinanceClient.KlineCompat.toJsonArray(c, 3_600_000L)
        assertEquals("kline array must have 7 members", 7, arr.size())
        assertEquals(1_700_000_000_000L, arr[0].asLong)
        assertEquals(1.5, arr[4].asDouble, 0.0)
        assertEquals("closeTime = openTime + step", 1_700_003_600_000L, arr[6].asLong)
    }

    @Test
    fun interval_ms_covers_used_timeframes() {
        assertEquals(60_000L, BinanceClient.KlineCompat.intervalMs("1m"))
        assertEquals(900_000L, BinanceClient.KlineCompat.intervalMs("15m"))
        assertEquals(3_600_000L, BinanceClient.KlineCompat.intervalMs("1h"))
        assertEquals(14_400_000L, BinanceClient.KlineCompat.intervalMs("4h"))
        assertEquals(86_400_000L, BinanceClient.KlineCompat.intervalMs("1d"))
        assertEquals(604_800_000L, BinanceClient.KlineCompat.intervalMs("1w"))
    }
}
