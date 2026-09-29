package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.pumpwatch.app.data.FollowedWhale
import com.pumpwatch.app.data.FollowedWhalesStore
import com.pumpwatch.app.engine.WhaleFlowEngine
import com.pumpwatch.app.engine.WhaleFlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VCard = Color(0xFF1A2230)

@Composable
fun WhaleRadarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)

    var subTab by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // بارگذاری اولیه
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🐳 رادار نهنگ‌ها", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VGreen)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("🏆 لیدربورد", fontSize = 11.sp) })
                FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("❤️ دنبال‌شده‌ها", fontSize = 11.sp) })
            }
            if (info.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(info, fontSize = 10.sp, color = VGreen, modifier = Modifier.weight(1f))
                        Button(onClick = { info = "" }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("✖", fontSize = 10.sp) }
                    }
                }
            }
        }

        when (subTab) {
            0 -> WhaleLeaderboardTab(context, scope, sdf) { msg -> info = msg }
            1 -> FollowedWhalesTab(context, scope, sdf) { msg -> info = msg }
        }
    }
}

@Composable
private fun WhaleLeaderboardTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sdf: SimpleDateFormat,
    onInfo: (String) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WhaleFlowResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔍 تحلیل نهنگ‌ها", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VBlue)
                Text("نماد توکن را وارد کنید تا فعالیت نهنگ‌ها را ببینید", fontSize = 10.sp, color = VGray)
                TextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    placeholder = { Text("نماد... (BTC, ETH, SOL)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (symbol.trim().isEmpty()) {
                            onInfo("❌ نماد را وارد کنید")
                            return@Button
                        }
                        scope.launch {
                            loading = true
                            error = null
                            result = null
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    WhaleFlowEngine.analyze(symbol = symbol.trim() + "USDT", limit = 1000)
                                }
                                result = res
                                if (res == null) {
                                    onInfo("⚠️ داده‌ای یافت نشد")
                                } else {
                                    onInfo("✅ تحلیل کامل شد")
                                }
                            } catch (e: Exception) {
                                error = "خطا: ${e.message}"
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = VBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    }
                    Text("🔍 تحلیل نهنگ‌ها", fontSize = 12.sp)
                }
                if (error != null) {
                    Text(error ?: "", fontSize = 10.sp, color = VRed)
                }
            }
        }

        if (result != null) {
            val res = result!!
            Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊 خلاصه تحلیل", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VGreen)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("فشار:", fontSize = 10.sp, color = VGray)
                        Text(
                            when (res.pressure) {
                                WhaleFlowEngine.PRESSURE_ACCUMULATION -> "🐳 تجمع (خرید)"
                                WhaleFlowEngine.PRESSURE_DISTRIBUTION -> "📉 توزیع (فروش)"
                                else -> "️ متعادل"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (res.pressure) {
                                WhaleFlowEngine.PRESSURE_ACCUMULATION -> VGreen
                                WhaleFlowEngine.PRESSURE_DISTRIBUTION -> VRed
                                else -> VGold
                            }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("نسبت خرید:", fontSize = 10.sp, color = VGray)
                        Text("${(res.buyRatio * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("معاملات نهنگی:", fontSize = 10.sp, color = VGray)
                        Text("${res.whaleTrades.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VBlue)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("منبع:", fontSize = 10.sp, color = VGray)
                        Text(res.source, fontSize = 10.sp, color = VGray)
                    }
                }
            }

            if (res.whaleTrades.isNotEmpty()) {
                Text("🐋 لیست نهنگ‌ها:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VGreen)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    items(res.whaleTrades.sortedByDescending { it.usd }) { trade ->
                        val isFollowing = FollowedWhalesStore.isFollowing(context, trade.address)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VCard),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🐋", fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${trade.address.take(6)}...${trade.address.takeLast(4)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            "معامله: ${sdf.format(Date(trade.timestamp))}",
                                            fontSize = 9.sp,
                                            color = VGray
                                        )
                                    }
                                    Text(
                                        "$${String.format(Locale.US, "%,.0f", trade.usd)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (trade.usd > 0) VGreen else VRed
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            try {
                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                    .setPrimaryClip(ClipData.newPlainText("addr", trade.address))
                                                onInfo(" آدرس کپی شد")
                                            } catch (_: Exception) { }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VCard),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("📋 کپی", fontSize = 9.sp)
                                    }
                                    Button(
                                        onClick = {
                                            if (isFollowing) {
                                                FollowedWhalesStore.removeWhale(context, trade.address)
                                                onInfo(" دنبال کردن لغو شد")
                                            } else {
                                                FollowedWhalesStore.addWhale(
                                                    context,
                                                    FollowedWhale(
                                                        address = trade.address,
                                                        symbol = symbol.trim()
                                                    )
                                                )
                                                onInfo("✅ نهنگ دنبال شد")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowing) VRed else VGold
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(if (isFollowing) "❌ لغو دنبال" else "❤️ دنبال کن", fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedWhalesTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sdf: SimpleDateFormat,
    onInfo: (String) -> Unit
) {
    var followedWhales by remember { mutableStateOf<List<FollowedWhale>>(emptyList()) }

    LaunchedEffect(Unit) {
        followedWhales = FollowedWhalesStore.load(context)
    }

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("❤️ نهنگ‌های دنبال‌شده", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VGreen)
                Text("وقتی این نهنگ‌ها معامله بزرگ انجام دهند، نوتیفیکیشن دریافت می‌کنید", fontSize = 10.sp, color = VGray)
                Spacer(Modifier.height(8.dp))
                Text("تعداد: ${followedWhales.size}", fontSize = 11.sp, color = VBlue)
            }
        }

        if (followedWhales.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("", fontSize = 32.sp)
                    Text("هنوز نهنگی دنبال نکرده‌اید", fontSize = 11.sp, color = VGray)
                    Text("از تب لیدربورد، نهنگ‌ها را دنبال کنید", fontSize = 10.sp, color = VGray)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(followedWhales) { whale ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VCard),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🐋", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${whale.address.take(6)}...${whale.address.takeLast(4)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        "نماد: ${whale.symbol} • دنبال شده: ${sdf.format(Date(whale.followedAt))}",
                                        fontSize = 9.sp,
                                        color = VGray
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("آستانه آلرت:", fontSize = 9.sp, color = VGray)
                                Text("$${String.format(Locale.US, "%,.0f", whale.alertThreshold)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VGold)
                            }
                            Button(
                                onClick = {
                                    FollowedWhalesStore.removeWhale(context, whale.address)
                                    followedWhales = FollowedWhalesStore.load(context)
                                    onInfo(" دنبال کردن لغو شد")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VRed.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("❌ لغو دنبال کردن", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
