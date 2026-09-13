package com.pumpwatch.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun SpotWorkspace(
    onCoinClick: (CoinMarket) -> Unit,
    selectedCoin: CoinMarket?
) {
    var selectedTab by remember { mutableStateOf(SpotTab.MARKET) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Content
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
            
            // Bottom Navigation - 2 rows
            Surface(
                color = Color(0xFF121820),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SpotTab.values().take(5).forEach { tab ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = tab }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    tab.emoji,
                                    fontSize = 20.sp,
                                    color = if (selectedTab == tab) Color(0xFF00E676) else Color(0xFF8B949E)
                                )
                                Text(
                                    tab.title,
                                    fontSize = 8.sp,
                                    color = if (selectedTab == tab) Color(0xFF00E676) else Color(0xFF8B949E),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SpotTab.values().drop(5).forEach { tab ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = tab }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    tab.emoji,
                                    fontSize = 20.sp,
                                    color = if (selectedTab == tab) Color(0xFF00E676) else Color(0xFF8B949E)
                                )
                                Text(
                                    tab.title,
                                    fontSize = 8.sp,
                                    color = if (selectedTab == tab) Color(0xFF00E676) else Color(0xFF8B949E),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Coin Detail Overlay
        if (selectedCoin != null) {
            Surface(
                color = Color(0xFF0B0F14),
                modifier = Modifier.fillMaxSize()
            ) {
                CoinDetailScreen(
                    coin = selectedCoin,
                    onBack = { /* Clear selected coin */ }
                )
            }
        }
    }
}
