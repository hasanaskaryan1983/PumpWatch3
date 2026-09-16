package com.pumpwatch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.worker.SignalNavigator

enum class FuturesTab(val title: String, val emoji: String) {
    DASHBOARD("داشبورد", "🎛️"),
    SCANNER("اسکنر", "🔍"),
    CHART("نمودار", "📊"),
    ALERTS("هشدار", "🔔"),
    BACKTEST("بک‌تست", "🧪"),
    PAPER("Paper", "📝"),
    JOURNAL("ژورنال", "📓")
}

private val FuturesAccent = Color(0xFFFF5252)
private val FuturesAccentSoft = Color(0xFFFF6B35)
private val FuturesNavBg = Color(0xFF1A0E0E)
private val FuturesNavIdle = Color(0xFF8B949E)
private val FuturesBg = Color(0xFF0F0B0B)

@Composable
fun FuturesWorkspace() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(FuturesTab.DASHBOARD) }

    // 🚀 Sprint 11 (C2b): کلیک روی نوتیفیکیشن → تب اسکنر
    LaunchedEffect(Unit) {
        SignalNavigator.observe(context).collect { pending ->
            if (pending) {
                selectedTab = FuturesTab.SCANNER
                SignalNavigator.consume(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FuturesBg)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                FuturesTab.DASHBOARD -> FuturesDashboardScreen()
                FuturesTab.SCANNER -> FuturesScannerScreen()
                FuturesTab.CHART -> FuturesChartScreen()
                // 🚀 Sprint 12 (F5): هشدارهای واقعی فیوچرز
                FuturesTab.ALERTS -> FuturesAlertsScreen()
                FuturesTab.BACKTEST -> FuturesPlaceholder(
                    emoji = "🧪",
                    title = "بک‌تست Futures",
                    description = "Leverage • Isolated margin • Liquidation • Funding • Fee • Slippage • Out-of-sample"
                )
                FuturesTab.PAPER -> FuturesPlaceholder(
                    emoji = "📝",
                    title = "Paper Trading Futures",
                    description = "Long/Short • Leverage • Liquidation • Unrealized/Realized PnL • Maintenance margin"
                )
                FuturesTab.JOURNAL -> FuturesPlaceholder(
                    emoji = "📓",
                    title = "ژورنال عملکرد",
                    description = "بهترین setup • بهترین timeframe • عملکرد بر اساس regime • Long vs Short • Drawdown متوالی"
                )
            }
        }

        Surface(color = FuturesNavBg, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                FuturesTab.entries.forEach { tab ->
                    FuturesNavItem(tab, selectedTab == tab) { selectedTab = tab }
                }
            }
        }
    }
}

@Composable
private fun RowScope.FuturesNavItem(tab: FuturesTab, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Text(
            tab.emoji,
            fontSize = 18.sp,
            color = if (selected) FuturesAccent else FuturesNavIdle
        )
        Text(
            tab.title,
            fontSize = 8.sp,
            color = if (selected) FuturesAccent else FuturesNavIdle,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun FuturesPlaceholder(emoji: String, title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FuturesBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(emoji, fontSize = 56.sp)
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = FuturesAccent,
                textAlign = TextAlign.Center
            )
            Text(
                description,
                fontSize = 12.sp,
                color = FuturesNavIdle,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Surface(
                color = FuturesAccent.copy(alpha = 0.15f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "🚧 در Sprint بعدی پیاده‌سازی می‌شود",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    color = FuturesAccentSoft
                )
            }
        }
    }
}
