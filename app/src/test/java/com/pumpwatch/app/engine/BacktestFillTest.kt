package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۱ / Commit 4): تست رگرسیون قوانین fill و کارمزد بک‌تست.
 *
 * این تست‌ها چهار رفتار جدید را قفل می‌کنند (H6 + M7):
 *  ۱) برخورد intrabar: استاپ/تارگت با high/low چک می‌شوند، نه close
 *  ۲) قانون محافظه‌کارانه: اگر هر دو سطح در یک کندل خورد شوند → استاپ اول
 *  ۳) next-bar fill: ورود در open کندلِ بعد از سیگنال، نه close کندل سیگنال
 *  ۴) گپ: پر شدن در open وقتی کندل پشت سطح باز شود
 *  + determinism: اجرای دوبارهٔ همان داده = همان نتیجه (معیار مرحله ۳ گزارش)
 *
 * روش: ساخت کندل‌های مصنوعی deterministic (بدون random) تا سناریوها
 * دقیقاً قابل کنترل باشند.
 */
class BacktestFillTest {

    private fun candle(
        o: Double, h: Double, l: Double, c: Double, v: Double = 1_000_000.0
    ): List<Double> = listOf(o, h, l, c, v)

    /** ۲۶۰ کندل صعود یکنواخت ۰.۵٪ (open = close قبلی، بدون گپ) */
    private fun baseUptrend(): MutableList<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var prevClose = 100.0 / 1.005
        for (i in 0 until 260) {
            val c = prevClose * 1.005
            out.add(candle(prevClose, c * 1.001, prevClose * 0.999, c))
            prevClose = c
        }
        return out
    }

    private fun closeAt(i: Int): Double = 100.0 * Math.pow(1.005, i.toDouble())

    /** کندل ورود (بار ۲۱۱) با گپ صعودی کوچک در open */
    private fun setEntryBarGap(k: MutableList<List<Double>>) {
        val c210 = closeAt(210)
        k[211] = candle(c210 * 1.002, c210 * 1.004 * 1.001, c210 * 1.002 * 0.999, c210 * 1.004)
    }

    /** کندل ورود بدون گپ (open = close کندل سیگنال) برای مقایسه */
    private fun setEntryBarNoGap(k: MutableList<List<Double>>) {
        val c210 = closeAt(210)
        k[211] = candle(c210, c210 * 1.004 * 1.001, c210 * 0.999, c210 * 1.004)
    }

    // ------------------------------------------------------------------
    // ۱) قانون محافظه‌کارانه: هر دو سطح در یک کندل → استاپ اول
    // ------------------------------------------------------------------
    @Test
    fun both_levels_touched_stop_wins_conservatively() {
        val k = baseUptrend()
        setEntryBarGap(k)
        val c210 = closeAt(210)
        // کندلی که هم تارگت (~+2.1%) و هم استاپ (~-1.4%) را لمس می‌کند
        k[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.97, c210 * 1.01)

        val (trades, _) = BacktestEngine.runSpot("TEST", k, 30)
        assertTrue("expected at least one trade", trades.isNotEmpty())
        val t = trades.first()
        assertEquals("BUY", t.side)
        assertEquals("when both levels touch in one candle, STOP must win", "LOSS", t.result)
        assertTrue("stop-out must be negative after fees", t.pnl < 0)
    }

    // ------------------------------------------------------------------
    // ۲) برخورد intrabar: خروج به قیمت تارگت، نه close کندل
    // ------------------------------------------------------------------
    @Test
    fun target_touch_fills_at_target_not_at_close() {
        val k = baseUptrend()
        setEntryBarGap(k)
        val c210 = closeAt(210)
        // high به تارگت می‌رسد ولی close خیلی پایین‌تر می‌ماند
        k[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val (trades, _) = BacktestEngine.runSpot("TEST", k, 30)
        val t = trades.first()
        assertEquals("WIN", t.result)
        // اگر خروج روی close بود: ~(1.01-1.002)/1.002 = +0.5% منهای کارمزد = ~+0.2%
        // اگر خروج روی تارگت باشد: ~(1.021-1.002)/1.002 = +1.9% منهای کارمزد = ~+1.6%
        assertTrue("fill must be at target level (intrabar), not at close", t.pnl > 1.0)
        assertTrue("round-trip fee (0.3%) must be deducted", t.pnl < 1.9)
    }

    // ------------------------------------------------------------------
    // ۳) گپ: کندل پشت تارگت باز شود → پر شدن در open (بهتر برای ما)
    // ------------------------------------------------------------------
    @Test
    fun gap_through_target_fills_at_open() {
        val c210 = closeAt(210)

        val kB = baseUptrend()
        setEntryBarGap(kB)
        kB[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val kC = baseUptrend()
        setEntryBarGap(kC)
        // گپ صعودی: open بالای تارگت
        kC[212] = candle(c210 * 1.03, c210 * 1.05, c210 * 1.02, c210 * 1.04)

        val tB = BacktestEngine.runSpot("TEST", kB, 30).first.first()
        val tC = BacktestEngine.runSpot("TEST", kC, 30).first.first()

        assertEquals("WIN", tB.result)
        assertEquals("WIN", tC.result)
        assertTrue(
            "gap-up open must fill at open (better price) => pnlC > pnlB",
            tC.pnl > tB.pnl
        )
    }

    // ------------------------------------------------------------------
    // ۴) next-bar fill: ورود در open کندل بعد، نه close کندل سیگنال
    // ------------------------------------------------------------------
    @Test
    fun entry_uses_next_bar_open_not_signal_close() {
        val c210 = closeAt(210)

        val kGap = baseUptrend()
        setEntryBarGap(kGap)   // ورود گران‌تر (گپ +0.2%)
        kGap[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val kNoGap = baseUptrend()
        setEntryBarNoGap(kNoGap) // ورود = close کندل سیگنال
        kNoGap[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val tGap = BacktestEngine.runSpot("TEST", kGap, 30).first.first()
        val tNoGap = BacktestEngine.runSpot("TEST", kNoGap, 30).first.first()

        assertTrue(
            "higher entry (next-bar gap open) must reduce profit => proof entry = next bar open",
            tGap.pnl < tNoGap.pnl
        )
    }

    // ------------------------------------------------------------------
    // ۵) determinism: اجرای دوبارهٔ همان داده = همان نتیجه
    //    (معیار پذیرش مرحله ۳ گزارش اجرایی)
    // ------------------------------------------------------------------
    @Test
    fun spot_replay_is_deterministic() {
        val k = baseUptrend()
        val (t1, m1) = BacktestEngine.runSpot("TEST", k, 30)
        val (t2, m2) = BacktestEngine.runSpot("TEST", k, 30)
        assertEquals("same input must produce identical trades", t1, t2)
        assertEquals("same input must produce identical metrics", m1, m2)
    }

    @Test
    fun futures_replay_is_deterministic_and_fires_on_pullback() {
        // ۲۰۰ کندل صعود قوی + ۶۰ کندل رنج/پولبک (برای نشستن RSI و فعال‌شدن سیگنال)
        val k = mutableListOf<List<Double>>()
        var prev = 100.0
        for (i in 0 until 200) {
            val c = prev * 1.005
            k.add(candle(prev, c * 1.001, prev * 0.999, c))
            prev = c
        }
        for (i in 0 until 60) {
            val c = if (i % 2 == 0) prev * 1.001 else prev * 0.9995
            k.add(candle(prev, c * 1.001, prev * 0.999, c))
            prev = c
        }

        val (t1, m1) = BacktestEngine.runFutures("TEST", k, 260, 12, 0.0001)
        val (t2, m2) = BacktestEngine.runFutures("TEST", k, 260, 12, 0.0001)
        assertEquals(t1, t2)
        assertEquals(m1, m2)

        assertTrue(
            "pullback inside uptrend should generate at least one futures signal",
            t1.isNotEmpty()
        )
        assertTrue(t1.all { it.side == "BUY" || it.side == "SELL" })
        assertTrue(t1.all { it.result in listOf("WIN", "LOSS", "EXP") })
    }
}
