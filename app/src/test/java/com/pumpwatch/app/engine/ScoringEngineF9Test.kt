package com.pumpwatch.app.engine

import com.google.gson.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields

class ScoringEngineF9Test {

    private val dayMs = 86_400_000L

    private fun candle(time: Long, high: Double, low: Double, close: Double, vol: Double): JsonArray {
        val a = JsonArray()
        a.add(time); a.add(0.0); a.add(high); a.add(low); a.add(close); a.add(vol)
        return a
    }

    @Test
    fun `F9 - ATR sees high-low range even when closes are flat`() {
        val start = LocalDate.of(2024, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val candles = (0 until 200).map { candle(start + it * dayMs, 105.0, 95.0, 100.0, 1000.0) }
        assertEquals(10.0, ScoringEngine.atrPctTrueRange(candles, 100.0), 1e-9)
        assertEquals(10.0, ScoringEngine.scoreFromCandles(candles).second, 1e-9)
    }

    @Test
    fun `F9 - ATR includes overnight gap via prevClose`() {
        val start = LocalDate.of(2024, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val candles = (0 until 199).map { candle(start + it * dayMs, 100.5, 99.5, 100.0, 1000.0) }.toMutableList()
        candles.add(candle(start + 199 * dayMs, 120.0, 119.0, 119.5, 1000.0))
        val expected = (13 * 1.0 + 20.0) / 14 / 119.5 * 100
        assertEquals(expected, ScoringEngine.atrPctTrueRange(candles, 119.5), 1e-9)
    }

    @Test
    fun `F9 - OBV delta sign is correct even when cumulative OBV is negative`() {
        // 41 روز: past دقیقاً -200 (i=20)، now=-201 → delta=-1
        // منطق ضربی قدیمی اینجا +10 می‌داد؛ منطق تفاضلی جدید باید -5 بدهد
        val closes = mutableListOf(100.0)
        val vols = mutableListOf(0.0)
        for (i in 1..20) { closes.add(100.0 - i); vols.add(10.0) }   // obv → -200
        for (i in 21..39) { closes.add(80.0); vols.add(2.0) }        // flat (19 روز)
        closes.add(79.0); vols.add(1.0)                              // i=40 → delta=-1
        val score = ScoringEngine.obvDeltaScore(closes, vols)
        assertTrue("delta منفی باید امتیاز منفی بدهد، got=$score", score < 0)
        assertEquals(-5, score)
    }

    @Test
    fun `F9 - weekly closes align to ISO calendar weeks, not window start`() {
        val start = LocalDate.of(2024, 1, 3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val candles = (0 until 30).map { candle(start + it * dayMs, 101.0, 99.0, 100.0 + it, 1000.0) }

        val wf = WeekFields.ISO
        val expected = mutableListOf<Double>()
        var lastKey = -1
        var lastClose = 0.0
        for (c in candles) {
            val d = LocalDate.ofEpochDay(c[0].asLong / dayMs)
            val key = d.get(wf.weekBasedYear()) * 100 + d.get(wf.weekOfWeekBasedYear())
            if (key != lastKey) { if (lastKey != -1) expected.add(lastClose); lastKey = key }
            lastClose = c[4].asDouble
        }
        expected.add(lastClose)

        assertEquals(expected, ScoringEngine.weeklyClosesAligned(candles))
        // تفاوت در محتواست نه تعداد: اولین هفتهٔ تقویمی به Jan7 (104) ختم می‌شود، chunked به Jan9 (106)
        assertTrue(
            "محتوای هفتهٔ تقویمی باید با chunked(7) متفاوت باشد",
            expected != candles.chunked(7).map { it[4].asDouble }
        )
    }
}
