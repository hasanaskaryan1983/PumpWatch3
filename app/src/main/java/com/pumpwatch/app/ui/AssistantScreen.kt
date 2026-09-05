package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val ABlue = Color(0xFF40C4FF)
private val AGold = Color(0xFFFFC107)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A2230)

private data class CoinAnalysis(
    val symbol: String,
    val name: String,
    val coingeckoId: String?,
    val rank: Int?,
    val score: Int,
    val recommendation: String,
    val indicators: Map<String, String>,
    val whaleActivity: String,
    val reason: String,
    val trustScore: Int,
    val isDex: Boolean,
    val chainName: String?
)

@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var topCoins by remember { mutableStateOf<List<CoinAnalysis>>(emptyList()) }
    var searchResult by remember { mutableStateOf<CoinAnalysis?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            try {
                val coins = withContext(Dispatchers.IO) {
                    ApiClient.getTop1000Coins().take(20)
                }
                topCoins = coins.map { coin ->
                    analyzeCoin(coin.id, coin.symbol, coin.name, coin.market_cap_rank, false, null)
                }
            } catch (e: Exception) {
                error = "خطا در دریافت داده‌ها: ${e.message}"
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "🤖 دستیار هوشمند",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AGreen,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "تحلیل ۱۰۰۰ ارز برتر CEX + تمام DEX‌ها",
            fontSize = 12.sp,
            color = AGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = ACard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🔍 جستجوی ارز (CEX + DEX)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ABlue)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("نماد ارز... (BTC, SOL, FATCOIN...)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            scope.launch {
                                loading = true
                                error = null
                                searchStatus = "🔍 در حال جستجو..."
                                
                                try {
                                    val coins = withContext(Dispatchers.IO) {
                                        ApiClient.getTop1000Coins()
                                    }
                                    val found = coins.firstOrNull { 
                                        it.symbol.equals(searchQuery, ignoreCase = true) 
                                    }
                                    
                                    if (found != null) {
                                        searchStatus = "✅ پیدا شد در CEX"
                                        searchResult = analyzeCoin(
                                            found.id, found.symbol, found.name, 
                                            found.market_cap_rank, false, null
                                        )
                                    } else {
                                        searchStatus = "🔍 جستجو در DEX‌ها..."
                                        val dexResult = searchDex(searchQuery)
                                        if (dexResult != null) {
                                            searchStatus = "✅ پیدا شد در DEX"
                                            searchResult = dexResult
                                        } else {
                                            searchStatus = "❌ پیدا نشد"
                                            error = "ارز پیدا نشد"
                                        }
                                    }
                                } catch (e: Exception) {
                                    error = "خطا: ${e.message}"
                                }
                                loading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تحلیل کن 🔍", fontSize = 12.sp)
                }
                
                if (searchStatus.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(searchStatus, fontSize = 10.sp, color = if (searchStatus.contains("✅")) AGreen else if (searchStatus.contains("❌")) ARed else AGray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        searchResult?.let { analysis ->
            Text("📊 نتیجه: ${analysis.symbol}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AGreen)
            Spacer(Modifier.height(8.dp))
            CoinAnalysisCard(context, analysis)
            Spacer(Modifier.height(16.dp))
        }

        Text("🏆 ۲۰ ارز برتر", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AGold)
        Spacer(Modifier.height(8.dp))

        if (loading && topCoins.isEmpty()) {
            Text("⏳ در حال تحلیل...", fontSize = 12.sp, color = AGray)
        } else if (topCoins.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(topCoins) { analysis ->
                    CoinAnalysisCard(context, analysis)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ACard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("💡 راهنما:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ABlue)
                Spacer(Modifier.height(8.dp))
                Text("• جستجو: CEX (رتبه ۱-۱۰۰۰) + DEX‌ها", fontSize = 11.sp, color = AGray)
                Text("• دکمه 📈: باز کردن نمودار CoinGecko در مرورگر", fontSize = 11.sp, color = AGray)
                Text("• این توصیه مالی نیست", fontSize = 11.sp, color = ARed)
            }
        }
    }
}

@Composable
private fun CoinAnalysisCard(context: android.content.Context, analysis: CoinAnalysis) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ACard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${analysis.symbol}${if (analysis.isDex) " (DEX)" else ""}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ABlue
                    )
                    Text(
                        analysis.name + if (analysis.rank != null) " • #${analysis.rank}" else "",
                        fontSize = 11.sp,
                        color = AGray
                    )
                }
                Text(
                    analysis.recommendation,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        analysis.recommendation.contains("خرید") -> AGreen
                        analysis.recommendation.contains("فروش") -> ARed
                        else -> AGray
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (analysis.coingeckoId != null) {
                Button(
                    onClick = {
                        val url = "https://www.coingecko.com/en/coins/${analysis.coingeckoId}/chart"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ABlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📈 نمودار CoinGecko", fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("📊 امتیاز: ${analysis.score}/100", fontSize = 11.sp, color = AGold)
                Text("🛡️ اعتبار: ${analysis.trustScore}/100", fontSize = 11.sp, color = ABlue)
            }

            Spacer(Modifier.height(8.dp))

            Text("📈 اندیکاتورها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AGreen)
            analysis.indicators.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(key, fontSize = 10.sp, color = AGray)
                    Text(value, fontSize = 10.sp, color = AGray)
                }
            }

            Spacer(Modifier.height(6.dp))

            Text("🐳 فعالیت نهنگ‌ها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ABlue)
            Text(analysis.whaleActivity, fontSize = 10.sp, color = AGray)

            Spacer(Modifier.height(6.dp))

            Text("💡 دلیل:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AGold)
            Text(analysis.reason, fontSize = 10.sp, color = AGray)
        }
    }
}

private suspend fun searchDex(symbol: String): CoinAnalysis? {
    return try {
        val chains = listOf("solana", "bsc", "base", "ethereum", "arbitrum", "optimism", "polygon", "avalanche", "ton")
        
        for (chain in chains) {
            try {
                val pools = GeckoTerminal.api.searchPools(symbol).data
                val pool = pools?.firstOrNull { it.attributes != null }
                if (pool != null) {
                    val name = pool.attributes?.name ?: symbol
                    val chainName = chain.replaceFirstChar { it.uppercase() }
                    return analyzeCoin(null, symbol, name, null, true, chainName)
                }
            } catch (_: Exception) {
                continue
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private suspend fun analyzeCoin(
    coingeckoId: String?,
    symbol: String, 
    name: String, 
    rank: Int?, 
    isDex: Boolean, 
    chainName: String?
): CoinAnalysis {
    return try {
        var closes: List<Double> = emptyList()
        var volumes: List<Double> = emptyList()
        
        if (!isDex) {
            try {
                val klines = withContext(Dispatchers.IO) {
                    BinanceClient.api.klines("${symbol}USDT", "1h", 100)
                }
                // ✅ اصلاح امن: تبدیل JsonElement به String و سپس Double
                closes = klines.map { it[4].asString.toDoubleOrNull() ?: 0.0 }
                volumes = klines.map { it[5].asString.toDoubleOrNull() ?: 0.0 }
            } catch (_: Exception) { }
        }
        
        var score = 50
        val indicators = mutableMapOf<String, String>()
        
        if (closes.size >= 35) {
            val rsi = calculateRSI(closes)
            val macdUp = calculateMACD(closes)
            val ema20 = calculateEMA(closes, 20)
            val ema50 = calculateEMA(closes, 50)
            val price = closes.last()
            val volumeAvg = volumes.average()
            val currentVolume = volumes.last()
            val volumeRatio = if (volumeAvg > 0) currentVolume / volumeAvg else 1.0
            
            when {
                rsi < 30 -> {
                    score += 15
                    indicators["RSI"] = "${rsi.toInt()} (اشباع فروش ✅)"
                }
                rsi > 70 -> {
                    score -= 15
                    indicators["RSI"] = "${rsi.toInt()} (اشباع خرید ❌)"
                }
                else -> indicators["RSI"] = "${rsi.toInt()} (نرمال ⚪)"
            }
            
            if (macdUp) {
                score += 15
                indicators["MACD"] = "صعودی ✅"
            } else {
                score -= 10
                indicators["MACD"] = "نزولی ❌"
            }
            
            when {
                price > ema20 && ema20 > ema50 -> {
                    score += 20
                    indicators["EMA"] = "صعودی ✅"
                }
                price < ema20 && ema20 < ema50 -> {
                    score -= 20
                    indicators["EMA"] = "نزولی ❌"
                }
                else -> indicators["EMA"] = "خنثی ⚪"
            }
            
            when {
                volumeRatio > 2.0 -> {
                    score += 10
                    indicators["حجم"] = "${String.format(Locale.US, "%.1f", volumeRatio)}x (بالا 🔥)"
                }
                volumeRatio < 0.5 -> {
                    score -= 5
                    indicators["حجم"] = "${String.format(Locale.US, "%.1f", volumeRatio)}x (پایین ⚠️)"
                }
                else -> indicators["حجم"] = "${String.format(Locale.US, "%.1f", volumeRatio)}x (نرمال)"
            }
        } else {
            indicators["داده"] = "کندل کافی نیست"
        }
        
        val whaleActivity = try {
            val pool = withContext(Dispatchers.IO) {
                GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
            }
            if (pool != null) {
                val buys = pool.attributes?.transactions?.h1?.buys ?: 0.0
                val sells = pool.attributes?.transactions?.h1?.sells ?: 0.0
                val total = buys + sells
                val volH1 = pool.attributes?.volume?.h1 ?: 0.0
                if (total > 0) {
                    val buyRatio = buys / total * 100
                    "خرید ۱س: ${buyRatio.toInt()}٪ • حجم: ${String.format(Locale.US, "$%.0f", volH1)}"
                } else "بدون داده"
            } else "بدون داده"
        } catch (_: Exception) {
            "بدون داده"
        }
        
        val recommendation = when {
            isDex && closes.size < 35 -> "تحلیل محدود (DEX) ⚪"
            score >= 80 -> "خرید قوی 🟢"
            score >= 65 -> "خرید ✅"
            score >= 45 -> "صبر ⚪"
            score >= 30 -> "فروش 🔴"
            else -> "فروش قوی 🔴"
        }
        
        val reason = buildString {
            if (rank != null) append("رتبه: #$rank • ")
            if (isDex && chainName != null) append("شبکه: $chainName • ")
            append("امتیاز: $score/100")
        }
        
        val trustScore = when {
            rank != null && rank <= 10 -> 90
            rank != null && rank <= 50 -> 75
            rank != null && rank <= 100 -> 60
            rank != null -> 40
            isDex -> 30
            else -> 50
        }
        
        CoinAnalysis(
            symbol = symbol,
            name = name,
            coingeckoId = coingeckoId,
            rank = rank,
            score = score.coerceIn(0, 100),
            recommendation = recommendation,
            indicators = indicators,
            whaleActivity = whaleActivity,
            reason = reason,
            trustScore = trustScore,
            isDex = isDex,
            chainName = chainName
        )
    } catch (e: Exception) {
        CoinAnalysis(
            symbol = symbol,
            name = name,
            coingeckoId = coingeckoId,
            rank = rank,
            score = 50,
            recommendation = "صبر ⚪",
            indicators = mapOf("خطا" to "داده کافی نیست"),
            whaleActivity = "بدون داده",
            reason = "خطا: ${e.message}",
            trustScore = 50,
            isDex = isDex,
            chainName = chainName
        )
    }
}

private fun calculateRSI(closes: List<Double>, period: Int = 14): Double {
    if (closes.size <= period) return 50.0
    var gains = 0.0
    var losses = 0.0
    for (i in 1..period) {
        val change = closes[i] - closes[i - 1]
        if (change > 0) gains += change else losses -= change
    }
    var avgGain = gains / period
    var avgLoss = losses / period
    for (i in period + 1 until closes.size) {
        val change = closes[i] - closes[i - 1]
        avgGain = (avgGain * (period - 1) + max(change, 0.0)) / period
        avgLoss = (avgLoss * (period - 1) + max(-change, 0.0)) / period
    }
    return if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
}

private fun calculateEMA(closes: List<Double>, period: Int): Double {
    if (closes.size < period) return closes.lastOrNull() ?: 0.0
    val k = 2.0 / (period + 1)
    var ema = closes.take(period).average()
    for (i in period until closes.size) {
        ema = closes[i] * k + ema * (1 - k)
    }
    return ema
}

private fun calculateMACD(closes: List<Double>): Boolean {
    if (closes.size < 35) return false
    val ema12 = calculateEMA(closes, 12)
    val ema26 = calculateEMA(closes, 26)
    val prevEma12 = calculateEMA(closes.dropLast(1), 12)
    val prevEma26 = calculateEMA(closes.dropLast(1), 26)
    return (ema12 - ema26) > (prevEma12 - prevEma26)
}
