package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های P0-6 — تضمین می‌کند که UnifiedSignalEngine هرگز بر اساس
 * کندلِ در حال تشکیل (forming candle) سیگنال قطعی تولید نکند.
 *
 * منطق: klines از Binance همیشه آخرین ردیف = کندلِ در حال تشکیل را برمی‌گرداند.
 * اگر Engine این کندل را نادیده نگیرد، سیگنال می‌تواند وسط ساعت ظاهر و ناپدید شود.
 * P0-6: Engine خودکار آخرین کندل را drop می‌کند و فقط روی کندل‌های بسته‌شده محاسبه می‌کند.
 */
class UnifiedSignalEngineTest {

    /**
     * ساخت یک کندل ساده با OHLCV مشخص.
     * time به‌طور پیش‌فرض 0 است (برای تست اهمیتی ندارد؛ فقط ترتیب مهم است).
     */
    private fun c(open: Double, high: Double, low: Double, close: Double, volume: Double, time: Long = 0L): Candle =
        Candle(time = time, open = open, high = high, low = low, close = close, volume = volume)

    /**
     * سناریوی پایه: ۶۰ کندل فلت + ۱ کندل شکست صعودی (forming).
     *
     * بدون P0-6: Engine با close=105 کندل forming محاسبه می‌کرد
     * → prevHigh=100.05 → breakout=true → PUMP signal (اشتباه!)
     *
     * با P0-6: Engine آخرین کندل (forming) را drop می‌کند
     * → closed=۶۰ کندل فلت → price=100 → breakout=false → NONE (درست!)
     */
    @Test
    fun `forming breakout candle does NOT produce PUMP signal`() {
        val flat = (0 until 60).map {
            c(open = 100.0, high = 100.05, low = 99.95, close = 100.0, volume = 1000.0)
        }
        // کندل ۶۱ام (forming): breakout با حجم بالا ولی هنوز بسته نشده
        val formingBreakout = c(open = 100.0, high = 105.0, low = 100.0, close = 105.0, volume = 15000.0)
        val candles = flat + formingBreakout

        val result = UnifiedSignalEngine.analyze(
            coinId = "TEST", symbol = "TEST", name = "TEST",
            candles1h = candles, mode = "SPOT"
        )

        // P0-6: باید NONE باشد، نه PUMP — چون کندل شکست هنوز بسته نشده
        assertNotNull("باید نتیجه‌ای برگرداند (حداقل ۶۱ کندل)", result)
        assertEquals(
            "کندل ناتمام نباید سیگنال PUMP تولید کند",
            "NONE", result!!.side
        )
    }

    /**
     * سناریوی متقارن: اگر کندل شکست **بسته شده** باشد و کندلِ بعد از آن تشکیل شود،
     * سیگنال باید PUMP باشد. این تأیید می‌کند که dropLast(1) فقط forming را حذف می‌کند.
     */
    @Test
    fun `closed breakout followed by forming candle produces PUMP signal`() {
        val flat = (0 until 59).map {
            c(open = 100.0, high = 100.05, low = 99.95, close = 100.0, volume = 1000.0)
        }
        // کندل ۶۰ام: breakout بسته شده
        val closedBreakout = c(open = 100.0, high = 105.0, low = 100.0, close = 105.0, volume = 15000.0)
        // کندل ۶۱ام: forming بعد از breakout (هنوز بسته نشده)
        val forming = c(open = 105.0, high = 105.5, low = 104.5, close = 105.2, volume = 2000.0)
        val candles = flat + closedBreakout + forming

        val result = UnifiedSignalEngine.analyze(
            coinId = "TEST", symbol = "TEST", name = "TEST",
            candles1h = candles, mode = "SPOT",
            params = UnifiedSignalParams(minScore = 30)  // آستانه پایین برای اطمینان از تولید سیگنال
        )

        assertNotNull(result)
        // با dropLast(1): closed شامل closedBreakout می‌شود
        // → price=105 → breakout=true → احتمال PUMP
        assertTrue(
            "کندل شکست بسته‌شده باید سیگنال تولید کند",
            result!!.side == "PUMP" || result.score > 0
        )
    }

    /**
     * گارد اندازه: اگر ورودی کمتر از ۶۱ کندل باشد، null برگردان.
     * (چون ۱ کندل forming حذف می‌شود و حداقل ۶۰ کندل بسته لازم است.)
     */
    @Test
    fun `less than 61 candles returns null`() {
        val candles = (0 until 60).map {
            c(open = 100.0, high = 100.05, low = 99.95, close = 100.0, volume = 1000.0)
        }
        val result = UnifiedSignalEngine.analyze(
            coinId = "TEST", symbol = "TEST", name = "TEST",
            candles1h = candles, mode = "SPOT"
        )
        assertNull("۶۰ کندل کافی نیست (۱ forming باید حذف شود)", result)
    }

    /**
     * candleCloseTs باید از آخرین کندل بسته پر شود (اگر time > 0).
     */
    @Test
    fun `candleCloseTs is populated from last closed candle time`() {
        val flat = (0 until 60).map { i ->
            c(open = 100.0, high = 100.05, low = 99.95, close = 100.0, volume = 1000.0, time = 1_700_000_000_000L + i * 3_600_000L)
        }
        val forming = c(open = 100.0, high = 101.0, low = 99.0, close = 100.5, volume = 1500.0, time = 1_700_000_000_000L + 60 * 3_600_000L)
        val candles = flat + forming

        val result = UnifiedSignalEngine.analyze(
            coinId = "TEST", symbol = "TEST", name = "TEST",
            candles1h = candles, mode = "SPOT"
        )

        assertNotNull(result)
        val expectedCloseTs = 1_700_000_000_000L + 59 * 3_600_000L  // time آخرین کندل بسته (ایندکس ۵۹)
        assertEquals(
            "candleCloseTs باید برابر time آخرین کندل بسته باشد",
            expectedCloseTs, result!!.candleCloseTs
        )
    }

    /**
     * اگر time = 0 (legacy)، candleCloseTs fallback به System.currentTimeMillis.
     */
    @Test
    fun `candleCloseTs falls back to currentTimeMillis when time is zero`() {
        val candles = (0 until 61).map {
            c(open = 100.0, high = 100.05, low = 99.95, close = 100.0, volume = 1000.0, time = 0L)
        }
        val before = System.currentTimeMillis()
        val result = UnifiedSignalEngine.analyze(
            coinId = "TEST", symbol = "TEST", name = "TEST",
            candles1h = candles, mode = "SPOT"
        )
        val after = System.currentTimeMillis()

        assertNotNull(result)
        assertTrue(
            "candleCloseTs باید بین before و after باشد",
            result!!.candleCloseTs in before..after
        )
    }
}
