package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pumpwatch.app.MarketScreen
import com.pumpwatch.app.R
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.worker.SignalNavigator

enum class SpotTab(val title: String, val iconRes: Int, val accent: Color) {
    MARKET("بازار", R.drawable.ic_tab_market, PumpWatchPalette.TabMarket),
    ALERTS("هشدار", R.drawable.ic_tab_alerts, PumpWatchPalette.TabAlerts),
    WHALE("نهنگ", R.drawable.ic_tab_whale, PumpWatchPalette.TabWhale),
    ASSISTANT("دستیار", R.drawable.ic_tab_assistant, PumpWatchPalette.TabAssistant),
    BACKTEST("بک‌تست", R.drawable.ic_tab_backtest, PumpWatchPalette.TabBacktest),
    TRADERS("تریدرها", R.drawable.ic_tab_traders, PumpWatchPalette.TabLeaderboard),
    WATCHLIST("واچ‌لیست", R.drawable.ic_tab_watchlist, PumpWatchPalette.TabWatchlist),
    WALLETS("کیف پول", R.drawable.ic_tab_wallet, PumpWatchPalette.TabWallet),
    TRADES("معامله", R.drawable.ic_tab_trade, PumpWatchPalette.TabTrade),
    MEME("میم", R.drawable.ic_tab_meme, PumpWatchPalette.TabMeme)
}

private val SpotDockTabs = listOf(
    SpotTab.MARKET,
    SpotTab.ALERTS,
    SpotTab.WHALE,
    SpotTab.ASSISTANT,
    SpotTab.BACKTEST,
    SpotTab.TRADERS,
    SpotTab.WATCHLIST,
    SpotTab.WALLETS,
    SpotTab.TRADES,
    SpotTab.MEME
).map { EditorialDockTab(it.name, it.title, it.iconRes, it.accent) }

@Composable
fun SpotWorkspace(onCoinClick: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(SpotTab.MARKET) }

    LaunchedEffect(Unit) {
        SignalNavigator.observe(context).collect { pending ->
            if (pending) {
                selectedTab = SpotTab.ALERTS
                SignalNavigator.consume(context)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SpotTab.MARKET -> MarketScreen(onCoinClick = onCoinClick)
                SpotTab.ALERTS -> SmartAlertsScreen(onCoinClick = onCoinClick)
                SpotTab.WHALE -> WhaleRadarScreen()
                SpotTab.ASSISTANT -> AssistantScreen()
                SpotTab.BACKTEST -> BacktestScreen()
                SpotTab.MEME -> MemeRadarScreen()
                SpotTab.TRADES -> TradesScreen()
                SpotTab.WALLETS -> WalletScreen()
                
                // 🚀 اصلاح شد: پارامتر onCoinClick به WatchlistScreen پاس داده شد
                SpotTab.WATCHLIST -> WatchlistScreen(onCoinClick = onCoinClick)
                
                SpotTab.TRADERS -> TraderLeaderboardScreen()
            }
        }
        EditorialTabDock(
            tabs = SpotDockTabs,
            selectedId = selectedTab.name,
            onSelect = { selectedTab = SpotTab.valueOf(it) }
        )
    }
}
