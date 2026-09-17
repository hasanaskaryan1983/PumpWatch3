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
        assertTrue("stop-out must be negative
