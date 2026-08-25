package com.pumpwatch.app

import java.util.Locale

fun formatMarketCap(marketCap: Double?): String {
    if (marketCap == null) return "N/A"
    return when {
        marketCap >= 1_000_000_000_000 -> String.format(Locale.US, "$%.2fT", marketCap / 1_000_000_000_000)
        marketCap >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", marketCap / 1_000_000_000)
        marketCap >= 1_000_000 -> String.format(Locale.US, "$%.2fM", marketCap / 1_000_000)
        marketCap >= 1_000 -> String.format(Locale.US, "$%.2fK", marketCap / 1_000)
        else -> String.format(Locale.US, "$%.2f", marketCap)
    }
}

fun formatPrice(price: Double?): String {
    if (price == null) return "N/A"
    return when {
        price >= 1 -> String.format(Locale.US, "$%,.2f", price)
        price >= 0.01 -> String.format(Locale.US, "$%.4f", price)
        else -> String.format(Locale.US, "$%.6f", price)
    }
}
