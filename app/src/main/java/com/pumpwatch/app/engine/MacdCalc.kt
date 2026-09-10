package com.pumpwatch.app.engine

/**
 * محاسبات MACD هم‌تراز روی timeline مشترک.
 * تمام سری‌ها هم‌اندازه با ورودی‌اند؛ نقاط نارس (قبل از period-1) = null.
 */
object MacdCalc {

    /**
     * EMA aligned: نقطه `period-1` = SMA(0..period-1)، نقاط بعدی = EMA واقعی.
     */
    fun emaAligned(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty() || period <= 0) return emptyList()
        if (values.size < period) return List(values.size) { null }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double?>(values.size)
        var ema = values.take(period).average()
        for (i in values.indices) {
            when {
                i < period - 1 -> out.add(null)
                i == period - 1 -> out.add(ema)
                else -> {
                    ema = values[i] * k + ema * (1 - k)
                    out.add(ema)
                }
            }
        }
        return out
    }

    /**
     * خط MACD: EMA_fast - EMA_slow روی timeline مشترک.
     * نقاط قبل از `slow-1` = null.
     */
    fun macdLine(closes: List<Double>, fast: Int = 12, slow: Int = 26): List<Double?> {
        if (closes.size < slow) return List(closes.size) { null }
        val ef = emaAligned(closes, fast)
        val es = emaAligned(closes, slow)
        return closes.indices.map { i ->
            val f = ef[i]; val s = es[i]
            if (f == null || s == null) null else f - s
        }
    }

    /**
     * خط سیگنال: EMA sig روی بخش معتبر خط MACD، برگشته به timeline اصلی.
     */
    fun signalLine(closes: List<Double>, fast: Int = 12, slow: Int = 26, sig: Int = 9): List<Double?> {
        val m = macdLine(closes, fast, slow)
        val firstValid = slow - 1
        if (firstValid < 0 || firstValid >= m.size) return List(closes.size) { null }
        val validMacd = m.drop(firstValid).mapNotNull { it }
        if (validMacd.size < sig) return List(closes.size) { null }
        val sigOnValid = emaAligned(validMacd, sig)
        val out = ArrayList<Double?>(closes.size)
        for (i in 0 until firstValid) out.add(null)
        out.addAll(sigOnValid)
        return out
    }

    /**
     * تشخیص کراس صعودی MACD در آخرین نقطه کامل.
     * (قابل استفاده از QuickScanner و CoinDetailScreen — مرحله ۳)
     */
    fun macdUp(closes: List<Double>, fast: Int = 12, slow: Int = 26, sig: Int = 9): Boolean {
        val n = closes.size
        if (n < slow + sig) return false
        val m = macdLine(closes, fast, slow)
        val s = signalLine(closes, fast, slow, sig)
        if (n < 2) return false
        val m0 = m[n - 2]; val m1 = m[n - 1]
        val s0 = s[n - 2]; val s1 = s[n - 1]
        if (m0 == null || m1 == null || s0 == null || s1 == null) return false
        return m0 <= s0 && m1 > s1
    }
}
