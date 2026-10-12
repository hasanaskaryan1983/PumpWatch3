package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.FuturesCandle
import com.pumpwatch.app.engine.FuturesScannerEngine
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

// 🧠 هاب داده: داده بین تعویض تب‌ها و حتی تعویض اسپات/فیوچرز زنده می‌ماند
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
            val c = ApiClient.getTop1000Coins()
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

// 🔧 هاب فیوچرز: نتایج سیگنال/اسکنر/هشدار بین تب‌ها زنده می‌ماند
object FuturesHub {
    // Dashboard
    data class FutSignal(
        val coinId: String,
        val rank: Int,
        val symbol: String,
        val base: String,
        val direction: String,
        val score: Int,
        val entry: Double,
        val stopLoss: Double,
        val target: Double,
        val change24h: Double,
        val fundingPct: Double?,
        val volumeUsd: Double,
        val reasons: List<String>
    )
    var dashboardSignals: List<FutSignal> = emptyList(); private set
    var dashboardLastMs: Long = 0L; private set
    var dashboardLoading: Boolean = false; private set

    // Scanner
    var scannerResults: List<FuturesScannerEngine.ScanResult> = emptyList(); private set
    var scannerLastMs: Long = 0L; private set
    var scannerLoading: Boolean = false; private set

    // Alerts
    data class FutAlert(
        val base: String,
        val type: String,
        val severity: Int,
        val title: String,
        val detail: String,
        val rank: Int? = null
    )
    var alertsList: List<FutAlert> = emptyList(); private set
    var alertsLastMs: Long = 0L; private set
    var alertsLoading: Boolean = false; private set

    private val mutex = Mutex()

    suspend fun scanDashboard(force: Boolean = false): List<FutSignal> = mutex.withLock {
        val fresh = System.currentTimeMillis() - dashboardLastMs < 5 * 60_000L
        if (!force && dashboardSignals.isNotEmpty() && fresh) return dashboardSignals
        dashboardLoading = true
        try {
            val coins = ApiClient.getTop100Coins()
            val (tickerMap, premMap) = withContext(Dispatchers.IO) {
                val t = try { BinanceFutures.api.ticker24h().associateBy { it.symbol } } catch (_: Exception) { emptyMap() }
                val p = try { BinanceFutures.api.premiumIndexAll().associateBy { it.symbol } } catch (_: Exception) { emptyMap() }
                t to p
            }
            val allSignals = mutableListOf<FutSignal>()
            for ((idx, c) in coins.withIndex()) {
                val fsym = c.symbol.uppercase() + "USDT"
                val t = tickerMap[fsym] ?: continue
                val prem = premMap[fsym]
                val price = t.lastPrice?.toDoubleOrNull() ?: c.current_price
                val change24h = t.priceChangePercent?.toDoubleOrNull() ?: (c.price_change_percentage_24h ?: 0.0)
                val fundingPct = prem?.lastFundingRate?.toDoubleOrNull()?.times(100.0)
                val volumeUsd = t.quoteVolume?.toDoubleOrNull() ?: c.total_volume
                val absChange = abs(change24h)
                if (absChange < 5.0) continue
                val direction = if (change24h > 0) "LONG" else "SHORT"
                val reasons = mutableListOf<String>()
                var score = (absChange * 3.0).toInt().coerceAtMost(60)
                reasons.add("شتاب ۲۴س: ${String.format("%+.1f%%", change24h)}")
                if (fundingPct != null && abs(fundingPct) >= 0.03) {
                    score += 20
                    reasons.add("فاندینگ شدید: ${String.format("%+.4f%%", fundingPct)}")
                }
                if (volumeUsd >= 50_000_000) {
                    score += 15
                    reasons.add("نقدشوندگی بالا")
                }
                val slDistance = price * 0.02
                val tpDistance = price * 0.04
                val stopLoss = if (direction == "LONG") price - slDistance else price + slDistance
                val target = if (direction == "LONG") price + tpDistance else price - tpDistance
                allSignals.add(FutSignal(
                    coinId = c.id, rank = c.market_cap_rank ?: (idx + 1), symbol = fsym,
                    base = c.symbol.uppercase(), direction = direction, score = score.coerceAtMost(100),
                    entry = price, stopLoss = stopLoss, target = target, change24h = change24h,
                    fundingPct = fundingPct, volumeUsd = volumeUsd, reasons = reasons
                ))
            }
            dashboardSignals = allSignals.sortedByDescending { it.score }.take(20)
            dashboardLastMs = System.currentTimeMillis()
        } finally { dashboardLoading = false }
        dashboardSignals
    }

    suspend fun scanScanner(force: Boolean = false): List<FuturesScannerEngine.ScanResult> = mutex.withLock {
        val fresh = System.currentTimeMillis() - scannerLastMs < 5 * 60_000L
        if (!force && scannerResults.isNotEmpty() && fresh) return scannerResults
        scannerLoading = true
        try {
            val coins = ApiClient.getTop100Coins()
            val (tMap, pMap) = withContext(Dispatchers.IO) {
                val t = try { BinanceFutures.api.ticker24h().associateBy { it.symbol } } catch (_: Exception) { emptyMap() }
                val p = try { BinanceFutures.api.premiumIndexAll().associateBy { it.symbol } } catch (_: Exception) { emptyMap() }
                t to p
            }
            val candidates = coins.mapNotNull { c ->
                val fsym = c.symbol.uppercase() + "USDT"
                val t = tMap[fsym] ?: return@mapNotNull null
                val chg = t.priceChangePercent?.toDoubleOrNull() ?: 0.0
                val vol = t.quoteVolume?.toDoubleOrNull() ?: 0.0
                if (abs(chg) < 2.0 && vol < 50_000_000) return@mapNotNull null
                Triple(fsym, c.symbol.uppercase(), vol)
            }.sortedByDescending { it.third }.take(10)
            if (candidates.isEmpty()) {
                scannerResults = emptyList()
                scannerLastMs = System.currentTimeMillis()
                return@withLock scannerResults
            }
            val phaseA = mutableListOf<FuturesScannerEngine.ScanResult>()
            for (cand in candidates) {
                val c4 = withContext(Dispatchers.IO) { candles(cand.first, "4h", 200) }
                val c1 = withContext(Dispatchers.IO) { candles(cand.first, "1h", 200) }
                val c15 = withContext(Dispatchers.IO) { candles(cand.first, "15m", 100) }
                val r = FuturesScannerEngine.analyze(
                    cand.first, cand.second, c4, c1, c15,
                    pMap[cand.first]?.lastFundingRate?.toDoubleOrNull()?.times(100.0)
                )
                if (r != null) phaseA.add(r)
            }
            val final = phaseA.map { r ->
                val c5 = withContext(Dispatchers.IO) { candles(r.symbol, "5m", 100) }
                FuturesScannerEngine.applyTrigger(r, c5)
            }
            scannerResults = final.sortedByDescending { it.score }
            scannerLastMs = System.currentTimeMillis()
        } finally { scannerLoading = false }
        scannerResults
    }

    suspend fun scanAlerts(force: Boolean = false): List<FutAlert> = mutex.withLock {
        val fresh = System.currentTimeMillis() - alertsLastMs < 5 * 60_000L
        if (!force && alertsList.isNotEmpty() && fresh) return alertsList
        alertsLoading = true
        try {
            val top100 = try { ApiClient.getTop100Coins() } catch (_: Exception) { emptyList() }
            val top100Symbols = top100.map { it.symbol.uppercase() }.toSet()
            val top100Rank = top100.associate { it.symbol.uppercase() to it.market_cap_rank }
            val prem = try { BinanceFutures.api.premiumIndexAll() } catch (_: Exception) { emptyList() }
            val tick = try { BinanceFutures.api.ticker24h() } catch (_: Exception) { emptyList() }
            val fundingRates = prem.mapNotNull { it.lastFundingRate?.toDoubleOrNull() }
            val avgAbsFunding = if (fundingRates.isNotEmpty()) fundingRates.map { abs(it) }.average() else 0.0001
            val fundingThreshold = maxOf(avgAbsFunding * 3.0, 0.0003)
            val fundingHighThreshold = maxOf(avgAbsFunding * 6.0, 0.0006)
            val out = mutableListOf<FutAlert>()
            for (p in prem) {
                if (!p.symbol.endsWith("USDT")) continue
                val base = p.symbol.removeSuffix("USDT")
                if (base !in top100Symbols) continue
                val rate = p.lastFundingRate?.toDoubleOrNull() ?: continue
                val absRate = abs(rate)
                if (absRate < fundingThreshold) continue
                val sev = if (absRate >= fundingHighThreshold) 3 else 2
                val pct = rate * 100.0
                out.add(FutAlert(
                    base = base, type = "FUNDING", severity = sev,
                    title = if (pct > 0) "فاندینگ شدید مثبت" else "فاندینگ شدید منفی",
                    detail = if (pct > 0) String.format("%+.4f%% — لانگ‌ها هزینه می‌دهند", pct)
                    else String.format("%+.4f%% — شورت‌ها هزینه می‌دهند", pct),
                    rank = top100Rank[base]
                ))
            }
            for (t in tick) {
                if (!t.symbol.endsWith("USDT")) continue
                val base = t.symbol.removeSuffix("USDT")
                if (base !in top100Symbols) continue
                val chg = t.priceChangePercent?.toDoubleOrNull() ?: continue
                if (abs(chg) < 10.0) continue
                val sev = if (abs(chg) >= 20.0) 3 else 2
                out.add(FutAlert(
                    base = base, type = "MOVE24", severity = sev,
                    title = if (chg > 0) "پامپ شدید ۲۴ ساعته" else "دامپ شدید ۲۴ ساعته",
                    detail = String.format("%+.1f%% در ۲۴ ساعت", chg),
                    rank = top100Rank[base]
                ))
            }
            alertsList = out.sortedByDescending { it.severity }.take(50)
            alertsLastMs = System.currentTimeMillis()
        } finally { alertsLoading = false }
        alertsList
    }

    private suspend fun candles(symbol: String, interval: String, limit: Int): List<FuturesCandle> =
        try { BinanceFutures.api.klines(symbol, interval, limit).mapNotNull { BinanceFutures.parseCandle(it) } }
        catch (_: Exception) { emptyList() }
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
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(if (loading) "…" else "↻ بروزرسانی", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
