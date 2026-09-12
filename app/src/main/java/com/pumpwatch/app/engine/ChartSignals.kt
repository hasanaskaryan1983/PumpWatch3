package com.pumpwatch.app.engine

import kotlin.math.abs

data class SignalPoint(
    val index: Int,
    val side: String, // "BUY" | "SELL"
    val price: Double
)

object ChartSignals {

    // نقاط خرید/فروش Sixty Second Trades روی نمودار
    fun sixtyPoints(highs: List<Double>, lows: List<Double>, closes: List<Double>): List<SignalPoint> {
        val out = mutableListOf<SignalPoint>()
        if (closes.size < 40) return out
        for (i in 30 until closes.size) {
            val r = PumpDetector.analyzeSixtySecond(
                highs.subList(0, i + 1),
                lows.subList(0, i + 1),
                closes.subList(0, i + 1)
            )
            if (r.signal == "BUY") out.add(SignalPoint(i, "BUY", lows[i]))
            else if (r.signal == "SELL") out.add(SignalPoint(i, "SELL", highs[i]))
        }
        return out
    }

    // سیگنال مشترک: جایی که حداقل minAgree اندیکاتور، در فاصله ±۲ کندل هم‌نظر باشن
    fun consensusPoints(lists: List<List<SignalPoint>>, minAgree: Int): List<SignalPoint> {
        if (lists.isEmpty()) return emptyList()
        val all = lists.flatten().distinctBy { it.index to it.side }
        val out = mutableListOf<SignalPoint>()
        for (p in all) {
            val agree = lists.count { list ->
                list.any { q -> q.side == p.side && abs(q.index - p.index) <= 2 }
            }
            if (agree >= minAgree) out.add(p)
        }
        return out.sortedBy { it.index }
    }
}
