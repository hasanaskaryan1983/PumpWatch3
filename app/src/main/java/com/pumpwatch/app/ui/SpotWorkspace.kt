package com.pumpwatch.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.MarketScreen
import com.pumpwatch.app.data.CoinMarket

enum class SpotTab(val title: String, val emoji: String) {
    MARKET("بازار", "📊"),
    ALERTS("هشدار", "🔔"),
    WHALE("نهنگ", "🐳"),
    ASSISTANT("دستیار", "🤖"),
    BACKTEST("بک‌تست", "🧪"),
    TOP("برترین", "🏆"),
    MEME("میم", "🐸"),
    LOG("سیگنال", "📓"),
    TRADES("معامله", "📈"),
    WALLETS("کیف پول", "👛")
}

private val SpotAccent = Color(0xFF00E676)
private val SpotNavBg = Color(0xFF121820)
private val SpotNavIdle = Color(0xFF8B949E)

/**
 * SpotWorkspace — محیط کامل Spot با ۱۰ تب اختصاصی.
 * نکتهٔ معماری: لایهٔ CoinDetail متعلق به AppShell (MainActivity) است،
 * نه این Workspace؛ پس اینجا رندر نمی‌شود.
 */
@Composable
fun SpotWorkspace(onCoinClick: (CoinMarket) -> Unit) {
    var selectedTab by remember { mutableStateOf(SpotTab.MARKET) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SpotTab.MARKET -> MarketScreen(onCoinClick = onCoinClick)
                SpotTab.ALERTS -> SmartAlertsScreen(onCoinClick = onCoinClick)
                SpotTab.WHALE -> WhaleRadarScreen()
                SpotTab.ASSISTANT -> AssistantScreen()
                SpotTab.BACKTEST -> BacktestScreen()
                SpotTab.TOP -> TopPicksScreen("SPOT")
                SpotTab.MEME -> MemeRadarScreen()
                SpotTab.LOG -> SignalLogScreen()
                SpotTab.TRADES -> TradesScreen()
                SpotTab.WALLETS -> WalletScreen()
            }
        }

        Surface(color = SpotNavBg, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    SpotTab.entries.take(5).forEach { tab ->
                        SpotNavItem(tab, selectedTab == tab) { selectedTab = tab }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    SpotTab.entries.drop(5).forEach { tab ->
                        SpotNavItem(tab, selectedTab == tab) { selectedTab = tab }
                    }
                }
            }
        }
    }
}

/**
 * SpotNavItem — آیتم ناوبری یک تب.
 * به‌صورت extension روی RowScope تعریف شده تا Modifier.weight(1f) در دسترس باشد.
 */
@Composable
private fun RowScope.SpotNavItem(tab: SpotTab, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(4.dp)
    ) {
        Text(tab.emoji, fontSize = 20.sp, color = if (selected) SpotAccent else SpotNavIdle)
        Text(
            tab.title,
            fontSize = 8.sp,
            color = if (selected) SpotAccent else SpotNavIdle,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
