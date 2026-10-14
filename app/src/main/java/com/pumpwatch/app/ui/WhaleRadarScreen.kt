package com.pumpwatch.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.DataEnvelope
import com.pumpwatch.app.data.DataStatus
import com.pumpwatch.app.data.FollowedWhale
import com.pumpwatch.app.data.FollowedWhalesStore
import com.pumpwatch.app.data.ServedFrom
import com.pumpwatch.app.engine.WhaleFlowEngine
import com.pumpwatch.app.engine.WhaleFlowResult
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WhaleRadarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subTab by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 🚀 P0 Tab 03: صداقت محصول و سلب مسئولیت شفاف
            EditorialPageHeader(
                eyebrow = "ON-CHAIN · MACRO FLOW",
                title = "رصد جریان‌های کلان",
                subtitle = "تحلیل جریان‌های نمونه با حجم بالا (Notional Flow). این خروجی پژوهشی است و ادعای مالکیت کیف پول یا پیش‌بینی قطعی ندارد.",
                accent = PumpWatchPalette.TabWhale
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    label = { Text("📊 تحلیل نماد", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWhale,
                        selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass,
                        labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    label = { Text("❤️ لیست رصد", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWhale,
                        selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass,
                        labelColor = PumpWatchPalette.Muted
                    )
                )
            }
            if (info.isNotEmpty()) {
                GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(info, fontSize = 10.sp, color = PumpWatchPalette.Positive, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { info = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("✖", fontSize = 10.sp, color = PumpWatchPalette.Text)
                        }
                    }
                }
            }
        }

        when (subTab) {
            0 -> WhaleAnalysisTab(context, scope) { msg -> info = msg }
            1 -> FollowedWhalesTab(context, scope) { msg -> info = msg }
        }
    }
}

@Composable
private fun WhaleAnalysisTab(context: Context, scope: kotlinx.coroutines.CoroutineScope, onInfo: (String) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    
    // 🚀 P0 Tab 03: استفاده از DataEnvelope برای مدیریت دقیق وضعیت‌ها
    var envelope by remember { mutableStateOf<DataEnvelope<WhaleFlowResult>?>(null) }

    fun analyze() {
        val cleanSymbol = symbol.trim().uppercase()
        if (cleanSymbol.isEmpty()) { 
            onInfo("❌ لطفاً نماد را وارد کنید")
            return 
        }
        
        scope.launch {
            loading = true
            try {
                val res = withContext(Dispatchers.IO) { 
                    WhaleFlowEngine.analyze(symbol = cleanSymbol + "USDT", limit = 1000) 
                }
                
                val status = if (res != null) DataStatus.SUCCESS else DataStatus.EMPTY_VALID
                
                envelope = DataEnvelope(
                    data = res,
                    source = "DEX Aggregator / On-chain",
                    observedAtMs = System.currentTimeMillis(),
                    servedFrom = ServedFrom.NETWORK,
                    status = status
                )
                
                if (res == null) {
                    onInfo("⚠️ تراکنش بزرگی در بازهٔ زمانی فعلی یافت نشد")
                } else {
                    onInfo("✅ تحلیل جریان نمونه کامل شد")
                }
            } catch (e: Exception) {
                envelope = DataEnvelope(
                    data = null,
                    source = "DEX Aggregator / On-chain",
                    observedAtMs = System.currentTimeMillis(),
                    status = DataStatus.FAILURE
                )
                onInfo("⚠️ خطا در دریافت داده: ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔍 تحلیل جریان‌های نمونه با حجم بالا", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PumpWatchPalette.Text)
                Text("نماد توکن را وارد کنید تا آمار تراکنش‌های کلان در بازهٔ مشخص بررسی شود", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                TextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    placeholder = { Text("نماد... (BTC, ETH, SOL)", fontSize = 11.sp, color = PumpWatchPalette.MutedDim) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PumpWatchPalette.GlassDeep,
                        unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                        focusedTextColor = PumpWatchPalette.Text,
                        unfocusedTextColor = PumpWatchPalette.Text,
                        cursorColor = PumpWatchPalette.TabWhale,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Button(
                    onClick = { analyze() },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.TabWhale, contentColor = PumpWatchPalette.Ink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = PumpWatchPalette.Ink, strokeWidth = 2.dp)
                    Text("🔍 شروع تحلیل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 🚀 P0 Tab 03: نمایش وضعیت‌های Sealed Outcomes
        when (envelope?.status) {
            DataStatus.FAILURE -> {
                GlassCard(accent = PumpWatchPalette.Negative, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ خطا در دریافت داده‌های زنجیره", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                        Text("اتصال به گره‌های شبکه یا ارائه‌دهندهٔ داده برقرار نشد.", fontSize = 10.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                        Button(
                            onClick = { analyze() },
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("تلاش مجدد", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            
            DataStatus.EMPTY_VALID -> {
                GlassCard(accent = PumpWatchPalette.Muted, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😴 تراکنش بزرگی با معیارهای فعلی یافت نشد", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Muted)
                        Text("این به معنای عدم فعالیت نیست، بلکه حجم معاملات در بازهٔ بررسی شده از آستانهٔ تعریف‌شده پایین‌تر بوده است.", fontSize = 10.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                    }
                }
            }
            
            DataStatus.SUCCESS -> {
                val res = envelope!!.data!!
                val ageSec = envelope!!.ageSec()
                
                GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊 آمار جریان نمونه", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text)
                            Spacer(Modifier.weight(1f))
                            // 🚀 P0 Tab 03: نمایش شفاف متادیتا (Source, Window, Age)
                            Surface(color = PumpWatchPalette.Info.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "منبع: ${envelope!!.source} • بازه: 1000 تراکنش • سن: ${ageSec}ث",
                                    fontSize = 8.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جهت‌گیری جریان:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                            Text(
                                when (res.pressure) {
                                    WhaleFlowEngine.PRESSURE_ACCUMULATION -> "جریان ورودی خالص مثبت"
                                    WhaleFlowEngine.PRESSURE_DISTRIBUTION -> "جریان خروجی خالص مثبت"
                                    else -> "متعادل"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (res.pressure) {
                                    WhaleFlowEngine.PRESSURE_ACCUMULATION -> PumpWatchPalette.Positive
                                    WhaleFlowEngine.PRESSURE_DISTRIBUTION -> PumpWatchPalette.Negative
                                    else -> PumpWatchPalette.Warning
                                }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تعداد تراکنش‌های کلان:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                            Text("${res.whaleTrades}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Info)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("بزرگ‌ترین تراکنش نمونه:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                            Text("$${String.format(Locale.US, "%,.0f", res.largestTrade)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("نسبت حجم خرید به کل:", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                            Text("${(res.buyRatio * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Positive)
                        }

                        Spacer(Modifier.height(8.dp))

                        val cleanSymbol = symbol.trim().uppercase()
                        val isFollowing = FollowedWhalesStore.isFollowing(context, cleanSymbol)
                        Button(
                            onClick = {
                                if (isFollowing) {
                                    FollowedWhalesStore.removeWhale(context, cleanSymbol)
                                    onInfo("❌ از لیست رصد حذف شد")
                                } else {
                                    FollowedWhalesStore.addWhale(context, FollowedWhale(address = "N/A", symbol = cleanSymbol, alertThreshold = res.largestTrade.coerceAtLeast(10_000.0)))
                                    onInfo("✅ نماد به لیست رصد اضافه شد")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) PumpWatchPalette.Negative else PumpWatchPalette.Warning,
                                contentColor = PumpWatchPalette.Ink
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isFollowing) "❌ حذف از لیست رصد" else "❤️ افزودن به لیست رصد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> { /* حالت پیش‌فرض: چیزی نمایش نده تا زمانی که داده بیاید */ }
        }
    }
}

@Composable
private fun FollowedWhalesTab(context: Context, scope: kotlinx.coroutines.CoroutineScope, onInfo: (String) -> Unit) {
    var followedWhales by remember { mutableStateOf<List<FollowedWhale>>(emptyList()) }
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    LaunchedEffect(Unit) { followedWhales = FollowedWhalesStore.load(context) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
            Column {
                // 🚀 P0 Tab 03: خنثی‌سازی ادبیات
                Text("❤️ نمادهای تحت رصد (آستانه حجم بالا)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PumpWatchPalette.Text)
                Text("در صورت ثبت تراکنش با حجم بالاتر از آستانهٔ تعریف‌شده، اعلان ارسال می‌شود", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                Spacer(Modifier.height(8.dp))
                Text("تعداد نمادهای فعال: ${followedWhales.size}", fontSize = 11.sp, color = PumpWatchPalette.Info)
            }
        }

        if (followedWhales.isEmpty()) {
            GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 32.sp)
                    Text("هنوز نمادی را به لیست رصد اضافه نکرده‌اید", fontSize = 11.sp, color = PumpWatchPalette.Muted)
                    Text("از تب «تحلیل نماد»، یک ارز را بررسی و به لیست اضافه کنید", fontSize = 10.sp, color = PumpWatchPalette.MutedDim)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(followedWhales) { whale ->
                    GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📊", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(whale.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text)
                                    Text("افزوده شده در: ${sdf.format(Date(whale.followedAt))}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("آستانهٔ اعلان:", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                Text("$${String.format(Locale.US, "%,.0f", whale.alertThreshold)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                            }
                            Button(
                                onClick = {
                                    FollowedWhalesStore.removeWhale(context, whale.symbol)
                                    followedWhales = FollowedWhalesStore.load(context)
                                    onInfo("❌ از لیست رصد حذف شد")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PumpWatchPalette.Negative.copy(alpha = 0.3f),
                                    contentColor = PumpWatchPalette.Text
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("❌ حذف از لیست رصد", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
