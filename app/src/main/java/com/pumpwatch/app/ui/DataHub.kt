package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 🧠 هاب داده: دادهٔ اسکن‌شده بین تعویض تب‌ها و حتی تعویض اسپات/فیوچرز زنده می‌ماند.
// هر تب اول از هاب می‌خواند (آنی) و فقط با دکمهٔ «بروزرسانی» یا دادهٔ قدیمی دوباره اسکن می‌کند.

object MarketHub {
    var coins: List<CoinMarket> = emptyList(); private set
    var lastUpdateMs: Long = 0L; private set
    var loading: Boolean = false; private set
    private val mutex = Mutex()

    suspend fun ensure(force: Boolean = false): List<CoinMarket> = mutex.withLock {
        val fresh = System.currentTimeMillis() - lastUpdateMs < 5 * 60_000L
        if (!force && coins.isNotEmpty() && fresh) return coins
        loading = true
        try {
            val c = ApiClient.getTop1000Coins(forceRefresh = force)
            if (c.isNotEmpty()) { coins = c; lastUpdateMs = System.currentTimeMillis() }
        } finally { loading = false }
        coins
    }
}

object MemeHub {
    var signals: List<MemeSignal> = emptyList(); private set
    var lastUpdateMs: Long = 0L; private set
    var loading: Boolean = false; private set
    var lastFailed: Boolean = false; private set
    private val mutex = Mutex()

    suspend fun scan(force: Boolean = false, sniper: Boolean): List<MemeSignal> = mutex.withLock {
        val fresh = System.currentTimeMillis() - lastUpdateMs < 10 * 60_000L
        if (!force && signals.isNotEmpty() && fresh) return signals
        loading = true
        try {
            val s = MemeRadar.scan(sniperMode = sniper) { _, _ -> }
            signals = s
            lastUpdateMs = System.currentTimeMillis()
            lastFailed = MemeRadar.lastScanFailed
        } finally { loading = false }
        signals
    }
}

fun hubAgeText(ms: Long): String {
    if (ms <= 0L) return "—"
    val s = (System.currentTimeMillis() - ms) / 1000L
    return when {
        s < 60 -> "همین الان"
        s < 3600 -> "${s / 60} دقیقه پیش"
        else -> "${s / 3600} ساعت پیش"
    }
}

// 🔁 ردیف مشترک «آخرین دریافت + دکمهٔ بروزرسانی» برای همهٔ تب‌ها
@Composable
fun HubRefreshRow(lastUpdateMs: Long, loading: Boolean, accent: Color, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🕒 آخرین دریافت: ${hubAgeText(lastUpdateMs)}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onRefresh,
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.copy(alpha = 0.18f),
                contentColor = accent
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(if (loading) "…" else "↻ بروزرسانی", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
