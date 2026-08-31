package com.pumpwatch.app.ui

data class TodayPick(
    val symbol: String,
    val side: String,
    val score: Int,
    val golden: Boolean,
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val reasons: List<String>
)

object TodayPicksCache {
    var spot: List<TodayPick> = emptyList()
    var fut: List<TodayPick> = emptyList()
}
