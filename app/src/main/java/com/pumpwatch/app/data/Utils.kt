package com.pumpwatch.app

import java.util.Locale

fun formatMarketCap(cap: Double?): String {
    if (cap == null) return "-"
    return when {
        cap >= 1_000_000_000_000 -> String.format(Locale.US, "$%.2fT", cap / 1_000_000_000_000)
        cap >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", cap / 1_000_000_000)
        cap >= 1_000_000 -> String.format(Locale.US, "$%.2fM", cap / 1_000_000)
        else -> String.format(Locale.US, "$%,.0f", cap)
    }
}

fun formatPrice(price: Double): String {
    return when {
        price >= 1000 -> String.format(Locale.US, "$%,.2f", price)
        price >= 1 -> String.format(Locale.US, "$%.4f", price)
        price >= 0.01 -> String.format(Locale.US, "$%.6f", price)
        else -> String.format(Locale.US, "$%.8f", price)
    }
}
