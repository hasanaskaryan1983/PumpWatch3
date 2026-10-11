package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.R
import com.pumpwatch.app.worker.SignalNavigator

enum class FuturesTab(val title: String, val iconRes: Int, val accent: Color) {
    DASHBOARD("سیگنال", R.drawable.ic_tab_market, PumpWatchPalette.TabMarket),
    SCANNER("اسکنر", R.drawable.ic_tab_scanner, PumpWatchPalette.TabWhale),
    ALERTS("هشدار", R.drawable.ic_tab_alerts, PumpWatchPalette.TabAlerts),
    PAPER("تمرین", R.drawable.ic_tab_trade, PumpWatchPalette.TabAssistant),
    JOURNAL("ژورنال", R.drawable.ic_tab_journal, PumpWatchPalette.TabWatchlist),
    BACKTEST("بک‌تست", R.drawable.ic_tab_backtest, PumpWatchPalette.TabBacktest),
    CHART("نمودار", R.drawable.ic_tab_market, PumpWatchPalette.TabTrade)
}

private val FuturesDockTabs = listOf(
    FuturesTab.DASHBOARD,
    FuturesTab.SCANNER,
    FuturesTab.ALERTS,
    FuturesTab.PAPER,
    FuturesTab.JOURNAL,
    FuturesTab.BACKTEST,
    FuturesTab.CHART
).map { EditorialDockTab(it.name, it.title, it.iconRes, it.accent) }

@Composable
fun FuturesWorkspace() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(FuturesTab.DASHBOARD) }

    LaunchedEffect(Unit) {
        SignalNavigator.observe(context).collect { pending ->
            if (pending) {
                selectedTab = FuturesTab.SCANNER
                SignalNavigator.consume(context)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                FuturesTab.DASHBOARD -> FuturesDashboardScreen()
                FuturesTab.SCANNER -> FuturesScannerScreen()
                FuturesTab.ALERTS -> FuturesAlertsScreen()
                FuturesTab.PAPER -> FuturesPaperScreen()
                FuturesTab.JOURNAL -> FuturesJournalScreen()
                FuturesTab.BACKTEST -> BacktestScreen()
                FuturesTab.CHART -> FuturesChartPlaceholder()
            }
        }
        EditorialTabDock(
            tabs = FuturesDockTabs,
            selectedId = selectedTab.name,
            onSelect = { selectedTab = FuturesTab.valueOf(it) }
        )
    }
}

// جای‌نگهٔ خصوصی: تابع FuturesChartScreen در مخزن وجود ندارد؛
// با این placeholder از قرمز شدن بیلد جلوگیری می‌شود. ظاهر داک دست‌نخورده می‌ماند.
@Composable
private fun FuturesChartPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("📈 نمودار چندبازه‌ای به‌زودی", color = PumpWatchPalette.Muted, fontSize = 12.sp)
    }
}
