package com.pumpwatch.app.ui

/**
 * 🚀 Commit 163: پل ارتباطی هاب → workspace
 * هاب تب مقصد را اینجا می‌گذارد؛ workspace موقع بالا آمدن آن را می‌خواند و پاک می‌کند.
 */
object HubNavigation {
    var pendingTab: String? = null

    fun consume(): String? {
        val t = pendingTab
        pendingTab = null
        return t
    }
}
