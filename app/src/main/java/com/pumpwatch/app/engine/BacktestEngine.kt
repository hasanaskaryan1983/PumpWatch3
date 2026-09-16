package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * 🚀 Sprint 13 (F6b): موتور بک‌تست استراتژی — دو حالت Spot و Futures
 *
 * interface مورد انتظار BacktestScreen:
 * - Trade: دادهٔ یک معاملهٔ بسته‌شده (نماد، جهت، نتیجه، PnL، امتیاز)
 * - BacktestMetrics: آمار کل بک‌تست (وین‌ریت، expectancy، profit factor، ...)
 * - runSpot(symbol, klines, holdDays): اسپات — امتیاز روزانه، نگهداری hold روز
 * - runFutures(symbol, klines, evalLast, hold, feeRate): فیوچرز کوتاه‌مدت
 *
 * ورودی klines: List<List<Double>> با ساختار
 *   [open, high, low, close, volume] به‌ترتیب
 *
 * صداقت:
 * - هزینه‌ها (0.1% slippage + 0.1% fee هر طرف = 0.4% round-trip) از PnL کسر می‌شوند
 * - اگر دادهٔ کافی نباشد، لیست ترید خالی برمی‌گردد (نه exception)
 * - استاپ و تارگت بر اساس ATR — همان منطق ربات زنده
 */
object BacktestEngine {

    /** یک معاملهٔ بسته‌شده در بک‌تست */
    data class Trade(
        val symbol: String,
        val side: String,         // "BUY" | "SELL"
        val result: String,       // "WIN" | "LOSS" | "EXP"
        val pnl: Double,          // درصد PnL (بعد از کسر هزینه)
        val score: Int            // امتیاز سیگنال در لحظهٔ ورود
    )

    /** آمار کل بک‌تست */
    data class BacktestMetrics(
        val totalTrades: Int,
        val wins: Int,
        val losses: Int,
        val expired: Int,
        val winRate: Double,
        val profitFactor: Double,
        val avgPnl: Double,
        val avgWin: Double,
        val avgLoss: Double,
        val expectancy: Double,
        val totalPnl: Double,
        val maxDrawdown: Double,
        val equityCurve: List<Double>,
        val inSampleMetrics: BacktestMetrics?,
        val outOfSampleMetrics: BacktestMetrics?
    )

    // ================================================================
    // =========================  HELPERS  ============================
    // ================================================================

    private fun closes(klines: List<List<Double>>): List<Double> = klines.map { it[3] }
    private fun volumes(klines: List<List<Double>>): List<Double> = klines.map { it[4] }
    private fun highs(klines: List<List<Double>>): List<Double> = klines.map { it[1] }
    private fun lows(klines: List<List<Double>>): List<Double> = klines.map { it[2] }

    private fun ema(values: List<Double>, p: Int): Double {
        if (values.size < p) return values.lastOrNull() ?: 0.0
        val k = 2.0 / (p + 1)
        var e = values.take(p).average()
        for (i in p until values.size) e = values[i] * k + e * (1 - k)
        return e
    }

    private fun rsi(values: List<Double>, p: Int = 14): Double {
        if (values.size <= p) return 50.0
        var g = 0.0; var l = 0.0
        for (i in 1..p) {
            val x = values[i] - values[i - 1]
            if (x > 0) g += x else l -= x
        }
        var ag = g / p; var al = l / p
        for (i in p + 1 until values.size) {
            val x = values[i] - values[i - 1]
            ag = (ag * (p - 1) + max(x, 0.0)) / p
            al = (al * (p - 1) + max(-x, 0.0)) / p
        }
        return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
    }

    private fun macdUp(values: List<Double>): Boolean {
        if (values.size < 35) return false
        val p = values.dropLast(1)
        return (ema(values, 12) - ema(values, 26)) > (ema(p, 12) - ema(p, 26))
    }

    private fun atr(klines: List<List<Double>>, period: Int = 14): Double {
        if (klines.size < period + 1) return 0.0
        val trs = mutableListOf<Double>()
        for (i in 1 until klines.size) {
            val prev = klines[i - 1]; val cur = klines[i]
            val tr = maxOf(cur[1] - cur[2], abs(cur[1] - prev[3]), abs(cur[2] - prev[3]))
            trs.add(tr)
        }
        return trs.takeLast(period).average()
    }

    /** OBV: مجموع حجم در روزهای مثبت منهای حجم در روزهای منفی */
    private fun obvSeries(klines: List<List<Double>>): List<Double> {
        if (klines.size < 2) return emptyList()
        val out = mutableListOf<Double>()
        var obv = 0.0
        for (i in 1 until klines.size) {
            val diff = klines[i][3] - klines[i - 1][3]
            obv += when {
                diff > 0 -> klines[i][4]
                diff < 0 -> -klines[i][4]
                else -> 0.0
            }
            out.add(obv)
        }
        return out
    }

    /**
     * امتیازدهی اسپات (همان منطق ربات زنده — EMA/MACD/RSI/Volume/OBV/Weekly)
     * @param klinesHistory پنجرهٔ تاریخ تا کندل فعلی (no look-ahead)
     */
    private fun spotScore(klinesHistory: List<List<Double>>): Int {
        if (klinesHistory.size < 200) return -100
        val c = closes(klinesHistory)
        val v = volumes(klinesHistory)
        val weekly = c.chunked(7).map { it.last() }
        val price = c.last()

        val e50 = ema(c, 50); val e200 = ema(c, 200)
        var s = when {
            price > e50 && e50 > e200 -> 50
            price > e50 -> 25
            price < e50 && e50 < e200 -> -50
            else -> -25
        }
        s += if (macdUp(c)) 20 else -20
        val r = rsi(c)
        s += when {
            r in 45.0..65.0 -> 15
            r < 35 -> 20
            r > 75 -> -25
            else -> 5
        }
        if (v.size > 40) {
            val rec = v.takeLast(20).average()
            val prior = v.dropLast(20).takeLast(20).average()
            if (prior > 0 && rec > prior * 1.2) s += 10
        }
        if (weekly.size >= 25) {
            val w = weekly.last()
            val e10 = ema(weekly, 10); val e20 = ema(weekly, 20)
            s += when {
                w > e10 && e10 > e20 -> 15
                w > e10 -> 8
                w < e10 && e10 < e20 -> -20
                else -> -8
            }
        }
        if (c.size >= 30) {
            var obv = 0.0; val ser = mutableListOf<Double>()
            for (i in 1 until c.size) {
                obv += when {
                    c[i] > c[i - 1] -> v[i]
                    c[i] < c[i - 1] -> -v[i]
                    else -> 0.0
                }
                ser.add(obv)
            }
            if (ser.size >= 21) {
                val now = ser.last(); val past = ser[ser.size - 21]
                s += when {
                    now > past * 1.05 -> 10
                    now > past -> 5
                    now < past * 0.95 -> -10
                    else -> -5
                }
            }
        }
        return s.coerceIn(-100, 100)
    }

    /**
     * امتیازدهی فیوچرز کوتاه‌مدت (EMA9/21 cross + RSI + ATR breakout)
     */
    private fun futuresScore(klinesHistory: List<List<Double>>): Int {
        if (klinesHistory.size < 60) return 0
        val c = closes(klinesHistory)
        val price = c.last()
        val e9 = ema(c, 9); val e21 = ema(c, 21); val e50 = ema(c, 50)

        var s = 0
        // روند: EMA9 > EMA21 > EMA50 = صعودی
        if (e9 > e21 && e21 > e50) s += 30
        else if (e9 < e21 && e21 < e50) s -= 30
        else if (e9 > e21) s += 15
        else if (e9 < e21) s -= 15

        // کراس تازه (در ۳ کندل آخر)
        for (i in (c.size - 3) until c.size) {
            if (i < 10) continue
            val e9p = ema(c.take(i), 9); val e21p = ema(c.take(i), 21)
            val e9n = ema(c.take(i + 1), 9); val e21n = ema(c.take(i + 1), 21)
            if (e9p <= e21p && e9n > e21n) { s += 25; break }
            if (e9p >= e21p && e9n < e21n) { s -= 25; break }
        }

        // RSI
        val r = rsi(c)
        s += when {
            r in 40.0..60.0 -> 10
            r < 30 -> 20
            r > 70 -> -20
            else -> 0
        }

        // مومنتوم: فاصلهٔ قیمت از EMA50
        val mom = if (e50 > 0) (price - e50) / e50 * 100 else 0.0
        s += when {
            mom in 0.5..5.0 -> 15
            mom in -5.0..-0.5 -> -15
            mom > 15 -> -10    // overextended
            mom < -15 -> 10    // oversold bounce potential
            else -> 0
        }

        return s.coerceIn(-100, 100)
    }

    /** محاسبهٔ metrics از لیست تریدها — pure */
    private fun computeMetrics(trades: List<Trade>): BacktestMetrics {
        val wins = trades.count { it.result == "WIN" }
        val losses = trades.count { it.result == "LOSS" }
        val expired = trades.count { it.result == "EXP" }
        val decided = wins + losses
        val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
        val avgPnl = if (trades.isEmpty()) 0.0 else trades.map { it.pnl }.average()
        val totalPnl = trades.sumOf { it.pnl }
        val winningTrades = trades.filter { it.pnl > 0 }
        val losingTrades = trades.filter { it.pnl < 0 }
        val avgWin = if (winningTrades.isNotEmpty()) winningTrades.map { it.pnl }.average() else 0.0
        val avgLoss = if (losingTrades.isNotEmpty()) abs(losingTrades.map { it.pnl }.average()) else 0.0
        val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)

        val totalWins = winningTrades.sumOf { it.pnl }
        val totalLosses = abs(losingTrades.sumOf { it.pnl })
        val profitFactor = when {
            totalLosses > 0 -> totalWins / totalLosses
            totalWins > 0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        }

        val equityCurve = mutableListOf(100.0)
        var equity = 100.0
        trades.forEach { t ->
            equity *= (1 + t.pnl / 100.0)
            equityCurve.add(equity)
        }
        var maxDrawdown = 0.0
        var peak = equityCurve[0]
        for (e in equityCurve) {
            if (e > peak) peak = e
            val dd = (peak - e) / peak * 100.0
            if (dd > maxDrawdown) maxDrawdown = dd
        }

        return BacktestMetrics(
            totalTrades = trades.size, wins = wins, losses = losses, expired = expired,
            winRate = winRate, profitFactor = profitFactor,
            avgPnl = avgPnl, avgWin = avgWin, avgLoss = avgLoss,
            expectancy = expectancy, totalPnl = totalPnl, maxDrawdown = maxDrawdown,
            equityCurve = equityCurve,
            inSampleMetrics = null, outOfSampleMetrics = null
        )
    }

    // ================================================================
    // ==========================  SPOT  ==============================
    // ================================================================

    /**
     * بک‌تست اسپات روزانه
     * @param klines کندل‌های روزانه (حداقل ۲۵۰ تا)
     * @param holdDays حداکثر روز نگهداری
     * @return Pair(لیست تریدها، metrics)
     *
     * قوانین:
     * - ورود وقتی امتیاز >= 0 و weekly مثبت و OBV مثبت
     * - هزینه: 0.2% ورود + 0.2% خروج + 0.1% slippage هر طرف = 0.6% round-trip
     * - استاپ = ۲ × ATR • تارگت = ۳ × ATR
     * - اگر به holdDays رسید بدون رسیدن به هیچ‌کدام → "EXP"
     */
    fun runSpot(
        symbol: String,
        klines: List<List<Double>>,
        holdDays: Int
    ): Pair<List<Trade>, BacktestMetrics> {
        if (klines.size < 250) return emptyList<Trade>() to computeMetrics(emptyList())

        val trades = mutableListOf<Trade>()
        val feeRoundTrip = 0.6 // 0.2% entry + 0.2% exit + 0.1% slip x 2

        var inTrade = false
        var entryPrice = 0.0
        var entryDay = 0
        var stop = 0.0
        var target = 0.0
        var entryScore = 0

        for (i in 210 until klines.size) {
            val history = klines.subList(0, i + 1)
            val cur = klines[i]
            val price = cur[3]

            if (inTrade) {
                val daysHeld = i - entryDay
                val pnlPctRaw = (price - entryPrice) / entryPrice * 100
                val hitStop = price <= stop
                val hitTarget = price >= target
                val expired = !hitStop && !hitTarget && daysHeld >= holdDays

                if (hitStop || hitTarget || expired) {
                    val result = when {
                        hitTarget -> "WIN"
                        hitStop -> "LOSS"
                        else -> "EXP"
                    }
                    val pnl = pnlPctRaw - feeRoundTrip
                    trades.add(Trade(symbol, "BUY", result, pnl, entryScore))
                    inTrade = false
                }
            } else {
                // بررسی شرایط ورود
                val score = spotScore(history)
                if (score < 0) continue

                val c = closes(history)
                val weekly = c.chunked(7).map { it.last() }
                val weeklyPos = weekly.size >= 2 && weekly.last() > weekly[weekly.size - 2]

                val obv = obvSeries(history)
                val obvPos = obv.size >= 21 && obv.last() > obv[obv.size - 21]

                if (weeklyPos && obvPos) {
                    val a = atr(history)
                    if (a <= 0) continue
                    entryPrice = price
                    entryDay = i
                    stop = price - 2 * a
                    target = price + 3 * a
                    entryScore = score
                    inTrade = true
                }
            }
        }

        return trades to computeMetrics(trades)
    }

    // ================================================================
    // ========================  FUTURES  =============================
    // ================================================================

    /**
     * بک‌تست فیوچرز کوتاه‌مدت
     * @param klines کندل‌های تایم‌فریم انتخابی (15m/30m/1h/4h)
     * @param evalLast تعداد کندل آخر برای ارزیابی (معمولاً برابر limit)
     * @param hold تعداد کندل نگهداری
     * @param feeRate کارمزد یک‌طرفه (معمولاً 0.0001 = 0.01%)
     *
     * قوانین:
     * - امتیاز >= 40 → BUY، امتیاز <= -40 → SELL
     * - استاپ = ۱.۵ × ATR • تارگت = ۲.۵ × ATR
     * - خروج روی CLOSE کندل `hold`ام یا برخورد با استاپ/تارگت
     * - اگر به hold رسید بدون stop/target → "EXP" (بر اساس جهت: مثبت = WIN، منفی = LOSS)
     */
    fun runFutures(
        symbol: String,
        klines: List<List<Double>>,
        evalLast: Int,
        hold: Int,
        feeRate: Double
    ): Pair<List<Trade>, BacktestMetrics> {
        if (klines.size < 80) return emptyList<Trade>() to computeMetrics(emptyList())

        val trades = mutableListOf<Trade>()
        val feeRoundTrip = feeRate * 200 * 2 + 0.2 // 0.1% slippage x 2 + fee دو طرف

        var inTrade = false
        var side = ""
        var entryPrice = 0.0
        var entryIdx = 0
        var stop = 0.0
        var target = 0.0
        var entryScore = 0

        val startIdx = max(60, klines.size - evalLast)
        var i = startIdx

        while (i < klines.size) {
            val history = klines.subList(0, i + 1)
            val cur = klines[i]
            val price = cur[3]

            if (inTrade) {
                val candlesSinceEntry = i - entryIdx
                val pnlPctRaw = if (side == "BUY")
                    (price - entryPrice) / entryPrice * 100
                else
                    (entryPrice - price) / entryPrice * 100

                val hitStop = if (side == "BUY") price <= stop else price >= stop
                val hitTarget = if (side == "BUY") price >= target else price <= target
                val expired = !hitStop && !hitTarget && candlesSinceEntry >= hold

                if (hitStop || hitTarget || expired) {
                    val pnl = pnlPctRaw - feeRoundTrip
                    val result = when {
                        hitTarget -> "WIN"
                        hitStop -> "LOSS"
                        else -> if (pnl > 0) "WIN" else "LOSS"
                    }
                    trades.add(Trade(symbol, side, result, pnl, entryScore))
                    inTrade = false
                }
            } else {
                val score = futuresScore(history)
                val a = atr(history)
                if (a > 0 && score >= 40) {
                    side = "BUY"
                    entryPrice = price
                    entryIdx = i
                    stop = price - 1.5 * a
                    target = price + 2.5 * a
                    entryScore = score
                    inTrade = true
                } else if (a > 0 && score <= -40) {
                    side = "SELL"
                    entryPrice = price
                    entryIdx = i
                    stop = price + 1.5 * a
                    target = price - 2.5 * a
                    entryScore = -score
                    inTrade = true
                }
            }
            i++
        }

        return trades to computeMetrics(trades)
    }
}
