package com.pumpwatch.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.ui.design.TabPalette
import kotlin.math.*

// 🚀 Commit 160: هاب مداری تعاملی — تشخیص لمس مستقیم روی آیکون‌های چرخان

data class OrbitalItem(
    val id: String,
    val emoji: String,
    val label: String,
    val color: Color,
    val radius: Float // 0.0 تا 1.0 (نسبت به اندازه Canvas)
)

@Composable
fun OrbitalHub(onTabClick: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    
    // چرخش حلقه بیرونی: ۲۰ ثانیه برای یک دور کامل (ساعت‌گرد)
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRing"
    )
    
    // چرخش حلقه درونی: ۱۵ ثانیه (پادساعت‌گرد)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRing"
    )

    // 🌟 گوی مرکزی ژله‌ای طلایی
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // محاسبه موقعیت لمس نسبت به مرکز
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val dx = tapOffset.x - centerX
                    val dy = tapOffset.y - centerY
                    val distance = sqrt(dx * dx + dy * dy)
                    
                    // بررسی آیا لمس روی حلقه بیرونی است
                    val minDim = minOf(size.width, size.height)
                    val outerRadius = minDim * 0.35f
                    val iconRadius = 25.dp.toPx()
                    
                    if (abs(distance - outerRadius) < iconRadius * 2) {
                        // محاسبه زاویه لمس (با در نظر گرفتن چرخش فعلی)
                        var angle = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)
                        angle = (angle - outerRotation + 360) % 360
                        
                        // پیدا کردن نزدیک‌ترین آیکون
                        val tabs = getOuterTabs(outerRadius)
                        val angleStep = 360f / tabs.size
                        val index = ((angle / angleStep).roundToInt() % tabs.size + tabs.size) % tabs.size
                        onTabClick(tabs[index].id)
                    }
                }
            }
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val minDim = minOf(size.width, size.height)

            // 🪐 حلقه بیرونی: ۱۶ تب
            val outerRadius = minDim * 0.35f
            val tabs = getOuterTabs(outerRadius)
            val angleStep = 360f / tabs.size

            rotate(outerRotation) {
                tabs.forEachIndexed { i, item ->
                    val angle = (i * angleStep) * (PI / 180.0f)
                    val x = centerX + (item.radius * cos(angle).toFloat())
                    val y = centerY + (item.radius * sin(angle).toFloat())
                    
                    // دایره پس‌زمینه
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(item.color.copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(x, y),
                            radius = 30.dp.toPx()
                        ),
                        radius = 30.dp.toPx(),
                        center = Offset(x, y)
                    )
                    
                    // آیکون
                    drawCircle(
                        color = item.color.copy(alpha = 0.8f),
                        radius = 20.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // 💫 حلقه درونی: ارزهای محبوب
            val innerRadius = minDim * 0.18f
            val coins = listOf("BTC", "ETH", "SOL", "BNB", "XRP", "ADA")
            val coinColors = listOf(
                Color(0xFFF7931A), Color(0xFF627EEA), Color(0xFF00FFA3),
                Color(0xFFF3BA2F), Color(0xFF23292F), Color(0xFF0033AD)
            )

            rotate(innerRotation) {
                coins.forEachIndexed { i, coin ->
                    val angle = (i * (360f / coins.size)) * (PI / 180.0f)
                    val x = centerX + (innerRadius * cos(angle).toFloat())
                    val y = centerY + (innerRadius * sin(angle).toFloat())
                    
                    drawCircle(
                        color = coinColors[i % coinColors.size].copy(alpha = 0.6f),
                        radius = 15.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // 🌟 گوی مرکزی ژله‌ای طلایی
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
        }

        //  لایه متنی روی Canvas
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "🚀 pumpdump",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFE6EDF3)
            )
            Text(
                "روی آیکون‌های چرخان ضربه بزن",
                fontSize = 11.sp,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun getOuterTabs(radius: Float): List<OrbitalItem> = listOf(
    OrbitalItem("market", "📊", "بازار", TabPalette.Market, radius),
    OrbitalItem("alerts_spot", "🔔", "هشدار", TabPalette.Alerts, radius),
    OrbitalItem("alerts_fut", "⚡", "فیوچرز", TabPalette.Alerts, radius),
    OrbitalItem("watchlist", "⭐", "واچ‌لیست", TabPalette.Watchlist, radius),
    OrbitalItem("whale", "🐳", "نهنگ", TabPalette.Whale, radius),
    OrbitalItem("meme", "🐸", "میم", TabPalette.Meme, radius),
    OrbitalItem("wallet", "👛", "کیف", TabPalette.Wallet, radius),
    OrbitalItem("assistant", "🤖", "دستیار", TabPalette.Assistant, radius),
    OrbitalItem("backtest", "🧪", "بک‌تست", TabPalette.Backtest, radius),
    OrbitalItem("trades", "", "معامله", TabPalette.Trade, radius),
    OrbitalItem("traders", "🏆", "تریدرها", TabPalette.Leaderboard, radius),
    OrbitalItem("futures_dash", "⚡", "داشبورد", TabPalette.Alerts, radius),
    OrbitalItem("futures_scan", "🔍", "اسکنر", TabPalette.Alerts, radius),
    OrbitalItem("paper", "", "Paper", TabPalette.Trade, radius),
    OrbitalItem("journal", "📓", "ژورنال", TabPalette.Trade, radius),
    OrbitalItem("settings", "️", "تنظیمات", Color(0xFF8B949E), radius)
)

// توابع کمکی ریاضی
private fun cos(angle: Float): Double = cos(angle.toDouble())
private fun sin(angle: Float): Double = sin(angle.toDouble())
