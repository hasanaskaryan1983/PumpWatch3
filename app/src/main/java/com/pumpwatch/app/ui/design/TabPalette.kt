package com.pumpwatch.app.ui.design

import androidx.compose.ui.graphics.Color

object TabPalette {
    val Market      = Color(0xFF22D3EE)
    val Alerts      = Color(0xFFFB4D6D)
    val Whale       = Color(0xFF3B82F6)
    val Assistant   = Color(0xFFA855F7)
    val Backtest    = Color(0xFF10B981)
    val Meme        = Color(0xFFF59E0B)
    val Trade       = Color(0xFF00E676)
    val Wallet      = Color(0xFFFFC107)
    val Watchlist   = Color(0xFFEC4899)
    val Leaderboard = Color(0xFFFF9500)

    private val byKey: Map<String, Color> = mapOf(
        "market" to Market, "alerts" to Alerts, "whale" to Whale,
        "assistant" to Assistant, "backtest" to Backtest, "meme" to Meme,
        "trade" to Trade, "wallet" to Wallet, "watchlist" to Watchlist,
        "leaderboard" to Leaderboard,
    )

    fun accent(key: String): Color = byKey[key.lowercase()] ?: Market
    fun glow(c: Color) = c.copy(alpha = 0.18f)
    fun border(c: Color) = c.copy(alpha = 0.55f)
    fun chip(c: Color) = c.copy(alpha = 0.22f)
}
