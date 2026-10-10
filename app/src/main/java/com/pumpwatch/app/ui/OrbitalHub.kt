package com.pumpwatch.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette
import kotlinx.coroutines.delay

// 🚀 Commit 159: هاب مداری — منظومه شمسی PumpDump

data class OrbitalItem(
    val id: String,
    val emoji: String,
    val label: String,
    val color: Color,
    val radius: Float // 0.0 تا 1.0 (نسبت به اندازه Canvas)
)

@Composable
fun OrbitalHub(
    onTabClick: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing), // ۲۰ ثانیه برای یک دور کامل
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRing"
    )
    
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing), // ۱ ثانیه، جهت مخالف
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRing"
    )

    // 🌟 گوی مرکزی ژله‌ای طلایی
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val minDim = minOf(size.width, size.height)

            // 🪐 حلقه بیرونی: ۱۶ تب
            val outerRadius = minDim * 0.35f
            val tabs = listOf(
                OrbitalItem("market", "", "بازار", TabPalette.Market, outerRadius),
                OrbitalItem("alerts_spot", "🔔", "هشدار", TabPalette.Alerts, outerRadius),
                OrbitalItem("alerts_fut", "⚡", "فیوچرز", TabPalette.Alerts, outerRadius),
                OrbitalItem("watchlist", "⭐", "واچ‌لیست", TabPalette.Watchlist, outerRadius),
                OrbitalItem("whale", "🐳", "نهنگ", TabPalette.Whale, outerRadius),
                OrbitalItem("meme", "🐸", "میم", TabPalette.Meme, outerRadius),
                OrbitalItem("wallet", "👛", "کیف", TabPalette.Wallet, outerRadius),
                OrbitalItem("assistant", "🤖", "دستیار", TabPalette.Assistant, outerRadius),
                OrbitalItem("backtest", "🧪", "بک‌تست", TabPalette.Backtest, outerRadius),
                OrbitalItem("trades", "📈", "معامله", TabPalette.Trade, outerRadius),
                OrbitalItem("traders", "", "تریدرها", TabPalette.Leaderboard, outerRadius),
                OrbitalItem("futures_dash", "⚡", "داشبورد", TabPalette.Alerts, outerRadius),
                OrbitalItem("futures_scan", "🔍", "اسکنر", TabPalette.Alerts, outerRadius),
                OrbitalItem("paper", "", "Paper", TabPalette.Trade, outerRadius),
                OrbitalItem("journal", "📓", "ژورنال", TabPalette.Trade, outerRadius),
                OrbitalItem("settings", "⚙️", "تنظیمات", Color(0xFF8B949E), outerRadius)
            )

            val angleStep = 360f / tabs.size

            rotate(outerRotation) {
                tabs.forEachIndexed { i, item ->
                    val angle = (i * angleStep) * (Math.PI / 180.0f)
                    val x = centerX + (item.radius * cos(angle.toFloat())).toFloat()
                    val y = centerY + (item.radius * sin(angle.toFloat())).toFloat()
                    
                    translate(left = x - 20.dp.toPx(), top = y - 20.dp.toPx()) {
                        // آیکون تب
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(item.color.copy(alpha = 0.3f), Color.Transparent),
                                center = Offset(20.dp.toPx(), 20.dp.toPx()),
                                radius = 25.dp.toPx()
                            ),
                            radius = 25.dp.toPx()
                        )
                        // اینجا می‌تونیم کلیک‌پذیر کنیم (در نسخه پیشرفته‌تر)
                    }
                }
            }

            // 💫 حلقه درونی: ارزهای محبوب (نمونه)
            val innerRadius = minDim * 0.18f
            val coins = listOf("BTC", "ETH", "SOL", "BNB", "XRP", "ADA")
            val coinColors = listOf(
                Color(0xFFF7931A), Color(0xFF627EEA), Color(0xFF00FFA3),
                Color(0xFFF3BA2F), Color(0xFF23292F), Color(0xFF0033AD)
            )

            rotate(innerRotation) {
                coins.forEachIndexed { i, coin ->
                    val angle = (i * (360f / coins.size)) * (Math.PI / 180.0f)
                    val x = centerX + (innerRadius * cos(angle.toFloat())).toFloat()
                    val y = centerY + (innerRadius * sin(angle.toFloat())).toFloat()
                    
                    translate(left = x - 15.dp.toPx(), top = y - 15.dp.toPx()) {
                        drawCircle(
                            color = coinColors[i % coinColors.size].copy(alpha = 0.6f),
                            radius = 15.dp.toPx()
                        )
                    }
                }
            }

            //  گوی مرکزی ژله‌ای طلایی
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD700).copy(alpha = 0.9f),
                        Color(0xFFFFA500).copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = 50.dp.toPx()
                ),
                radius = 50.dp.toPx(),
                center = Offset(centerX, centerY)
            )

            // لوگوی pumpdump در مرکز
            // (در نسخه نهایی، اینجا Text یا Image قرار می‌گیره)
        }

        // 🎯 لایه کلیک‌پذیر (برای سادگی، فعلاً دکمه‌های متنی)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                " pumpdump",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFE6EDF3)
            )
            Text(
                "برای انتخاب تب، از منوی پایین استفاده کنید",
                fontSize = 11.sp,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(Modifier.height(200.dp))
            
            // دکمه‌های سریع (نسخه موقت تا پیاده‌سازی کامل کلیک روی Canvas)
            Surface(
                color = Color(0xFF1A2230),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(" بازار", modifier = Modifier.clickable { onTabClick("market") }.padding(8.dp), color = TabPalette.Market)
                    Text("🔔 هشدار", modifier = Modifier.clickable { onTabClick("alerts_spot") }.padding(8.dp), color = TabPalette.Alerts)
                    Text(" نهنگ", modifier = Modifier.clickable { onTabClick("whale") }.padding(8.dp), color = TabPalette.Whale)
                    Text("🐸 میم", modifier = Modifier.clickable { onTabClick("meme") }.padding(8.dp), color = TabPalette.Meme)
                }
            }
        }
    }
}

// توابع کمکی
private fun cos(angle: Float): Double = Math.cos(angle.toDouble())
private fun sin(angle: Float): Double = Math.sin(angle.toDouble())
