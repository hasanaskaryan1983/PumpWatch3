package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.GeckoOhlcv
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.engine.MacdCalc
import com.pumpwatch.app.ui.components.GlassCard
import com.pumpwatch.app.ui.PumpWatchPalette
import com.pumpwatch.app.ui.EditorialPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// 🚀 P0 Tab 04: تغییر از private به internal برای رفع خطای visibility در API کلاس CoinAnalysis
internal data class DexCandle(val o: Double, val h: Double, val l: Double, val c: Double)

// 🚀 P0 Tab 04: تعریف صریح کاندیدای دارایی
internal data class AssetCandidate(
    val providerId: String,
    val symbol: String,
    val name: String,
    val chain: String?,
    val contract: String?,
    val venue: String, // "CEX" یا "DEX"
    val rank: Int?,
    val poolUrl: String?
)

// 🚀 P0 Tab 04: نتیجهٔ مهر و موم شده (Sealed Outcome)
internal sealed class AnalysisStatus {
    object Success : AnalysisStatus()
    object Partial : AnalysisStatus()
    object Insufficient : AnalysisStatus()
    data class Error(val message: String) : AnalysisStatus()
}

// 🚀 P0 Tab 04: ساختار دادهٔ تحلیل با متادیتای کامل
internal data class CoinAnalysis(
    val candidate: AssetCandidate,
    val status: AnalysisStatus,
    val score: Int?,
    val momentumLabel: String,
    val indicators: Map<String, String>,
    val buySellRatio1h: Double?,
    val reason: String,
    val dataQualityScore: Int,
    val source: String,
    val observedAtMs: Long,
    val validityHours: Int,
    val invalidationScenario: String,
    val formulaBreakdown: Map<String, String>,
    val candles: List<DexCandle>?
)

@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AssetCandidate>>(emptyList()) }
    var selectedCandidate by remember { mutableStateOf<AssetCandidate?>(null) }
    var analysisResult by remember { mutableStateOf<CoinAnalysis?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true
            searchResults = emptyList()
            selectedCandidate = null
            analysisResult = null
            error = null
            try {
                val coins = withContext(Dispatchers.IO) { ApiClient.getTop1000Coins() }
                val asRank = q.toIntOrNull()
                val cexCandidates = coins.filter { 
                    it.symbol.equals(q, true) || it.name.contains(q, true) || (asRank != null && it.market_cap_rank == asRank) 
                }.map { 
                    AssetCandidate(
                        providerId = it.id,
                        symbol = it.symbol.uppercase(),
                        name = it.name,
                        chain = null,
                        contract = null,
                        venue = "CEX (CoinGecko)",
                        rank = it.market_cap_rank,
                        poolUrl = null
                    )
                }
                
                var dexCandidates = emptyList<AssetCandidate>()
                if (cexCandidates.isEmpty() || q.length > 2) {
                    try {
                        val pools = withContext(Dispatchers.IO) { GeckoTerminal.api.searchPools(q).data ?: emptyList() }
                        dexCandidates = pools.take(5).mapNotNull { pool ->
                            val attrs = pool.attributes ?: return@mapNotNull null
                            val net = pool.relationships?.network?.data?.id ?: "unknown"
                            val addr = pool.id?.substringAfter('_') ?: ""
                            AssetCandidate(
                                providerId = pool.id ?: "",
                                symbol = attrs.name?.split('/')?.firstOrNull()?.trim() ?: q.uppercase(),
                                name = attrs.name ?: q,
                                chain = net,
                                contract = addr,
                                venue = "DEX ($net)",
                                rank = null,
                                poolUrl = "https://www.geckoterminal.com/$net/pools/$addr"
                            )
                        }
                    } catch (_: Exception) {}
                }
                
                searchResults = (cexCandidates + dexCandidates).distinctBy { it.providerId }.take(10)
                if (searchResults.isEmpty()) {
                    error = "هیچ دارایی منطبقی یافت نشد. لطفاً نماد، نام یا شناسه را دقیق‌تر وارد کنید."
                }
            } catch (t: Throwable) {
                error = "خطا در جست‌وجو: ${t.message}"
            } finally {
                loading = false
            }
        }
    }

    fun analyzeCandidate(candidate: AssetCandidate) {
        scope.launch {
            loading = true
            selectedCandidate = candidate
            analysisResult = null
            error = null
            try {
                val result = withContext(Dispatchers.IO) { performAnalysis(candidate) }
                analysisResult = result
            } catch (t: Throwable) {
                error = "خطا در تحلیل: ${t.message}"
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        EditorialPageHeader(
            eyebrow = "RESEARCH · EXPLAINABLE",
            title = "تحلیل‌گر قاعده‌محور",
            subtitle = "ارزیابی تکنیکال بر اساس اندیکاتورهای کلاسیک و داده‌های حجم. این خروجی پژوهشی است و توصیه مالی نیست.",
            accent = PumpWatchPalette.TabAssistant
        )

        Spacer(Modifier.height(12.dp))

        GlassCard(accent = PumpWatchPalette.TabAssistant, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔍 جستجوی دارایی (نماد، نام یا رتبه)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("مثلاً: BTC، SOL یا 46", fontSize = 11.sp, color = PumpWatchPalette.MutedDim) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PumpWatchPalette.GlassDeep,
                        unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                        focusedTextColor = PumpWatchPalette.Text,
                        unfocusedTextColor = PumpWatchPalette.Text,
                        cursorColor = PumpWatchPalette.TabAssistant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Button(
                    onClick = { doSearch() },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.TabAssistant, contentColor = PumpWatchPalette.Ink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading && selectedCandidate == null) CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), color = PumpWatchPalette.Ink, strokeWidth = 2.dp)
                    Text("جست‌وجو 🔍", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (error != null) Text(error ?: "", fontSize = 10.sp, color = PumpWatchPalette.Negative)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 🚀 P0 Tab 04: انتخاب صریح کاندیدا پیش از تحلیل
        if (searchResults.isNotEmpty() && selectedCandidate == null) {
            Text("لطفاً دارایی مورد نظر را برای تحلیل انتخاب کنید:", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(bottom = 4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(searchResults) { candidate ->
                    GlassCard(accent = PumpWatchPalette.TabAssistant, modifier = Modifier.fillMaxWidth().clickable { analyzeCandidate(candidate) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text)
                                Text("${candidate.name} • ${candidate.venue}" + (if (candidate.chain != null) " (${"${candidate.chain}"})" else ""), fontSize = 9.sp, color = PumpWatchPalette.Muted)
                            }
                            Text("➡️", fontSize = 16.sp, color = PumpWatchPalette.TabAssistant)
                        }
                    }
                }
            }
        }

        // 🚀 P0 Tab 04: نمایش وضعیت تحلیل
        if (loading && selectedCandidate != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PumpWatchPalette.TabAssistant)
                Text("در حال تحلیل ${selectedCandidate?.symbol}...", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (analysisResult != null) {
            val a = analysisResult!!
            GlassCard(accent = PumpWatchPalette.TabAssistant, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.candidate.symbol} ${if (a.candidate.venue.startsWith("DEX")) "(DEX)" else ""}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = PumpWatchPalette.Text)
                            Text(
                                a.candidate.name + (if (a.candidate.rank != null) " • رتبه #${a.candidate.rank}" else if (a.candidate.chain != null) " • شبکه ${a.candidate.chain}" else ""),
                                fontSize = 11.sp, color = PumpWatchPalette.Muted
                            )
                        }
                        val statusText = when (a.status) {
                            is AnalysisStatus.Success -> "✅ موفق"
                            is AnalysisStatus.Partial -> "⚠️ ناقص"
                            is AnalysisStatus.Insufficient -> "❌ داده ناکافی"
                            is AnalysisStatus.Error -> "❌ خطا"
                        }
                        val statusColor = when (a.status) {
                            is AnalysisStatus.Success -> PumpWatchPalette.Positive
                            is AnalysisStatus.Partial -> PumpWatchPalette.Warning
                            is AnalysisStatus.Insufficient -> PumpWatchPalette.Negative
                            is AnalysisStatus.Error -> PumpWatchPalette.Negative
                        }
                        Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                            Text(statusText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                    }

                    // 🚀 P0 Tab 04: زبان خنثی و سلب مسئولیت شفاف
                    Text(
                        a.momentumLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            a.momentumLabel.contains("صعودی") -> PumpWatchPalette.Positive
                            a.momentumLabel.contains("نزولی") -> PumpWatchPalette.Negative
                            else -> PumpWatchPalette.Warning
                        }
                    )
                    
                    Surface(color = PumpWatchPalette.Negative.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "⚠️ سلب مسئولیت: این خروجی صرفاً تجمیع چند اندیکاتور تکنیکال است و احتمال موفقیت کالیبره‌شده یا توصیه مالی نیست. مسئولیت تصمیم نهایی با کاربر است.",
                            fontSize = 9.sp, color = PumpWatchPalette.Negative, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (a.score != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("📊 امتیاز هم‌گرایی:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                                Text("${a.score}/100", fontSize = 14.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("🛡️ کیفیت داده:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                                Text("${a.dataQualityScore}/100", fontSize = 14.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 🚀 P0 Tab 04: نمایش شفاف منبع، زمان و انقضا
                    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
                    val expiryTime = timeFmt.format(Date(a.observedAtMs + a.validityHours * 3600 * 1000L))
                    Surface(color = PumpWatchPalette.Info.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "منبع: ${a.source} • مشاهده: ${timeFmt.format(Date(a.observedAtMs))} • اعتبار تا: $expiryTime",
                            fontSize = 9.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("🎯 سناریوی تغییر ارزیابی:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                            Text(a.invalidationScenario, fontSize = 10.sp, color = PumpWatchPalette.Muted, lineHeight = 13.sp)
                        }
                    }

                    Text("📈 اندیکاتورهای تکنیکال:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    a.indicators.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, fontSize = 10.sp, color = PumpWatchPalette.Muted)
                            Text(v, fontSize = 10.sp, color = PumpWatchPalette.Text)
                        }
                    }

                    // 🚀 P0 Tab 04: تعریف دقیق و عینی "نهنگ" بدون ادعای مالکیت
                    if (a.buySellRatio1h != null) {
                        Text("📊 نسبت حجم خرید به فروش (۱ ساعت): ${(a.buySellRatio1h * 100).toInt()}٪", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                        Text("این متریک نشان‌دهندهٔ نسبت تراکنش‌های خرید به کل تراکنش‌ها در استخر است و ادعای مالکیت کیف پول خاصی ندارد.", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                    }

                    Text("💡 منطق امتیازدهی:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    Text(a.reason, fontSize = 10.sp, color = PumpWatchPalette.Muted)

                    if (a.formulaBreakdown.isNotEmpty()) {
                        Text("📐 جزئیات فرمول:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                        a.formulaBreakdown.forEach { (indicator, weight) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(indicator, fontSize = 10.sp, color = PumpWatchPalette.Muted)
                                Text(weight, fontSize = 10.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (a.candles != null && a.candles.isNotEmpty()) {
                GlassCard(accent = PumpWatchPalette.TabAssistant, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊 نمودار ساعتی + میانگین‌های متحرک (قرمز=EMA20، آبی=EMA50)", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                        DexChart(a.candles)
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (a.candidate.providerId.isNotEmpty() && !a.candidate.venue.startsWith("DEX")) {
                ProChart(coinId = a.candidate.providerId, symbol = a.candidate.symbol)
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.candidate.poolUrl != null) {
                    Button(
                        onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.candidate.poolUrl))) } catch (_: Throwable) { } },
                        colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("🌊 مشاهده استخر DEX", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                Button(
                    onClick = { selectedCandidate = null; analysisResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.TabAssistant, contentColor = PumpWatchPalette.Ink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("🔍 جست‌وجوی جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ============================================================
// توابع کمکی تحلیل (بدون تغییر در منطق اصلی، فقط تطبیق با ساختار جدید)
// ============================================================

private suspend fun performAnalysis(candidate: AssetCandidate): CoinAnalysis {
    val observedAtMs = System.currentTimeMillis()
    var status: AnalysisStatus = AnalysisStatus.Success
    var score = 50
    val ind = mutableMapOf<String, String>()
    val formula = mutableMapOf<String, String>()
    formula["پایه"] = "۵۰ امتیاز"
    
    var closes = emptyList<Double>()
    var candles = emptyList<DexCandle>()
    var buySellRatio1h: Double? = null
    var dataQualityChecks = 0
    
    if (candidate.venue.startsWith("CEX")) {
        try {
            val kl = BinanceClient.api.klines("${candidate.symbol}USDT", "1h", 100)
            closes = kl.map { it[4].asDouble }
            if (closes.size >= 35) dataQualityChecks++ else status = AnalysisStatus.Partial
        } catch (_: Throwable) {
            status = AnalysisStatus.Partial
        }
    } else {
        try {
            val net = candidate.chain ?: "solana"
            val addr = candidate.contract ?: ""
            val oh = GeckoOhlcv.api.poolOhlcvHour(net, addr)
            candles = oh.data?.attributes?.ohlcv_list?.mapNotNull { row -> 
                if (row.size >= 5) DexCandle(row[1], row[2], row[3], row[4]) else null 
            } ?: emptyList()
            closes = candles.map { it.c }
            if (closes.size >= 35) dataQualityChecks++ else status = AnalysisStatus.Partial
            
            try {
                val pool = GeckoTerminal.api.searchPools(candidate.symbol).data?.firstOrNull { it.attributes != null }
                if (pool != null) {
                    val a = pool.attributes!!
                    val b1 = a.transactions?.h1?.buys ?: 0.0
                    val s1 = a.transactions?.h1?.sells ?: 0.0
                    if (b1 + s1 > 0) {
                        buySellRatio1h = b1 / (b1 + s1)
                        dataQualityChecks++
                    } else {
                        status = AnalysisStatus.Partial
                    }
                }
            } catch (_: Throwable) {
                status = AnalysisStatus.Partial
            }
        } catch (_: Throwable) {
            status = AnalysisStatus.Error("عدم دسترسی به داده‌های DEX")
        }
    }
    
    if (status is AnalysisStatus.Error) {
        return CoinAnalysis(candidate, status, null, "تحلیل ناموفق", emptyMap(), null, "داده‌ای دریافت نشد.", 0, candidate.venue, observedAtMs, 0, "نامشخص", emptyMap(), null)
    }
    
    if (closes.size < 10) status = AnalysisStatus.Insufficient
    
    if (closes.size >= 10) {
        val rsi = rsiOf(closes)
        val mUp = MacdCalc.macdUp(closes)
        val e20 = emaSeries(closes, 20).lastOrNull() ?: closes.last()
        val e50 = emaSeries(closes, 50).lastOrNull() ?: closes.last()
        val px = closes.last()
        
        when {
            rsi < 30 -> { score += 15; ind["RSI (14)"] = "${rsi.toInt()} (اشباع فروش)"; formula["RSI < ۳۰"] = "+۱۵" }
            rsi > 70 -> { score -= 15; ind["RSI (14)"] = "${rsi.toInt()} (اشباع خرید)"; formula["RSI > ۷۰"] = "−۱۵" }
            else -> ind["RSI (14)"] = "${rsi.toInt()} (محدوده میانی)"
        }
        if (mUp) { score += 15; ind["MACD"] = "سیگنال صعودی"; formula["MACD صعودی"] = "+۱۵" } 
        else { score -= 10; ind["MACD"] = "سیگنال نزولی"; formula["MACD نزولی"] = "−۱۰" }
        when {
            px > e20 && e20 > e50 -> { score += 20; ind["EMA"] = "آرایش صعودی"; formula["EMA صعودی"] = "+۲۰" }
            px < e20 && e20 < e50 -> { score -= 20; ind["EMA"] = "آرایش نزولی"; formula["EMA نزولی"] = "−۲۰" }
            else -> ind["EMA"] = "آرایش خنثی / درهم"
        }
    } else {
        ind["تکنیکال"] = "دادهٔ کندلی ناکافی"
        formula["تکنیکال"] = "۰ (داده ناکافی)"
    }
    
    if (buySellRatio1h != null) {
        val ratioPct = (buySellRatio1h * 100).toInt()
        ind["نسبت خرید (۱س)"] = "$ratioPct٪"
        when {
            buySellRatio1h >= 0.60 -> { score += 10; formula["نسبت خرید ≥ ۶۰٪"] = "+۱۰" }
            buySellRatio1h <= 0.40 -> { score -= 10; formula["نسبت خرید ≤ ۴۰٪"] = "−۱۰" }
            else -> formula["نسبت خرید متعادل"] = "۰"
        }
    } else {
        ind["نسبت خرید (۱س)"] = "داده در دسترس نیست"
    }
    
    val finalScore = score.coerceIn(0, 100)
    val momentumLabel = when {
        finalScore >= 80 -> "مومنتوم صعودی قوی"
        finalScore >= 65 -> "مومنتوم صعودی"
        finalScore >= 45 -> "روند خنثی / نیاز به تأیید"
        finalScore >= 30 -> "مومنتوم نزولی"
        else -> "مومنتوم نزولی قوی"
    }
    
    val dataQualityScore = (dataQualityChecks * 25).coerceAtMost(100)
    val validityHours = when { closes.size >= 24 -> 24; closes.size >= 12 -> 12; else -> 4 }
    
    val invalidationScenario = when {
        finalScore >= 65 -> "اگر RSI به بالای ۷۰ برسد یا MACD سیگنال نزولی دهد، این ارزیابی تغییر می‌کند."
        finalScore <= 35 -> "اگر RSI به زیر ۳۰ برسد یا MACD سیگنال صعودی دهد، این ارزیابی تغییر می‌کند."
        else -> "تغییر قابل توجه در RSI یا MACD می‌تواند این ارزیابی را باطل کند."
    }
    
    val reason = buildString {
        append("تحلیل بر اساس ${candidate.venue}. ")
        if (status == AnalysisStatus.Partial) append("توجه: برخی داده‌ها در دسترس نبودند (وضعیت Partial). ")
        append("امتیاز نهایی بر اساس هم‌گرایی اندیکاتورهای تکنیکال و نسبت حجم محاسبه شده است.")
    }
    
    return CoinAnalysis(candidate, status, finalScore, momentumLabel, ind, buySellRatio1h, reason, dataQualityScore, candidate.venue, observedAtMs, validityHours, invalidationScenario, formula, if (candles.isNotEmpty()) candles else null)
}

@Composable
private fun DexChart(candles: List<DexCandle>) {
    val upColor = PumpWatchPalette.Positive
    val downColor = PumpWatchPalette.Negative
    val ema20Color = PumpWatchPalette.Negative.copy(alpha = 0.9f)
    val ema50Color = PumpWatchPalette.Info
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val vis = candles.takeLast(80)
        if (vis.size < 5) return@Canvas
        val w = size.width; val h = size.height
        val minV = vis.minOf { it.l }; val maxV = vis.maxOf { it.h }
        val range = if (maxV > minV) maxV - minV else 1.0
        val cw = w / vis.size; val bw = cw * 0.55f
        fun y(v: Double) = (h - ((v - minV) / range * h * 0.9 + h * 0.05)).toFloat()

        vis.forEachIndexed { i, c ->
            val x = i * cw + cw / 2; val col = if (c.c >= c.o) upColor else downColor
            drawLine(col, Offset(x, y(c.h)), Offset(x, y(c.l)), strokeWidth = 2f)
            val yo = y(c.o); val yc = y(c.c)
            drawRect(col, topLeft = Offset(x - bw / 2, min(yo, yc)), size = Size(bw, max(3f, abs(yo - yc))))
        }
        val closes = vis.map { it.c }
        val e20 = emaSeries(closes, 20); val e50 = emaSeries(closes, 50)
        fun drawLine2(series: List<Double>, color: Color) {
            if (series.isEmpty()) return
            val off = closes.size - series.size; var prev: Offset? = null
            for (i in off until closes.size) {
                val cur = Offset((i * cw + cw / 2), y(series[i - off]))
                if (prev != null) drawLine(color, prev!!, cur, strokeWidth = 2f)
                prev = cur
            }
        }
        drawLine2(e20, ema20Color); drawLine2(e50, ema50Color)
    }
}

private fun emaSeries(v: List<Double>, p: Int): List<Double> {
    if (v.size < p) return emptyList()
    val k = 2.0 / (p + 1); val out = ArrayList<Double>(v.size); var e = v.take(p).average()
    for (i in v.indices) { e = if (i < p) e else v[i] * k + e * (1 - k); out.add(e) }
    return out
}

private fun rsiOf(v: List<Double>, p: Int = 14): Double {
    if (v.size <= p) return 50.0
    var g = 0.0; var l = 0.0
    for (i in 1..p) { val d = v[i] - v[i - 1]; if (d > 0) g += d else l -= d }
    var ag = g / p; var al = l / p
    for (i in p + 1 until v.size) {
        val d = v[i] - v[i - 1]
        ag = (ag * (p - 1) + max(d, 0.0)) / p; al = (al * (p - 1) + max(-d, 0.0)) / p
    }
    return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
}
