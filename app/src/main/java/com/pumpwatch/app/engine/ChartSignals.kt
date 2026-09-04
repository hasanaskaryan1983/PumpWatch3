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

    // نقاط Order Flow (انباشت = خرید | توزیع = فروش)
    fun orderFlowPoints(
        opens: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>
    ): List<SignalPoint> {
        val out = mutableListOf<SignalPoint>()
        if (closes.size < 45) return out

        val hist = mutableListOf<Double>()
        var cvd = 0.0
        for (i in 1 until closes.size) {
            val range = highs[i] - lows[i]
            if (range > 0) {
                val body = abs(closes[i] - opens[i])
                val buyRatio = if (closes[i] > opens[i]) 0.5 + (body / range) * 0.5 else 0.5 - (body / range) * 0.5
                cvd += volumes[i] * (buyRatio - (1.0 - buyRatio))
            }
            hist.add(cvd)
        }

        for (i in 40 until closes.size) {
            val before = hist.getOrNull(i - 1 - 20) ?: continue
            val now = hist.getOrNull(i - 1) ?: continue
            val base = closes.getOrNull(i - 20) ?: continue
            if (base <= 0) continue
            val priceChange = (closes[i] - base) / base * 100

            if (now > before && abs(priceChange) < 3) out.add(SignalPoint(i, "BUY", lows[i]))
            else if (now < before && abs(priceChange) < 3) out.add(SignalPoint(i, "SELL", highs[i]))
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
