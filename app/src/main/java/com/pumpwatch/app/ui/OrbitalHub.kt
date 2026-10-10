package com.pumpwatch.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlin.random.Random

data class TabItem(val id: String, val emoji: String, val label: String, val color: Color)
data class CoinItem(val symbol: String, val color: Color)

@Composable
fun OrbitalHub(onTabClick: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRing"
    )
    
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRing"
    )

    val tabs = remember {
        listOf(
            TabItem("market", "", "بازار", Color(0xFF22D3EE)),
            TabItem("alerts_spot", "", "هشدار", Color(0xFFFB4D6D)),
            TabItem("whale", "", "نهنگ", Color(0xFF06B6D4)),
            TabItem("meme", "", "میم", Color(0xFFF59E0B)),
            TabItem("wallet", "", "کیف", Color(0xFFFFC107)),
            TabItem("assistant", "", "دستیار", Color(0xFFA855F7)),
            TabItem("backtest", "", "بک‌تست", Color(0xFF10B981)),
            TabItem("traders", "", "تریدرها", Color(0xFFFF9500))
        )
    }

    val coins = remember {
        listOf(
            CoinItem("BTC", Color(0xFFF7931A)),
            CoinItem("ETH", Color(0xFF627EEA)),
            CoinItem("SOL", Color(0xFF00FFA3)),
            CoinItem("BNB", Color(0xFFF3BA2F)),
            CoinItem("XRP", Color(0xFF23292F)),
            CoinItem("SUI", Color(0xFF4DA2FF)),
            CoinItem("ADA", Color(0xFF0033AD)),
            CoinItem("PEPE", Color(0xFF4C9F3B))
        )
    }

    // تولید ستاره‌ها با Random استاندارد
    val stars = remember {
        List(100) { 
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                0.5f + Random.nextFloat() * 1.5f
            )
        }
    }

    // تبدیل dp به px بیرون از Canvas برای جلوگیری از خطای تایپ
    val density = LocalDensity.current
    val iconSizePx = with(density) { 28.dp.toPx() }
    val coinSizePx = with(density) { 20.dp.toPx() }
    val centerSizePx = with(density) { 60.dp.toPx() }
    val labelOffsetPx = with(density) { 40.dp.toPx() }
    val textSizePx = with(density) { 11.sp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f0f1e)
                    )
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = tapOffset.x - centerX
                        val dy = tapOffset.y - centerY
                        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        val minDim = minOf(size.width, size.height)
                        val outerRadius = minDim * 0.32f
                        
                        if (abs(distance - outerRadius) < iconSizePx * 1.5f) {
                            var angle = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)
                            angle = (angle - outerRotation + 360) % 360
                            val angleStep = 360f / tabs.size
                            val index = ((angle / angleStep).roundToInt() % tabs.size + tabs.size) % tabs.size
                            onTabClick(tabs[index].id)
                        }
                    }
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val minDim = minOf(size.width, size.height)

            // ۱. ستاره‌ها
            stars.forEach { star ->
                val x = star.first * size.width
                val y = star.second * size.height
                val r = star.third
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f + (Random.nextFloat() * 0.5f)),
                    radius = r * 0.5f,
                    center = Offset(x, y)
                )
            }

            // ۲. حلقه بیرونی
            val outerRadius = minDim * 0.32f
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = outerRadius,
                style = Stroke(width = 2f)
            )

            // ۳. حلقه درونی
            val innerRadius = minDim * 0.18f
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = innerRadius,
                style = Stroke(width = 2f)
            )

            // ۴. آیکون‌های تب (حلقه بیرونی)
            val angleStep = 360f / tabs.size
            tabs.forEachIndexed { i, tab ->
                // استفاده از Double برای cos/sin جهت جلوگیری از خطای ComplexDouble
                val angleRad = (i * angleStep + outerRotation).toDouble() * (PI / 180.0)
                val x = centerX + (outerRadius * cos(angleRad)).toFloat()
                val y = centerY + (outerRadius * sin(angleRad)).toFloat()

                // Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tab.color.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(x, y),
                        radius = iconSizePx * 1.5f
                    ),
                    radius = iconSizePx * 1.5f,
                    center = Offset(x, y)
                )

                // دایره آیکون
                drawCircle(
                    color = tab.color.copy(alpha = 0.9f),
                    radius = iconSizePx,
                    center = Offset(x, y)
                )

                // حاشیه سفید
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = iconSizePx,
                    style = Stroke(width = 1.5f),
                    center = Offset(x, y)
                )

                // Emoji
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = iconSizePx * 1.2f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(tab.emoji, x, y + iconSizePx * 0.35f, paint)
                }

                // برچسب متنی
                val labelX = centerX + ((outerRadius + labelOffsetPx) * cos(angleRad)).toFloat()
                val labelY = centerY + ((outerRadius + labelOffsetPx) * sin(angleRad)).toFloat()
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = textSizePx
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText(tab.label, labelX, labelY, paint)
                }
            }

            // ۵. ارزها (حلقه درونی)
            val coinAngleStep = 360f / coins.size
            coins.forEachIndexed { i, coin ->
                val angleRad = (i * coinAngleStep + innerRotation).toDouble() * (PI / 180.0)
                val x = centerX + (innerRadius * cos(angleRad)).toFloat()
                val y = centerY + (innerRadius * sin(angleRad)).toFloat()

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(coin.color.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(x, y),
                        radius = coinSizePx * 1.3f
                    ),
                    radius = coinSizePx * 1.3f,
                    center = Offset(x, y)
                )

                drawCircle(
                    color = coin.color.copy(alpha = 0.85f),
                    radius = coinSizePx,
                    center = Offset(x, y)
                )

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = coinSizePx * 0.9f
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText(coin.symbol, x, y + coinSizePx * 0.3f, paint)
                }
            }

            // ۶. گوی مرکزی طلایی با ₿
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE066),
                        Color(0xFFFFD700),
                        Color(0xFFFFA500),
                        Color(0xFFFF8C00).copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = centerSizePx
                ),
                radius = centerSizePx,
                center = Offset(centerX, centerY)
            )

            // حاشیه درخشان گوی مرکزی
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = centerSizePx,
                style = Stroke(width = 2f),
                center = Offset(centerX, centerY)
            )

            // نماد ₿ در مرکز
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    textSize = centerSizePx * 0.9f
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                drawText("₿", centerX, centerY + centerSizePx * 0.3f, paint)
            }
        }

        // متن راهنما
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                "روی آیکون‌های چرخان ضربه بزن",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
